#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
src="$root/.cache/lampa-source"
commit=40743ea5dd962b01706f6f909d5bef1bdf710474
if [[ ! -d "$src/.git" ]]; then
  mkdir -p "$src"
  git -C "$src" init
  git -C "$src" remote add origin https://github.com/ARST113/lampa-source.git
fi
git -C "$src" fetch --depth 1 origin "$commit"
git -C "$src" checkout --detach "$commit"
test "$(git -C "$src" rev-parse HEAD)" = "$commit"
cp "$root/scripts/bundle-lampa.cjs" "$src/.lampa-probe-bundle.cjs"
cd "$src"
npm install --ignore-scripts --no-audit --no-fund
node .lampa-probe-bundle.cjs "$root/app/src/main/assets/lampa"
mkdir -p "$root/artifacts"
cp package-lock.json "$root/artifacts/lampa-package-lock.json"
printf '%s\n' "$commit" > "$root/artifacts/lampa-commit.txt"
