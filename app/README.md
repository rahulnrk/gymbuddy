# :app — Gym Mode (silent Android build)

Android application. One Gym Mode screen bound to `GymHudBinder` / `GymHudSession`,
plus a platform audio diagnostic. No dashboard, no chat, no STT/TTS.

- `GymHudBinder` maps snapshot + voice turn → HUD fields (unchanged JVM view-model).
- `GymHudSession` binds Start/Pause/Resume and the number pad to `WorkoutEngine`.
- `AndroidEventLog` writes committed events with `:persist` schema/codec so a
  process kill keeps sets.
- `AudioProbe` is the platform audio layer (AudioManager / AudioRecord / AudioTrack / focus).

## Build the debug APK

Needs an Android SDK (compileSdk 34, minSdk 26). From the repo root:

```
.\gradlew.bat :app:assembleDebug
```

APK:

```
app\build\outputs\apk\debug\app-debug.apk
```

Install:

```
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Unit tests (no device):

```
.\gradlew.bat :app:testDebugUnitTest
```

## Gym Mode

Start → log sets on the number pad (source=manual) → kill from recents → reopen.
Sets are restored from the local event log.

## Audio diagnostic

From Gym Mode, open **Audio diagnostic**. It reports the live output/input device
(built-in, wired headset, Bluetooth A2DP, Bluetooth SCO), what happens to the
output route when the mic opens, the actual sample rate, a headset-mic vs
phone-mic+A2DP toggle, record/playback, and ducking audio focus against music
from another app.
