# Lampa on Cefrium: Android input probe

First milestone: run the real Lampa frontend in the ready-made **Cefrium 0.9.2**
SDK and verify touch + remote control on Android. GitHub Actions orchestrates the
build and tests. A dedicated Ubuntu VPS supplies compute, disk and KVM.

This repository currently hosts a separate test APK (`dev.lampa.cefrium.probe`),
not a replacement for `ARST113/LAMPA` or its native AndroidJS/services. Native
codec changes are the next milestone after the input tests pass.

## Run

Open **Actions → Lampa on Cefrium — Android input test → Run workflow → main**.
The worker builds the pinned Lampa frontend, an x86_64 APK, runs Android
instrumentation with real touch and key events, and builds an ARM64 debug APK.
Download APKs, screenshots, JUnit reports and logcat from the run's artifact.

Pinned inputs:

| Component | Revision |
|---|---|
| Cefrium SDK and Gradle plugin | 0.9.2, the official Codeberg registry |
| Bundled Chromium | 152.0.7977.82 (Cefrium release) |
| Lampa frontend | 40743ea5dd962b01706f6f909d5bef1bdf710474 |
| Gradle | 9.7.1, SHA256 checked |
| JDK | OpenJDK 25 (SDK classes use class-file version 69) |
| AGP | 9.4.0 (matches upstream test harness) |
| Minimum Android | 10 / API 29 |
| Emulator | Android 15 / API 35, x86_64, KVM |

Frontend dependencies use the committed npm lock and `npm ci`. The Cefrium AAR
is checked against the author's published SHA256 before building. Codeberg only
retains its latest Maven release, so archive the verified SDK before a later
upstream release removes this coordinate. The worker's Gradle cache persists
between jobs. This initial probe is not a bit-for-bit reproducibility claim.

The SDK is prebuilt. Its small Gradle resource plugin is compiled from the
unmodified v0.9.2 source because the published plugin requires Java 25; see
[the recorded compatibility fix](build-logic/cefrium-plugin/UPSTREAM.md).

## What is checked

- The bundled Lampa UI loads in Chromium 152.
- Touch opens its real settings panel.
- Android D-pad Down/Up move and restore its real focused item.
- D-pad OK opens a section; Back returns and closes settings.
- Touch still works after remote input.
- Both TV and mobile Lampa input modes execute this sequence.

Tests observe the page through Cefrium's query bridge. They do not fake Lampa's
controllers, dispatch JavaScript clicks or replace its UI with a sample page.
The host defaults to Russian and disables account sync, plugin auto-loading and
socket sync for this isolated test. Catalogue networking remains enabled.

The ready SDK's AC3/EAC3 capability, HDMI passthrough, physical remote layouts,
the existing native LAMPA bridge and production signing are **not** validated by
this input test. Debug APKs are intended for testing.

## Worker

Run `infra/bootstrap-vps.sh` once as administrator on the dedicated Ubuntu 24.04
server, then register `/opt/lampa-ci/runner` as user `lampa-build`, labels `lampa`,
for this private repository. Install the runner service as that same user.
Registration requires a short-lived GitHub token; never commit it. The runner
has KVM access, without unrestricted sudo. Workflow runs are manual and serial.

See [the agreed sequence](docs/plan.md).

## Upstream and notices

- [Cefrium source, LGPL-3.0-or-later](https://codeberg.org/cefrium/cef-android/src/tag/v0.9.2)
- [Cefrium release and SDK docs](https://cefrium.com/releases/)
- [Lampa source and license](https://github.com/ARST113/lampa-source/tree/40743ea5dd962b01706f6f909d5bef1bdf710474)

The APK includes Lampa's license in its bundled assets. Cefrium is consumed as an
unmodified Maven SDK; this repository does not relicense upstream components.
