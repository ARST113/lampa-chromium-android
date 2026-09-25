#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root"
export ANDROID_HOME=${ANDROID_HOME:-/opt/lampa-ci/android-sdk}
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_AVD_HOME=/build/lampa-ci/avd
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
export ANDROID_SERIAL=emulator-5554
api=${LAMPA_ANDROID_API:-35}
case "$api" in 29|35) ;; *) echo 'Supported test APIs: 29, 35'; exit 1 ;; esac
evidence_dir="artifacts/android-$api"
mkdir -p "$evidence_dir" "$ANDROID_AVD_HOME"
emulator -accel-check > "$evidence_dir/kvm.txt" 2>&1
if adb devices | grep -q '^emulator-5554'; then
  echo 'Port 5554 already belongs to an emulator; refusing to replace it.' >&2
  exit 1
fi
printf 'no\n' | avdmanager create avd --force --name "lampa-probe-$api" --package "system-images;android-$api;google_apis;x86_64"
emulator -avd "lampa-probe-$api" -no-window -no-audio -no-boot-anim -no-snapshot -wipe-data \
  -gpu swiftshader_indirect -cores 4 -memory 4096 -skin 1280x720 -port 5554 \
  -camera-back none -camera-front none > "$evidence_dir/emulator.log" 2>&1 &
emulator_pid=$!
cleanup() {
  adb logcat -d > "$evidence_dir/logcat.txt" 2>&1 || true
  adb pull /sdcard/Download/lampa-probe-evidence "$evidence_dir/evidence" >/dev/null 2>&1 || true
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
bash scripts/gradle.sh :app:connectedDebugAndroidTest -Pabi=x86_64 --stacktrace
