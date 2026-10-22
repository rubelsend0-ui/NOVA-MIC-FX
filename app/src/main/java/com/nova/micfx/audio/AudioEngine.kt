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

private class EngineException(
    message: String,
    val kind: FailureKind = FailureKind.OTHER,
    val recoverable: Boolean = false
) : Exception(message)

/**
 * Live microphone monitoring engine (MODE A). Captures with AudioRecord, processes on a dedicated
 * audio thread, plays through AudioTrack. It does NOT feed audio into other apps.
 *
 * Safety: monitoring through the phone speaker is blocked unless the user explicitly allows it, and
 * the real output route is verified after start-up and re-checked while running.
 * Only one engine instance exists (owned by [com.nova.micfx.NovaApp]) and only one audio thread runs at a time.
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
    @Volatile private var stopKind = FailureKind.NONE
    @Volatile private var activeInputId = -1
    @Volatile private var activeOutputId = -1
    private var thread: Thread? = null
    private var focusRequest: AudioFocusRequest? = null

    fun init() {
        devices.start { removed ->
            if ((machine.state == EngineState.RUNNING || machine.state == EngineState.RECOVERING) &&
                (removed.id == activeInputId || removed.id == activeOutputId)
            ) {
                stopWithReason("Stopped: ${AudioDeviceManager.describe(removed)} was disconnected.", FailureKind.DEVICE_DISCONNECTED)
            }
        }
    }

    val state: EngineState get() = machine.state

    fun setParams(p: EffectParams) { _params.value = p.validated() }
    fun updateParams(transform: (EffectParams) -> EffectParams) { _params.value = transform(_params.value).validated() }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Starts monitoring. Repeated calls while starting/running are ignored (returns false). */
    fun start(): Boolean {
        synchronized(lock) {
            val s = machine.state
            if (s == EngineState.STARTING || s == EngineState.RUNNING || s == EngineState.RECOVERING || s == EngineState.STOPPING) return false
            if (thread?.isAlive == true) return false           // never two audio threads
            if (!hasPermission()) {
                machine.transition(EngineState.ERROR)
                _snapshot.value = EngineSnapshot(
                    state = EngineState.ERROR, message = "Microphone permission not granted",
                    errorDetail = "Allow microphone access for NOVA MIC FX in Android settings, then press Start.",
                    failure = FailureKind.PERMISSION
                )
                return false
            }
            machine.transition(EngineState.STARTING)
            stopRequested = false
            stopReason = null
            stopKind = FailureKind.NONE
            _snapshot.value = EngineSnapshot(state = EngineState.STARTING, message = "Starting audio engine...")
            thread = Thread({ runLoop() }, "NovaAudioThread").also { it.start() }
            return true
        }
    }

    /** Safe to call at any time and from any thread; never blocks. */
    fun stop() { stopWithReason(null, FailureKind.NONE) }

    private fun stopWithReason(reason: String?, kind: FailureKind) {
        synchronized(lock) {
            val s = machine.state
            if (s == EngineState.IDLE || s == EngineState.ERROR || s == EngineState.STOPPING) return
            if (reason != null) stopReason = reason
            stopKind = kind
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
                    stopWithReason("Stopped: another app or a call took audio focus.", FailureKind.AUDIO_FOCUS)
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
        var error: String? = null
        var detail: String? = null
        var kind = FailureKind.NONE
        var attempts = 0
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            while (true) {
                try {
                    runSession(attempts)
                    break
                } catch (e: EngineException) {
                    val canRetry = e.recoverable && settings.settings.value.autoRecover && attempts < MAX_RECOVERY && !stopRequested
                    if (canRetry) {
                        attempts++
                        val n = attempts
                        synchronized(lock) {
                            if (!stopRequested) machine.transition(EngineState.RECOVERING)
                            publish { it.copy(state = EngineState.RECOVERING, recoveryAttempt = n,
                                message = "Recovering audio (attempt $n of $MAX_RECOVERY)...", errorDetail = e.message) }
                        }
                        Thread.sleep(600)
                        continue
                    }
                    error = e.message
                    kind = e.kind
                    break
                }
            }
        } catch (e: SecurityException) {
            error = "Microphone access was denied by Android."
            kind = FailureKind.PERMISSION
            detail = e.message
        } catch (e: Throwable) {
            error = "Audio engine failure: ${e.javaClass.simpleName}"
            detail = e.message
        } finally {
            activeInputId = -1
            activeOutputId = -1
            val err = error
            synchronized(lock) {
                val st = machine.state
                if (err != null && st != EngineState.STOPPING) {
                    machine.transition(EngineState.ERROR)
                    _snapshot.value = EngineSnapshot(state = EngineState.ERROR, message = err, errorDetail = detail, failure = kind)
                } else {
                    if (st != EngineState.STOPPING) machine.transition(EngineState.STOPPING)
                    machine.transition(EngineState.IDLE)
                    _snapshot.value = EngineSnapshot(state = EngineState.IDLE, message = stopReason ?: "Stopped", failure = stopKind)
                }
            }
        }
    }

    /** One capture/playback session. Always releases every native resource before returning or throwing. */
    private fun runSession(attempt: Int) {
        var record: AudioRecord? = null
        var track: AudioTrack? = null
        val effects = AudioEffectController()
        try {
            val s = settings.settings.value
            val nativeRate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 0
            val rate = BufferSizing.chooseSampleRate(s.preferredSampleRate, nativeRate) { r ->
                AudioRecord.getMinBufferSize(r, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) > 0 &&
                    AudioTrack.getMinBufferSize(r, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) > 0
            } ?: throw EngineException("No supported sample rate (48000/44100/16000 Hz) is available on this device.", FailureKind.UNSUPPORTED_FORMAT)

            val block = BufferSizing.blockFrames(rate)
            val burst = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: block
            val recBytes = BufferSizing.bufferBytes(
                AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT),
                block, s.bufferMultiplier)
            val trkBytes = BufferSizing.bufferBytes(
                AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT),
                block, s.bufferMultiplier)

            if (!requestFocus()) throw EngineException("Audio focus was denied (a call or another app is using audio).", FailureKind.AUDIO_FOCUS)

            val notes = ArrayList<String>()
            val inIds = devices.inputs.value.map { it.id }
            val outIds = devices.outputs.value.map { it.id }
            val inPref = DevicePreference.resolve(s.preferredInputId, inIds)
            val outPref = DevicePreference.resolve(s.preferredOutputId, outIds)
            if (DevicePreference.isMissing(s.preferredInputId, inIds)) notes.add("Preferred microphone is not connected; using the system default.")
            if (DevicePreference.isMissing(s.preferredOutputId, outIds)) notes.add("Preferred output is not connected; using the system default.")

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
                throw EngineException("The microphone could not be opened. Another app may be using it, or access is blocked.", FailureKind.MIC_BUSY)
            }
            if (inPref != null) devices.findInput(inPref)?.let { rec.setPreferredDevice(it) }

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
            if (outPref != null) devices.findOutput(outPref)?.let { trk.setPreferredDevice(it) }
            // Keep the playback buffer small for low latency; it grows automatically after an underrun.
            val initialFrames = minOf(trk.bufferCapacityInFrames, maxOf(block, burst) * s.bufferMultiplier.coerceIn(1, 4))
            trk.setBufferSizeInFrames(maxOf(initialFrames, block))

            val fx = effects.attach(rec.audioSessionId, s.requestSystemNs, s.requestSystemAec, s.requestSystemAgc)
            notes.addAll(fx.notes)

            val pipeline = AudioProcessingPipeline(rate)
            val shortIn = ShortArray(block)
            val shortOut = ShortArray(block)
            val floatBuf = FloatArray(block)
            val silence = ShortArray(block)
            var framesWritten = 0L

            rec.startRecording()
            trk.play()

            // --- Warm-up: output silence while the real routes are verified (prevents feedback bursts) ---
            for (i in 0 until 8) {
                if (stopRequested) break
                val r = rec.read(shortIn, 0, block, AudioRecord.READ_BLOCKING)
                if (r < 0) throw EngineException("Microphone read failed (error $r).", FailureKind.OTHER, r == AudioRecord.ERROR_DEAD_OBJECT)
                val w = trk.write(silence, 0, block, AudioTrack.WRITE_BLOCKING)
                if (w < 0) throw EngineException("Audio output write failed (error $w).", FailureKind.OTHER, w == AudioTrack.ERROR_DEAD_OBJECT)
                framesWritten += w
            }
            val routedOut = trk.routedDevice
            val routedIn = rec.routedDevice
            if (!stopRequested && !isSafeOutput(routedOut, s.allowSpeakerMonitoring)) {
                throw EngineException(
                    "Monitoring blocked: output is " + (if (routedOut != null) AudioDeviceManager.describe(routedOut) else "the phone speaker") +
                        ". Connect headphones to avoid feedback, or allow speaker monitoring in Settings.",
                    FailureKind.UNSUPPORTED_ROUTE
                )
            }
            activeInputId = routedIn?.id ?: -1
            activeOutputId = routedOut?.id ?: -1

            val estimate = BufferSizing.durationMs(block, rate) + BufferSizing.durationMs(trk.bufferSizeInFrames, rate)
            synchronized(lock) {
                if (!stopRequested && machine.transition(EngineState.RUNNING)) {
                    _snapshot.value = EngineSnapshot(
                        state = EngineState.RUNNING,
                        message = if (attempt > 0) "Monitoring live (recovered)" else "Monitoring live",
                        recoveryAttempt = attempt,
                        inputDevice = AudioDeviceManager.describe(routedIn),
                        outputDevice = if (routedOut != null) AudioDeviceManager.describe(routedOut) else "Unverified route",
                        outputVerifiedSafe = true, sampleRate = rate, blockFrames = block,
                        latencyEstimateMs = estimate,
                        systemNsActive = fx.ns, systemAecActive = fx.aec, systemAgcActive = fx.agc,
                        effectNotes = notes
                    )
                }
            }

            // --- Real-time loop ---
            val blockNs = block * 1_000_000_000L / rate
            var lastRead = System.nanoTime()
            var lateReads = 0
            var zeroBlocks = 0
            var lastUnderruns = 0
            var holdPeak = 0f
            var measured = -1f
            var lastPublish = SystemClock.elapsedRealtime()
            var lastCheck = lastPublish
            while (!stopRequested) {
                val n = rec.read(shortIn, 0, block, AudioRecord.READ_BLOCKING)
                if (n < 0) throw EngineException("Microphone read failed (error $n).", FailureKind.OTHER, n == AudioRecord.ERROR_DEAD_OBJECT)
                if (n == 0) continue                                  // partial/empty reads are handled: only n frames are processed
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

                var off = 0                                           // handle partial writes
                var zeroWrites = 0
                while (off < n && !stopRequested) {
                    val w = trk.write(shortOut, off, n - off, AudioTrack.WRITE_BLOCKING)
                    if (w < 0) throw EngineException("Audio output write failed (error $w).", FailureKind.OTHER, w == AudioTrack.ERROR_DEAD_OBJECT)
                    if (w == 0 && ++zeroWrites > 3) break
                    off += w
                    framesWritten += w
                }

                val now = SystemClock.elapsedRealtime()
                if (now - lastPublish >= 50) {
                    val dt = now - lastPublish
                    lastPublish = now
                    holdPeak = maxOf(pipeline.outputPeak, holdPeak - dt * 0.0004f)
                    val under = trk.underrunCount
                    if (under > lastUnderruns) {
                        lastUnderruns = under
                        trk.setBufferSizeInFrames(minOf(trk.bufferCapacityInFrames, trk.bufferSizeInFrames + burst))
                    }
                    // Measured: frames still queued in the output buffer (written minus played) plus one capture block.
                    val head = trk.playbackHeadPosition.toLong() and 0xFFFFFFFFL
                    val queued = (framesWritten - head).coerceAtLeast(0L)
                    val sample = BufferSizing.durationMs(queued.toInt(), rate) + BufferSizing.durationMs(block, rate)
                    measured = if (measured < 0f) sample else measured * 0.9f + sample * 0.1f
                    val meters = pipeline.readMeters(holdPeak)
                    val est = BufferSizing.durationMs(block, rate) + BufferSizing.durationMs(trk.bufferSizeInFrames, rate)
                    val m = measured
                    publish { it.copy(meters = meters, underruns = under, lateReads = lateReads,
                        inputSilent = zeroBlocks > 300, latencyEstimateMs = est, measuredBufferLatencyMs = m) }
                }
                if (now - lastCheck >= 250) {
                    lastCheck = now
                    if (!hasPermission()) throw EngineException("Microphone permission was revoked.", FailureKind.PERMISSION)
                    val out = trk.routedDevice
                    if (out != null && !isSafeOutput(out, s.allowSpeakerMonitoring)) {
                        throw EngineException("Output switched to ${AudioDeviceManager.describe(out)}. Monitoring stopped to avoid feedback.", FailureKind.UNSUPPORTED_ROUTE)
                    }
                }
            }
        } finally {
            try { record?.stop() } catch (_: Throwable) {}
            try { record?.release() } catch (_: Throwable) {}
            try { track?.pause() } catch (_: Throwable) {}
            try { track?.flush() } catch (_: Throwable) {}
            try { track?.release() } catch (_: Throwable) {}
            effects.release()
            abandonFocus()
        }
    }

    companion object {
        const val MAX_RECOVERY = 2
    }
}
