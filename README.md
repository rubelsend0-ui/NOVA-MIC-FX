# NOVA MIC FX

Real-time microphone effects for Android (Kotlin, Jetpack Compose, Material 3, MVVM, Coroutines/Flow).
Package: `com.nova.micfx` - minSdk 26, targetSdk/compileSdk 34.

## What it does (Mode A: live microphone FX)
NOVA captures the microphone with `AudioRecord`, processes it on a dedicated audio thread and plays it through
`AudioTrack` to **headphones**. Every control changes real DSP:

| Feature | Implementation |
|---|---|
| Mic gain (-12..+24 dB) | Software gain on captured samples with per-sample smoothing. Does **not** change the phone's hardware mic sensitivity. |
| Output gain / limiter | Smoothed output volume (0..150%) and a peak limiter with adjustable ceiling (-12..-0.3 dB). Clipping is counted and shown. |
| Pitch (-12..+12 st) | Real duration-preserving pitch shifter (two crossfaded delay-line heads). Not a sample-rate trick. Adds ~20 ms. |
| Voice presets | Natural, Clean, Deep, Low Pitch, High Pitch, Robot, Alien, Radio, Echo, Reverb, Gaming, Noise Reduction, Bright, Warm, Telephone, Custom (saved locally). Stable ids, search, selected indicator. |
| Noise reduction | Adaptive noise-floor suppressor (minimum statistics, voice-activity detection, up to -24 dB) + noise gate. **Not AI noise cancellation.** |
| Equalizer | Bass (120 Hz shelf), voice presence (2.5 kHz), treble (6 kHz shelf) biquads with gliding gains |
| Echo / Reverb | Delay line with damped feedback (capped 0.8) / comb + all-pass reverb |
| Dry/wet | Blend between unprocessed and processed voice |
| System effects | Optional Android NoiseSuppressor / AEC / AGC; availability and the REAL enabled state are shown |
| Recovery | Up to 2 automatic stream restarts after a dead audio stream (never after a blocked/unsafe route) |
| Latency | Measured output-buffer queue + capture block (hardware delay not measurable) next to a buffer-size estimate |

## IMPORTANT: Free Fire, WePlay and other voice-chat apps
**NOVA MIC FX cannot put its voice into another app's microphone.** What was investigated for stock, non-rooted Android:

- Every app gets its own capture stream from the audio server; `AudioRecord.setPreferredDevice()` and
  `AudioManager` communication-device APIs only affect the calling app's own streams. They do not inject audio.
- `AudioTrack` output is playback, never another app's microphone input.
- `AudioPlaybackCapture` records *other apps' playback* (with permission); it cannot replace a microphone.
- Accessibility services, overlays and notification access cannot route audio.
- Root / privileged audio policy changes are outside an ordinary APK and are not used.
- **Device-dependent, unverified:** a USB audio interface or a hardware voice changer connected over USB-C may be used as the
  microphone by some apps on some phones. NOVA only detects a USB input and labels it "unverified".

So the app shows "Direct injection: unavailable" and never "Compatible with Free Fire/WePlay". Practical options:
use the game's own voice effects/noise suppression, a hardware voice changer / USB audio interface, or (on PC) a virtual audio cable.
NOVA stays useful as a live headphone voice-effects monitor and to find settings you like.

## Safety / feedback
- Monitoring is blocked through the phone speaker (feedback howl) unless you allow it in Settings.
- The real output route is verified after start-up (output is silent during verification) and re-checked every 250 ms;
  if it switches to the speaker the engine stops.
- Bluetooth works but adds latency; wired/USB headphones are recommended.

