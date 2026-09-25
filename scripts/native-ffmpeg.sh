#!/usr/bin/env bash
# Called under native-build.sh's checkout lock, after hooks and CEF patches.
set -euo pipefail
repo=$(cd "$(dirname "$0")/.." && pwd)
src=/build/lampa-ci/native-152/chromium/src
artifact="$repo/artifacts/ffmpeg"
mkdir -p "$artifact"
cd "$src"
export PATH="$src/third_party/llvm-build/Release+Asserts/bin:$src/buildtools/linux64:$src/third_party/ninja:/build/lampa-ci/native-152/depot_tools:$PATH"
for patch in "$repo"/native/*.patch; do
  if git apply --reverse --check "$patch" 2>/dev/null; then
    echo "Already applied: $(basename "$patch")"
  else
    git apply --check "$patch"
    git apply "$patch"
  fi
done
stamp=/build/lampa-ci/native-152/.ffmpeg-software-dolby
identity=$(cat "$repo/dependencies/native-engine.json" "$repo/native/0001-ffmpeg-dolby.patch" "$0" | sha256sum | cut -d' ' -f1)
if [[ ! -f "$stamp" || $(cat "$stamp") != "$identity" ]]; then
  # FFmpeg configure resolves all decoder dependencies; never hand-edit CONFIG_*.
  for cpu in arm64 x64; do
    python3 media/ffmpeg/scripts/build_ffmpeg.py android "$cpu" --branding=Chrome 2>&1 | tee "$artifact/configure-$cpu.log"
  done
  (cd third_party/ffmpeg && bash chromium/scripts/copy_config.sh) > "$artifact/copied-configs.log"
  python3 media/ffmpeg/scripts/generate_gn.py 2>&1 | tee "$artifact/generate-gn.log"
  printf '%s\n' "$identity" > "$stamp"
fi
for cpu in arm64 x64; do
  config="third_party/ffmpeg/chromium/config/Chrome/android/$cpu"
  grep -q '^#define CONFIG_AC3_DECODER 1$' "$config/config_components.h"
  grep -q '^#define CONFIG_EAC3_DECODER 1$' "$config/config_components.h"
  grep -q 'ff_ac3_decoder' "$config/libavcodec/codec_list.c"
  grep -q 'ff_eac3_decoder' "$config/libavcodec/codec_list.c"
  cp -a "$config" "$artifact/$cpu"
done
git -C third_party/ffmpeg diff --binary > "$artifact/generated-ffmpeg.patch"
git -C third_party/ffmpeg rev-parse HEAD > "$artifact/ffmpeg-revision.txt"
cp third_party/ffmpeg/ffmpeg_generated.gni "$artifact/"
cp "$repo/native/0001-ffmpeg-dolby.patch" "$artifact/"
echo 'AC3 and EAC3 decoder configurations generated for ARM64 and x64.'
