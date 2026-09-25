#!/usr/bin/env bash
set -euo pipefail
base=${LAMPA_TOOLS:-/opt/lampa-ci/tools}
mkdir -p "$base"
if [[ ! -x "$base/gradle-9.7.1/bin/gradle" ]]; then
  curl --fail --location --retry 3 -o "$base/gradle-9.7.1-bin.zip" https://services.gradle.org/distributions/gradle-9.7.1-bin.zip
  printf '%s  %s\n' acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a "$base/gradle-9.7.1-bin.zip" | sha256sum -c -
  unzip -q -o "$base/gradle-9.7.1-bin.zip" -d "$base"
fi
exec "$base/gradle-9.7.1/bin/gradle" --no-daemon "$@"