## Permissions
| Permission | Why |
|---|---|
| `RECORD_AUDIO` | Capture the microphone |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE` | Android requires a foreground service of type microphone for background capture |
| `POST_NOTIFICATIONS` (Android 13+) | Shows the "microphone is active" notification with a Stop button |

No internet permission. Nothing is recorded to storage or uploaded. Settings/presets are stored in local SharedPreferences.
From Android 10, background apps may receive silence from the microphone; the app warns if it sees digital silence.

## Architecture
```
app/src/main/java/com/nova/micfx/
  NovaApp.kt                      application singletons (settings, engine)
  audio/   (pure Kotlin DSP, no Android imports)
    AudioModels, Presets, DspMath, EqualizerProcessor (biquads), LimiterProcessor,
    NoiseReductionProcessor, EchoProcessor, ReverbProcessor, PitchShifter,
    AudioProcessingPipeline, EngineStateMachine, BufferSizing
  audio/   (Android layer)
    AudioEngine (capture + output thread), AudioDeviceManager, AudioEffectController, CompatibilityChecker
  service/MicFxService.kt         foreground service + notification
  settings/AudioSettingsRepository.kt
  ui/  MainActivity, MicFxViewModel, theme/, components/, screens/ (Home, Effects, Presets, Compatibility, Settings)
```
DSP never allocates inside the audio loop; parameters are immutable `EffectParams` swapped through a StateFlow.

## Tests
`./gradlew testDebugUnitTest` - JVM unit tests with generated PCM signals (no microphone needed): gain/level maths,
clipping and limiter, filter stability and response, noise reduction/gate, echo/reverb, pitch shifting (frequency measured),
pipeline bypass, presets and codec, buffer sizing validation, engine state machine.

## Build with Android Studio
Open the folder, let Gradle sync (JDK 17), run the `app` configuration. Android Studio creates/uses the Gradle wrapper.

## Build locally on a PC
```
./gradlew testDebugUnitTest assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`

### About the Gradle wrapper
`gradle/wrapper/gradle-wrapper.properties`, `gradlew` and `gradlew.bat` are included, but the binary
`gradle-wrapper.jar` could not be produced in the offline environment this ZIP was generated in. The GitHub Actions
workflow detects this and generates the official Gradle 8.7 wrapper automatically. For local builds run once:
`gradle wrapper --gradle-version 8.7` (or just open the project in Android Studio).

## Updating an existing repository with this ZIP
```
cd YOUR_REPO_FOLDER
git pull origin main
unzip -o ../NOVA-MIC-FX.zip -d ..      # overwrites files with the same names
git status
git add -A && git commit -m "Upgrade NOVA MIC FX" && git push origin main
```
Check `git status` first: if you edited files yourself, review the diff before committing.

## Push to GitHub from Termux
```
pkg update && pkg install unzip git gh
gh auth login
unzip NOVA-MIC-FX.zip
cd NOVA-MIC-FX
git init -b main
git add .
git commit -m "Initial NOVA MIC FX project"
git remote add origin YOUR_REPOSITORY_URL
git push -u origin main
```
Pushing to `main` starts the workflow automatically. Manual run and monitoring:
```
gh workflow run android-build.yml --ref main
gh run list --workflow android-build.yml --limit 3
gh run watch
gh run download --name NOVA-MIC-FX-debug-apk
```
Or in the browser: repository > Actions > Android Build > run > Artifacts > `NOVA-MIC-FX-debug-apk`.
The artifact is a ZIP containing `app-debug.apk`; extract it and install (allow "install unknown apps").

## Troubleshooting
- **Start does nothing / error "permission"**: grant microphone permission; use "Open app settings" if Android stopped asking.
- **"Monitoring blocked"**: connect headphones, or allow speaker monitoring in Settings (feedback risk).
- **Input is digital silence**: another app holds the microphone, or Android silences background capture. Close other apps, keep NOVA in the foreground.
- **Crackling/underruns**: Settings > Buffer size 3x or 4x.
- **Noise reduction weak**: raise strength, enable the gate, move the mic closer. Performance depends on microphone, noise type and device.
- **System noise suppressor "not available"**: many devices do not provide it for this audio source; the built-in adaptive suppressor still works.
- **Bluetooth delay**: A2DP adds 100+ ms; use wired/USB headphones.
- **Unsupported effect messages**: shown in the status card when the platform effect cannot be created or enabled.

## Honest status
This project was written without access to an Android SDK/Gradle in the generation environment, so it was **not compiled
there**. Build correctness is established by running the GitHub Actions workflow. Fix reports from the Actions log if any
compile error appears.
