package com.nova.micfx.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Process
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.nova.micfx.settings.AudioSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private class EngineException(message: String) : Exception(message)

/**
 * Live microphone monitoring engine (MODE A). Captures with AudioRecord, processes on a dedicated
 * audio thread, plays through AudioTrack. It does NOT feed audio into other apps.
 *
 * Safety: monitoring through the phone speaker is blocked unless the user explicitly allows it, and
 * the real output route is verified after start-up and re-checked while running.
 */
class AudioEngine(context: Context, private val settings: AudioSettingsRepository) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val devices = AudioDeviceManager(appContext)
    private val machine = EngineStateMachine()
    private val lock = Any()

    private val _snapshot = MutableStateFlow(EngineSnapshot())
    val snapshot: StateFlow<EngineSnapshot> = _snapshot

    private val _params = MutableStateFlow(EffectParams())
    val params: StateFlow<EffectParams> = _params

    @Volatile private var stopRequested = false
    @Volatile private var stopReason: String? = null
    @Volatile private var activeInputId = -1
    @Volatile private var activeOutputId = -1
    private var thread: Thread? = null
    private var focusRequest: AudioFocusRequest? = null

    fun init() {
        devices.start { removed ->
            if (machine.state == EngineState.RUNNING && (removed.id == activeInputId || removed.id == activeOutputId)) {
                stopWithReason("Stopped: ${AudioDeviceManager.describe(removed)} was disconnected.")
            }
        }
    }

    val state: EngineState get() = machine.state

    fun setParams(p: EffectParams) { _params.value = p.validated() }
    fun updateParams(transform: (EffectParams) -> EffectParams) { _params.value = transform(_params.value).validated() }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun start(): Boolean {
        synchronized(lock) {
            val s = machine.state
            if (s == EngineState.STARTING || s == EngineState.RUNNING || s == EngineState.STOPPING) return false
            if (!hasPermission()) {
                machine.transition(EngineState.ERROR)
                publish { it.copy(state = EngineState.ERROR, message = "Microphone permission not granted",
                    errorDetail = "Allow microphone access for NOVA MIC FX in Android settings, then press Start.") }
                return false
            }
            machine.transition(EngineState.STARTING)
            stopRequested = false
            stopReason = null
            _snapshot.value = EngineSnapshot(state = EngineState.STARTING, message = "Starting audio engine...")
            thread = Thread({ runLoop() }, "NovaAudioThread").also { it.start() }
            return true
        }
    }

    /** Safe to call at any time and from any thread; never blocks. */
    fun stop() { stopWithReason(null) }

    private fun stopWithReason(reason: String?) {
        synchronized(lock) {
            val s = machine.state
            if (s == EngineState.IDLE || s == EngineState.ERROR || s == EngineState.STOPPING) return
            if (reason != null) stopReason = reason
            stopRequested = true
            machine.transition(EngineState.STOPPING)
            publish { it.copy(state = EngineState.STOPPING, message = "Stopping...") }
        }
    }

    private fun publish(f: (EngineSnapshot) -> EngineSnapshot) { _snapshot.value = f(_snapshot.value) }

    private fun requestFocus(): Boolean {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    stopWithReason("Stopped: another app or a call took audio focus.")
                }
            }.build()
        focusRequest = req
        return audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun isSafeOutput(d: AudioDeviceInfo?, allowSpeaker: Boolean): Boolean {
        if (allowSpeaker) return true
        return if (d != null) AudioDeviceManager.isHeadphoneType(d.type) else devices.hasHeadphoneOutput()
    }

    private fun runLoop() {
        var record: AudioRecord? = null
        var track: AudioTrack? = null
        val effects = AudioEffectController()
        var error: String? = null
        var detail: String? = null
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val s = settings.settings.value

            val nativeRate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 0
            val rate = BufferSizing.chooseSampleRate(s.preferredSampleRate, nativeRate) { r ->
                AudioRecord.getMinBufferSize(r, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) > 0 &&
                    AudioTrack.getMinBufferSize(r, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) > 0
            } ?: throw EngineException("No supported sample rate (48000/44100/16000 Hz) is available on this device.")

            val block = BufferSizing.blockFrames(rate)
            val burst = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: block
            val recBytes = BufferSizing.bufferBytes(
                AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT),
                block, s.bufferMultiplier)
            val trkBytes = BufferSizing.bufferBytes(
                AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT),
                block, s.bufferMultiplier)

            if (!requestFocus()) throw EngineException("Audio focus was denied (a call or another app is using audio).")

            val source = if (s.useCommunicationSource) MediaRecorder.AudioSource.VOICE_COMMUNICATION else MediaRecorder.AudioSource.MIC
            val rec: AudioRecord = AudioRecord.Builder()
                .setAudioSource(source)
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                .setBufferSizeInBytes(recBytes)
                .build()
            record = rec
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                throw EngineException("The microphone could not be opened. Another app may be using it, or access is blocked.")
            }
            if (s.preferredInputId >= 0) devices.findInput(s.preferredInputId)?.let { rec.setPreferredDevice(it) }

            val trk: AudioTrack = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(trkBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
            track = trk
            if (trk.state != AudioTrack.STATE_INITIALIZED) throw EngineException("Audio output could not be opened.")
            if (s.preferredOutputId >= 0) devices.findOutput(s.preferredOutputId)?.let { trk.setPreferredDevice(it) }
            // Keep the playback buffer small for low latency; it grows automatically after an underrun.
            val initialFrames = minOf(trk.bufferCapacityInFrames, maxOf(block, burst) * 2 * s.bufferMultiplier.coerceIn(1, 4) / 2)
            trk.setBufferSizeInFrames(maxOf(initialFrames, block))

            val fx = effects.attach(rec.audioSessionId, s.requestSystemNs, s.requestSystemAec, s.requestSystemAgc)

            val pipeline = AudioProcessingPipeline(rate)
            val shortIn = ShortArray(block)
            val shortOut = ShortArray(block)
            val floatBuf = FloatArray(block)
            val silence = ShortArray(block)

            rec.startRecording()
            trk.play()

            // --- Warm-up: output silence while the real routes are verified (prevents feedback bursts) ---
            for (i in 0 until 8) {
                if (stopRequested) break
                val r = rec.read(shortIn, 0, block, AudioRecord.READ_BLOCKING)
                if (r < 0) throw EngineException("Microphone read failed (error $r).")
                trk.write(silence, 0, block, AudioTrack.WRITE_BLOCKING)
            }
            val routedOut = trk.routedDevice
            val routedIn = rec.routedDevice
            if (!isSafeOutput(routedOut, s.allowSpeakerMonitoring)) {
                throw EngineException(
                    "Monitoring blocked: output is " + (if (routedOut != null) AudioDeviceManager.describe(routedOut) else "the phone speaker") +
                        ". Connect headphones to avoid feedback, or allow speaker monitoring in Settings."
                )
            }
            activeInputId = routedIn?.id ?: -1
            activeOutputId = routedOut?.id ?: -1

            val latencyMs = BufferSizing.durationMs(block, rate) + BufferSizing.durationMs(trk.bufferSizeInFrames, rate)
            synchronized(lock) {
                if (!stopRequested && machine.transition(EngineState.RUNNING)) {
                    publish {
                        EngineSnapshot(
                            state = EngineState.RUNNING, message = "Monitoring live",
                            inputDevice = AudioDeviceManager.describe(routedIn),
                            outputDevice = if (routedOut != null) AudioDeviceManager.describe(routedOut) else "Unverified route",
                            outputVerifiedSafe = true, sampleRate = rate, blockFrames = block,
                            latencyEstimateMs = latencyMs,
                            systemNsActive = fx.ns, systemAecActive = fx.aec, systemAgcActive = fx.agc,
                            effectNotes = fx.notes
                        )
                    }
                }
            }

            // --- Real-time loop ---
            val blockNs = block * 1_000_000_000L / rate
            var lastRead = System.nanoTime()
            var lateReads = 0
            var zeroBlocks = 0
            var lastUnderruns = 0
            var holdPeak = 0f
            var lastPublish = SystemClock.elapsedRealtime()
            var lastRouteCheck = lastPublish
            while (!stopRequested) {
                val n = rec.read(shortIn, 0, block, AudioRecord.READ_BLOCKING)
                if (n < 0) throw EngineException("Microphone read failed (error $n).")
                if (n == 0) continue
                val t = System.nanoTime()
                if (t - lastRead > blockNs * 5 / 2) lateReads++
                lastRead = t

                var allZero = true
                for (i in 0 until n) {
                    val v = shortIn[i]
                    if (v.toInt() != 0) allZero = false
                    floatBuf[i] = v / 32768f
                }
                zeroBlocks = if (allZero) zeroBlocks + 1 else 0

                pipeline.setParams(_params.value)
                pipeline.process(floatBuf, n)
                for (i in 0 until n) shortOut[i] = DspMath.floatToShort(floatBuf[i])
                val w = trk.write(shortOut, 0, n, AudioTrack.WRITE_BLOCKING)
                if (w < 0) throw EngineException("Audio output write failed (error $w).")

                val now = SystemClock.elapsedRealtime()
                if (now - lastPublish >= 50) {
                    val frameDt = now - lastPublish
                    lastPublish = now
                    holdPeak = maxOf(pipeline.outputPeak, holdPeak - frameDt * 0.0004f)
                    val under = trk.underrunCount
                    if (under > lastUnderruns) {
                        lastUnderruns = under
                        val grown = minOf(trk.bufferCapacityInFrames, trk.bufferSizeInFrames + burst)
                        trk.setBufferSizeInFrames(grown)
                    }
                    val meters = pipeline.readMeters(holdPeak)
                    val lat = BufferSizing.durationMs(block, rate) + BufferSizing.durationMs(trk.bufferSizeInFrames, rate)
                    publish { it.copy(meters = meters, underruns = under, lateReads = lateReads,
                        inputSilent = zeroBlocks > 300, latencyEstimateMs = lat) }
                }
                if (now - lastRouteCheck >= 250) {
                    lastRouteCheck = now
                    val out = trk.routedDevice
                    if (out != null && !isSafeOutput(out, s.allowSpeakerMonitoring)) {
                        throw EngineException("Output switched to ${AudioDeviceManager.describe(out)}. Monitoring stopped to avoid feedback.")
                    }
                }
            }
        } catch (e: EngineException) {
            error = e.message
        } catch (e: SecurityException) {
            error = "Microphone access was denied by Android."
            detail = e.message
        } catch (e: Throwable) {
            error = "Audio engine failure: ${e.javaClass.simpleName}"
            detail = e.message
        } finally {
            try { record?.stop() } catch (_: Throwable) {}
            try { record?.release() } catch (_: Throwable) {}
            try { track?.pause() } catch (_: Throwable) {}
            try { track?.flush() } catch (_: Throwable) {}
            try { track?.release() } catch (_: Throwable) {}
            effects.release()
            abandonFocus()
            activeInputId = -1
            activeOutputId = -1
            synchronized(lock) {
                val reason = stopReason
                if (error != null && machine.state != EngineState.STOPPING) {
                    machine.transition(EngineState.ERROR)
                    _snapshot.value = EngineSnapshot(state = EngineState.ERROR, message = error ?: "Error", errorDetail = detail)
                } else {
                    if (machine.state != EngineState.STOPPING) machine.transition(EngineState.STOPPING)
                    machine.transition(EngineState.IDLE)
                    _snapshot.value = EngineSnapshot(
                        state = EngineState.IDLE,
                        message = reason ?: error ?: "Stopped"
                    )
                }
            }
        }
    }
}
