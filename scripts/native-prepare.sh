#!/usr/bin/env bash
set -euo pipefail
repo=$(cd "$(dirname "$0")/.." && pwd)
base=/build/lampa-ci/native-152
mkdir -p "$base" "$repo/artifacts/native"
exec 9>"$base/.lock"
flock -n 9 || { echo 'Another native operation owns this checkout'; exit 1; }
readarray -t pins < <(python3 - "$repo/dependencies/native-engine.json" <<'PY'
import json,sys
p=json.load(open(sys.argv[1]))
for k in ('chromium_revision','cefrium_revision','depot_tools_revision'):
 print(p[k])
PY
)
[[ ${#pins[@]} == 3 ]] || exit 1
ensure_checkout() {
  local dir=$1 url=$2 revision=$3
  if [[ ! -d "$dir/.git" ]]; then
    mkdir -p "$dir"
    git -C "$dir" init
    git -C "$dir" remote add origin "$url"
  fi
  [[ $(git -C "$dir" remote get-url origin) == "$url" ]]
  if git -C "$dir" rev-parse --verify HEAD >/dev/null 2>&1; then
    [[ $(git -C "$dir" rev-parse HEAD) == "$revision" ]] || {
      echo "Unexpected revision in $dir; refusing to reset it"; exit 1;
    }
  else
    git -C "$dir" fetch --depth=1 origin "$revision"
    git -C "$dir" -c advice.detachedHead=false checkout --detach FETCH_HEAD
  fi
}
if [[ ! -f "$base/.synced" ]]; then
  # Reserve room for the source tree and the first build before starting downloads.
  available=$(df -PB1 "$base" | awk 'NR==2 {print $4}')
  (( available > 220 * 1024 * 1024 * 1024 )) || { echo 'Need 220 GiB free for initial sync'; exit 1; }
fi
ensure_checkout "$base/depot_tools" https://chromium.googlesource.com/chromium/tools/depot_tools.git "${pins[2]}"
export PATH="$base/depot_tools:$PATH"
export DEPOT_TOOLS_UPDATE=0
export DEPOT_TOOLS_METRICS=0
mkdir -p "$base/chromium"
ensure_checkout "$base/chromium/src" https://chromium.googlesource.com/chromium/src.git "${pins[0]}"
cd "$base/chromium"
cat > .gclient <<'EOF'
solutions = [{
  'name': 'src',
  'url': 'https://chromium.googlesource.com/chromium/src.git',
  'managed': False,
  'custom_deps': {},
  'custom_vars': {},
}]
target_os = ['android']
EOF
if [[ ! -f "$base/.synced" ]]; then
  gclient sync --no-history --nohooks -j8
  gclient runhooks
  cp "$repo/dependencies/native-engine.json" "$base/.synced"
fi
cmp "$repo/dependencies/native-engine.json" "$base/.synced"
ensure_checkout "$base/chromium/src/cef" https://codeberg.org/cefrium/cef-android.git "${pins[1]}"
cd src
python3 - "$repo/dependencies/native-engine.json" <<'PY'
import ast,json,pathlib,sys
p=json.load(open(sys.argv[1]))
c=ast.literal_eval(pathlib.Path('cef/CHROMIUM_BUILD_COMPATIBILITY.txt').read_text())
assert c['chromium_checkout']=='refs/tags/'+p['chromium_tag'], c
PY
gclient revinfo > "$repo/artifacts/native/dependencies.txt"
cp "$repo/dependencies/native-engine.json" "$repo/artifacts/native/"
{ df -h "$base"; free -h; du -sh "$base"; } > "$repo/artifacts/native/resources.txt"
echo 'Pinned Chromium and Cefrium sources ready.'
