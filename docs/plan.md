# Lampa on Cefrium: Android feasibility test

User decision, 2026-09-25: first validate the ready-made Cefrium library with
Lampa, touch and remote control. Build a custom Chromium/Cefrium with codecs only
after this test. GitHub stores the project and orchestrates builds; the VPS is the
build and emulator worker. Initial target is Android 10+.

## Scope and acceptance

Use Cefrium 0.9.2 (Chromium 152.0.7977.82), unmodified native SDK from the official Codeberg registry.
Use ARST113/lampa-source at 40743ea5dd962b01706f6f909d5bef1bdf710474.
Create a separate Android application; do not replace the existing LAMPA app.
Package built Lampa assets in the APK so loading the UI does not depend on hosting.
External catalogue requests remain real and may fail separately from UI tests.

The test must execute on an Android x86_64 emulator with KVM. It must load the
real Lampa UI, open settings by touch, move the actual focus using Android D-pad
events, enter a section with OK and leave it with Back. Repeat touch after keys.
Save screenshots, JUnit results, logcat and APK artifacts in the Actions run.
Build an ARM64 APK for later physical device checks as well.

The prototype is a web frontend host, not a completed replacement for LAMPA's
native AndroidJS, updater, intent and service integrations. Do not claim those
are tested. An emulator proves input behavior, not physical remote key layouts,
HDMI passthrough or a device's hardware Dolby decoder.

## Implementation sequence

- [x] Provision a non-root GitHub runner and Android SDK on VPS 195.208.21.202.
- [x] Add a pinned SDK test host and build Lampa's frontend into bundled assets.
- [x] Add Android instrumentation tests before checking input behavior.
- [x] Run build and emulator tests through GitHub Actions; diagnose real failures.
- [x] Review screenshots and test reports and record supported/untested behavior.
- [x] Only then plan the custom Cefrium codec build as a separate milestone.

Completed input milestone: [run 36123360232](https://github.com/ARST113/lampa-chromium-android/actions/runs/36123360232),
commit c23f3dfaebac0f1dc045a1759a1da46201bdb470, 2026-09-25. Four tests passed:
TV and mobile layouts on Android 10 / API 29 and Android 15 / API 35. See
[validation details](validation.md) and the separate [codec milestone](codec-next.md).

## Repository and worker

Private repository: ARST113/lampa-chromium-android. Manual Actions trigger, one
worker job at a time, pinned action revisions, no external-PR execution on VPS.
Runner user lampa-build has KVM access and no general sudo permission.
SDK and caches live on VPS; runner registration uses a short-lived GitHub token.
SSH passwords and GitHub credentials are not repository contents.

## Sources

- https://cefrium.com/quickstart/
- https://cefrium.com/releases/
- https://codeberg.org/cefrium/cef-android/src/tag/v0.9.2
- https://github.com/ARST113/lampa-source/tree/40743ea5dd962b01706f6f909d5bef1bdf710474
