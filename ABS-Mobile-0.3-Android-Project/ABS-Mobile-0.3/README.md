# ABS Mobile 0.3 (Experimental)

Native Android prototype for camera preview, per-frame CameraX analysis, a draggable 2-D strike-zone overlay, and experimental automatic BALL/STRIKE calls.

## Confirmed configuration
- Camera: rear camera, same placement as the previous setup
- Decision mode: BOTH (BALL + STRIKE)
- FPS: automatic camera cadence
- Processing: Balanced (640x360 analysis, latest-frame backpressure)
- Android: minSdk 26, target/compile SDK 35
- Java: 17

## Important accuracy limitation
The included `BaseballPitchAnalyzer` is an experimental bright-moving-component detector using luminance and frame differences. It is not a trained baseball object-detection model, and it does not establish the true 3-D ball position at the front edge of home plate. Its automatic calls are 2-D image-plane estimates only. Treat them as experimental and do not use them as official umpiring decisions. A labelled pitch dataset, camera geometry calibration, and a validated baseball detector are required for dependable ABS accuracy.

## Build locally
Open this folder in Android Studio (Hedgehog or newer), install Android SDK Platform 35 and Build Tools, then build `:app:assembleDebug`.

Or with Gradle 8.9 and JDK 17:

```bash
gradle --no-daemon clean assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## GitHub Actions
The workflow `.github/workflows/build-apk.yml` runs on pushes to `main` and manual `workflow_dispatch`. It uses the Android SDK preinstalled on GitHub's Ubuntu runner; it intentionally does not use `android-actions/setup-android`, which caused the earlier SDK package failure. Download the artifact named `ABS-Mobile-0.3-debug-APK` from the successful Actions run.

## Camera calibration
Install and mount the phone in the same location used for the prior setup. Open the app, tap `ZONE CAL`, and drag the green rectangle over the desired image-plane strike-zone reference. Turn calibration off, then tap `START AUTO`. The detector reports candidate points and will issue an experimental call only when a tracked candidate crosses the top/bottom zone boundary.

## Controls
- START AUTO / STOP AUTO: enable or pause frame analysis decisions
- ZONE CAL: enable dragging the overlay
- RESET: clear ball/strike/out counts
- BALL+ / STRIKE+: manual operator correction/testing
