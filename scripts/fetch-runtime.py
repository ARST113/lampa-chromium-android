#!/usr/bin/env python3
"""Download the pinned, reusable Cefrium AAR; print its verified absolute path."""
import hashlib
import json
from pathlib import Path
import shutil
import sys
import urllib.request
import zipfile


def checksum(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def main():
    repo = Path(__file__).resolve().parent.parent
    pin = json.loads((repo / "dependencies/runtime.json").read_text())
    destination = repo / ".cache/runtime" / pin["sha256"] / pin["aar"]
    destination.parent.mkdir(parents=True, exist_ok=True)
    if not destination.is_file() or checksum(destination) != pin["sha256"]:
        temporary = destination.with_suffix(".aar.part")
        print(f'Downloading runtime {pin["release"]}', file=sys.stderr)
        try:
            request = urllib.request.Request(pin["url"], headers={"User-Agent": "Lampa-runtime-build"})
            with urllib.request.urlopen(request, timeout=120) as response, temporary.open("wb") as output:
                shutil.copyfileobj(response, output, length=1024 * 1024)
            if checksum(temporary) != pin["sha256"]:
                raise RuntimeError("Downloaded runtime SHA256 does not match the committed pin")
            temporary.replace(destination)
        finally:
            temporary.unlink(missing_ok=True)
    with zipfile.ZipFile(destination) as archive:
        required = {"classes.jar", f'jni/{pin["abi"]}/libcef.so'}
        if not required.issubset(archive.namelist()):
            raise RuntimeError("Runtime is missing its Java classes or expected native library")
    print(destination)


if __name__ == "__main__":
    main()
