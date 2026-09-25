#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root"
export ANDROID_HOME=${ANDROID_HOME:-/opt/lampa-ci/android-sdk}
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_AVD_HOME=${ANDROID_AVD_HOME:-/build/lampa-ci/avd}   # путь self-hosted VPS; на GitHub-раннере задаётся переменной окружения
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
api=${LAMPA_ANDROID_API:-35}
case "$api" in 29) port=5556 ;; 35) port=5554 ;; *) echo 'Supported test APIs: 29, 35'; exit 1 ;; esac
export ANDROID_SERIAL="emulator-$port"
evidence_dir="artifacts/android-$api"
mkdir -p "$evidence_dir" "$ANDROID_AVD_HOME"
# Generated reports from the previous API must never be attributed to this one.
rm -rf -- app/build/outputs/androidTest-results app/build/reports/androidTests
emulator -accel-check > "$evidence_dir/kvm.txt" 2>&1
if adb devices | grep -q "^$ANDROID_SERIAL[[:space:]]"; then
  echo "Port $port already belongs to an emulator; refusing to replace it." >&2
  exit 1
fi
printf 'no\n' | avdmanager create avd --force --name "lampa-probe-$api" --package "system-images;android-$api;google_apis;x86_64"
emulator -avd "lampa-probe-$api" -no-window -no-audio -no-boot-anim -no-snapshot -wipe-data \
  -gpu swiftshader_indirect -cores 4 -memory 4096 -skin 1280x720 -port "$port" \
  -camera-back none -camera-front none > "$evidence_dir/emulator.log" 2>&1 &
emulator_pid=$!
cleanup() {
  adb logcat -d > "$evidence_dir/logcat.txt" 2>&1 || true
  if [[ ! -d "$evidence_dir/evidence" ]]; then
    adb pull /sdcard/Download/lampa-probe-evidence "$evidence_dir/evidence" >/dev/null 2>&1 || true
  fi
  cp -a app/build/outputs/androidTest-results "$evidence_dir/junit" 2>/dev/null || true
  cp -a app/build/reports/androidTests "$evidence_dir/reports" 2>/dev/null || true
  adb emu kill >/dev/null 2>&1 || true
  kill "$emulator_pid" 2>/dev/null || true
  wait "$emulator_pid" 2>/dev/null || true
}
trap cleanup EXIT
timeout 240 adb wait-for-device
booted=false
for ((attempt=0; attempt<180; attempt++)); do
  if [[ $(adb shell getprop sys.boot_completed | tr -d '\r') == 1 ]]; then booted=true; break; fi
  kill -0 "$emulator_pid"
  sleep 2
done
[[ $booted == true ]] || { echo 'Android did not boot'; exit 1; }
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
# A fresh Android image shows a system-owned fullscreen tutorial over the app.
# Confirm it before testing so it cannot consume the first touch or D-pad events.
adb shell settings put secure immersive_mode_confirmations confirmed
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell wm density 160
adb shell wm size 1280x720
adb shell getprop > "$evidence_dir/android-properties.txt"
adb logcat -c
test_args=()
case ${LAMPA_TEST_SUITE:-input} in
  input) test_args+=(-Pandroid.testInstrumentationRunnerArguments.class=dev.lampa.cefrium.LampaInputTest) ;;
  media) test_args+=(-Pandroid.testInstrumentationRunnerArguments.class=dev.lampa.cefrium.CodecPlaybackTest) ;;
  all) ;;
  *) echo 'Unknown test suite'; exit 1 ;;
esac
bash scripts/gradle.sh :app:connectedDebugAndroidTest -Pabi=x86_64 --stacktrace "${test_args[@]}" "$@"
adb pull /sdcard/Download/lampa-probe-evidence "$evidence_dir/evidence"
if [[ ${LAMPA_TEST_SUITE:-input} != input ]]; then
  [[ -s "$evidence_dir/evidence/codec-report.json" ]] || { echo 'Missing codec evidence'; exit 1; }
fi
[[ ${LAMPA_TEST_SUITE:-input} == media ]] && exit 0
for mode in tv touch; do
  for name in report.json 01-lampa.png 02-touch-settings.png 03-dpad-focus.png 04-ok-opened.png 05-horizontal-focus.png 06-touch-after-remote.png; do
    [[ -s "$evidence_dir/evidence/$mode-$name" ]] || { echo "Missing evidence: $mode-$name"; exit 1; }
  done
done
