#!/usr/bin/env bash
set -euo pipefail
arch=${1:?Usage: native-build.sh x64 or arm64}
case "$arch" in x64|arm64) ;; *) echo 'Unsupported architecture'; exit 1 ;; esac
repo=$(cd "$(dirname "$0")/.." && pwd)
base=/build/lampa-ci/native-152
artifact="$repo/artifacts/native-$arch"
mkdir -p "$artifact"
exec 9>"$base/.lock"
flock -n 9 || { echo 'Another native operation owns this checkout'; exit 1; }
cmp "$repo/dependencies/native-engine.json" "$base/.synced"
export PATH="$base/depot_tools:$PATH"
export DEPOT_TOOLS_UPDATE=0 DEPOT_TOOLS_METRICS=0
cd "$base/chromium/src"
python3 - "$repo/dependencies/native-engine.json" "$base" <<'PY'
import json,sys,subprocess
p=json.load(open(sys.argv[1])); base=sys.argv[2]
for directory,key in [('.', 'chromium_revision'),('cef','cefrium_revision'),(base+'/depot_tools','depot_tools_revision')]:
 actual=subprocess.check_output(['git','-C',directory,'rev-parse','HEAD'],text=True).strip()
 assert actual==p[key], (directory,actual,p[key])
PY
available=$(df -PB1 . | awk 'NR==2 {print $4}')
(( available > 70 * 1024 * 1024 * 1024 )) || { echo 'Need at least 70 GiB free before building'; exit 1; }
# The upstream wrapper enables Android-conditional patches and fails on rejection.
python3 cef/cefrium_sdk/apply-patches.py 2>&1 | tee "$artifact/patches.log"
(cd cef && python3 tools/version_manager.py -u --fast-check) 2>&1 | tee "$artifact/version.log"
bash "$repo/scripts/native-ffmpeg.sh"
# Patcher may preserve timestamps. Touch all modified tracked source files.
python3 - <<'PY'
import pathlib,subprocess
for root in (pathlib.Path('.'),pathlib.Path('cef')):
 paths=subprocess.check_output(['git','-C',str(root),'diff','--name-only','-z']).decode().split('\0')
 for name in filter(None,paths):
  path=root/name
  if path.is_file(): path.touch()
PY
out="out/Lampa_android_${arch}_software_ac3"
mkdir -p "$out"
cat "$repo/native/args.android.gn" > "$out/args.gn"
printf 'target_cpu = "%s"\n' "$arch" >> "$out/args.gn"
gn gen "$out" 2>&1 | tee "$artifact/gn.log"
gn args "$out" --list --short > "$artifact/effective-args.txt"
grep -q '^enable_platform_ac3_eac3_audio = true$' "$artifact/effective-args.txt"
grep -q '^enable_passthrough_audio_codecs = false$' "$artifact/effective-args.txt"
cp "$out/args.gn" "$artifact/args.gn"
git diff --binary > "$artifact/chromium.patch"
git -C cef diff --binary > "$artifact/cefrium-generated.patch"
cp cef/VERSION.stamp "$artifact/cef-version.txt"
# Bound compiler/link memory on the 32 GiB worker. Never run both ABIs in parallel.
ninja -j8 -C "$out" cef:libcef cef/cefrium_sdk:cefrium_aar 2>&1 | tee "$artifact/ninja.log"
python3 cef/cefrium_sdk/slim_aar.py --input "$out/cefrium-raw.aar" --output "$artifact/cefrium-sdk-$arch-ac3.aar"
python3 - "$artifact" "$repo" "$arch" <<'PY'
import hashlib,json,pathlib,subprocess,sys,zipfile
dest=pathlib.Path(sys.argv[1]); repo=pathlib.Path(sys.argv[2]); arch=sys.argv[3]
aar=dest/f'cefrium-sdk-{arch}-ac3.aar'
abi={'x64':'x86_64','arm64':'arm64-v8a'}[arch]
with zipfile.ZipFile(aar) as z:
 assert f'jni/{abi}/libcef.so' in z.namelist(), 'Native library missing'
 assert 'classes.jar' in z.namelist(), 'SDK Java classes missing'
p=json.loads((repo/'dependencies/native-engine.json').read_text())
p.update({'abi':abi,'aar':aar.name,'sha256':hashlib.file_digest(aar.open('rb'),'sha256').hexdigest(),
          'app_revision':subprocess.check_output(['git','-C',str(repo),'rev-parse','HEAD'],text=True).strip(),
          'decoding':'Built-in FFmpeg AC3/EAC3 software decoding to PCM',
          'args':(dest/'args.gn').read_text()})
(dest/'manifest.json').write_text(json.dumps(p,indent=2))
(dest/'SHA256SUMS').write_text(p['sha256']+'  '+aar.name+'\n')
PY
echo "Native AAR ready: $artifact"
