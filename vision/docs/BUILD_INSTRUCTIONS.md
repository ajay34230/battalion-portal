# Build instructions

CI: push to `vision/phase-0` → workflow "VISION Android build" runs unit tests, the coding acceptance tests (Node + Python), builds debug and release APKs and publishes a pre-release `vision-dev-<n>`.
Local: JDK 21, Gradle 8.9, Android SDK 34; `cd vision && gradle testDebugUnitTest assembleDebug`. The APK is signed with the committed personal sideload key (private repo; do not publish the repo without rotating it).
Install: allow "install unknown apps" for your browser/file manager, open the APK. If Play Protect blocks it, choose "Install anyway" in the Play Protect dialog.
