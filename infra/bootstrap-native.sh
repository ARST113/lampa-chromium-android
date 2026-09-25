#!/usr/bin/env bash
# One-time administrator setup. Actions itself stays unprivileged.
set -euo pipefail
[[ $(id -u) == 0 ]] || { echo 'Administrator required'; exit 1; }
repo=$(cd "$(dirname "$0")/.." && pwd)
export DEBIAN_FRONTEND=noninteractive NEEDRESTART_MODE=l
mkdir -p /build/lampa-ci/bootstrap
python3 - "$repo/dependencies/native-engine.json" <<'PY'
import base64,json,pathlib,sys,urllib.request
revision=json.load(open(sys.argv[1]))['chromium_revision']
url=f'https://chromium.googlesource.com/chromium/src/+/{revision}/build/install-build-deps.py?format=TEXT'
data=base64.b64decode(urllib.request.urlopen(url,timeout=60).read())
pathlib.Path('/build/lampa-ci/bootstrap/install-build-deps.py').write_bytes(data)
PY
python3 /build/lampa-ci/bootstrap/install-build-deps.py --no-prompt --no-arm --no-chromeos-fonts --no-syms
apt-get install -y ffmpeg nasm
install -d -o lampa-build -g lampa-build /build/lampa-ci/native-152
