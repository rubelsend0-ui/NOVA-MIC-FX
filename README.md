# NOVA MIC FX

Real-time microphone effects for Android (Kotlin, Jetpack Compose, Material 3, MVVM, Coroutines/Flow).
Package: `com.nova.micfx` - minSdk 26, targetSdk/compileSdk 34.

## What it does (Mode A: live microphone FX)
The app captures the microphone with `AudioRecord`, processes it on a dedicated audio thread, and plays the
result through `AudioTrack` to **headphones**. Every control changes real DSP:

| Feature | Implementation |
|---|---|
| Mic gain (-12..+24 dB) | Software gain on captured samples with per-sample smoothing. It does **not** change the phone's hardware microphone sensitivity. |
| Monitor volume | Smoothed output gain (0..150%) |
| Limiter / clipping | Peak limiter (instant attack, 80 ms release, hard ceiling 0.97). Input and output clipping are counted and shown. |
| High-pass 80 Hz | RBJ biquad |
| Noise reduction | Adaptive noise-floor suppressor (minimum-statistics floor, voice-activity detector, up to -24 dB) + separate noise gate. **Not AI noise cancellation**; it cannot remove noise while you speak. |
| Bass / voice presence / treble | Low shelf 120 Hz, peaking 2.5 kHz, high shelf 6 kHz (biquads, stability checked, gains glide) |
| Voice changer | Deep / Bright / Custom use a real duration-preserving pitch shifter (two crossfaded delay-line heads). Warm / Radio / Telephone are filter-only. Robot is ring modulation. |
| Echo effect | Delay line with damped feedback (feedback capped at 0.8, cannot run away). This is a deliberate effect, **not** echo cancellation. |
| Reverb | Comb + all-pass reverb with room size and wet/dry mix |
| System effects | Optional Android `NoiseSuppressor`, `AcousticEchoCanceler`, `AutomaticGainControl`. Availability is queried and the real enabled state is displayed. |
| Presets | Gaming, Clear Voice, Deep Voice, Noise Reduction, Radio, Natural, plus a locally saved Custom preset |
| Compatibility screen | Queries real device features, routes and effects; manual checklist |

## IMPORTANT: Android routing limitation (Mode B is not supported)
A normal Android app **cannot** replace the microphone input of a game, Discord, a voice-chat app or a phone call.
Android gives every app its own capture stream from the system audio server; there is no public, supported API for
one app to inject processed audio into another app's microphone. NOVA MIC FX does not use root, hidden APIs,
accessibility tricks or "compatibility toggles", so the UI always reports external-app routing as **not supported**.
Processed audio stays inside NOVA MIC FX and is only heard on your headphones. Do not expect it to work in games.

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
