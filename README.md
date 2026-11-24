# Zenith for Android

Zenith's focus app with real blocking. The interface is the same web app as the GitHub Pages version (in `app/src/main/assets/index.html`), wrapped in a native Android shell that adds app blocking, Shorts/Reels blocking, website blocking, notification blocking, daily limits and uninstall protection.

## Install on your phone (no computer needed)
The workflow in `.github/workflows/build.yml` builds the app in the cloud and publishes `Zenith.apk` under this repo's **Releases** page. Open Releases in your phone's browser, download the APK and install it.

Then open the **Blocks** tab and turn on the permissions (Accessibility, Notification access, Usage access, Device admin). If Accessibility is greyed out: App info > Zenith > three dots > Allow restricted settings.

## Honest limits
- The code has not been run on a real phone yet. Expect small fixes.
- Shorts, Reels and browser blocking depend on view ids inside other apps and can break after their updates. The lists are in `Constants.kt`.
- Snapchat Spotlight is not detected. Block the whole app instead.
- Split-screen and floating-window detection is a size heuristic.
- Uninstall protection cannot beat safe mode or adb.
- Some phones kill background services. Turn off battery optimisation for Zenith.
- Cloud sign-in, rooms and leaderboard are off in the Android build.
