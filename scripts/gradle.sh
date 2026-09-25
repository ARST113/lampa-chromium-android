#!/usr/bin/env bash
set -euo pipefail
base=${LAMPA_TOOLS:-/opt/lampa-ci/tools}
mkdir -p "$base"
if [[ ! -x "$base/gradle-8.9/bin/gradle" ]]; then
  curl --fail --location --retry 3 -o "$base/gradle-8.9-bin.zip" https://services.gradle.org/distributions/gradle-8.9-bin.zip
  printf '%s  %s\n' d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab "$base/gradle-8.9-bin.zip" | sha256sum -c -
  unzip -q -o "$base/gradle-8.9-bin.zip" -d "$base"
fi
exec "$base/gradle-8.9/bin/gradle" --no-daemon "$@"
