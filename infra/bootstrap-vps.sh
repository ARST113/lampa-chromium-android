#!/usr/bin/env bash
set -euo pipefail
[[ $(id -u) == 0 ]] || { echo 'Run once as administrator'; exit 1; }
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y ca-certificates curl git unzip zip python3 openjdk-25-jdk-headless nodejs npm \
  libpulse0 libnss3 libx11-6 libxcb1 libxcomposite1 libxcursor1 libxi6 libxtst6 \
  libxrandr2 libxrender1 libvulkan1 libgl1 libasound2t64
id lampa-build >/dev/null 2>&1 || useradd --create-home --shell /bin/bash lampa-build
usermod -aG kvm lampa-build
install -d -o lampa-build -g lampa-build /opt/lampa-ci/runner /opt/lampa-ci/android-sdk /opt/lampa-ci/tools /build/lampa-ci
python3 - <<'PY'
import urllib.request, xml.etree.ElementTree as ET, hashlib, pathlib, zipfile, shutil
root=pathlib.Path('/opt/lampa-ci')
xml=ET.fromstring(urllib.request.urlopen('https://dl.google.com/android/repository/repository2-1.xml',timeout=60).read())
ns={'r':'http://schemas.android.com/repository/android/generic/01'}
pkgs=[p for p in xml if p.tag.endswith('remotePackage') and p.attrib.get('path')=='cmdline-tools;latest']
pkg=pkgs[0]
archive=next(a for a in pkg.find('archives') if a.findtext('host-os')=='linux')
url=archive.findtext('complete/url'); checksum=archive.findtext('complete/checksum')
target=root/'tools'/'commandline-tools.zip'
urllib.request.urlretrieve('https://dl.google.com/android/repository/'+url,target)
if hashlib.sha1(target.read_bytes()).hexdigest()!=checksum: raise SystemExit('SDK checksum mismatch')
dest=root/'android-sdk'/'cmdline-tools'
if not (dest/'latest'/'bin'/'sdkmanager').exists():
 dest.mkdir(exist_ok=True)
 with zipfile.ZipFile(target) as z: z.extractall(dest)
 (dest/'cmdline-tools').rename(dest/'latest')
for p in (dest/'latest'/'bin').iterdir(): p.chmod(0o755)
print('Android commandline tools:',url,checksum)
PY
chown -R lampa-build:lampa-build /opt/lampa-ci/android-sdk /opt/lampa-ci/tools
runuser -u lampa-build -- bash -c 'yes | /opt/lampa-ci/android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/opt/lampa-ci/android-sdk --licenses >/dev/null'
runuser -u lampa-build -- /opt/lampa-ci/android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/opt/lampa-ci/android-sdk \
  'platform-tools' 'platforms;android-37' 'build-tools;37.0.0' 'emulator' \
  'system-images;android-35;google_apis;x86_64' 'system-images;android-29;google_apis;x86_64'
runner=/opt/lampa-ci/tools/actions-runner-linux-x64-2.337.0.tar.gz
curl --fail --location --retry 3 -o "$runner" https://github.com/actions/runner/releases/download/v2.337.0/actions-runner-linux-x64-2.337.0.tar.gz
printf '%s  %s\n' 70920811a4f8ad4328818682bca5c6469c1c942fab52448868071d0063816613 "$runner" | sha256sum -c -
tar -xzf "$runner" -C /opt/lampa-ci/runner
chown -R lampa-build:lampa-build /opt/lampa-ci/runner
printf 'JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64\nANDROID_HOME=/opt/lampa-ci/android-sdk\nANDROID_SDK_ROOT=/opt/lampa-ci/android-sdk\n' > /opt/lampa-ci/runner/.env
chown lampa-build:lampa-build /opt/lampa-ci/runner/.env
echo 'BOOTSTRAP COMPLETE; register runner separately with a short-lived token.'
