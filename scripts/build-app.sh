#!/usr/bin/env bash
# Reuse a published runtime. This script never compiles Chromium or FFmpeg.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p artifacts
aar=$(python3 scripts/fetch-runtime.py)
sha=$(python3 -c 'import json; print(json.load(open("dependencies/runtime.json"))["sha256"])')
abi=$(python3 -c 'import json; print(json.load(open("dependencies/runtime.json"))["abi"])')
cp dependencies/runtime.json artifacts/runtime.json
bash scripts/build-frontend.sh
bash scripts/generate-media.sh
bash scripts/gradle.sh :app:assembleDebug -Pabi="$abi" -PcefriumAar="$aar" -PcefriumSha256="$sha"
cp app/build/outputs/apk/debug/app-debug.apk artifacts/lampa-software-ac3-arm64.apk
(cd artifacts && sha256sum lampa-software-ac3-arm64.apk > SHA256SUMS)
git rev-parse HEAD > artifacts/app-revision.txt
