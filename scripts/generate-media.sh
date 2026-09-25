#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
destination=app/src/main/assets/codecs/media
mkdir -p "$destination" artifacts
ffmpeg -version > artifacts/ffmpeg-version.txt
for pair in h264-aac h264-ac3 h264-eac3 hevc-eac3; do
  video=${pair%-*}; audio=${pair#*-}
  if [[ $video == h264 ]]; then
    encoder=(-c:v libx264 -preset ultrafast -profile:v baseline -level 3.0)
  else
    encoder=(-c:v libx265 -preset ultrafast -x265-params pools=2:frame-threads=2 -tag:v hvc1)
  fi
  ffmpeg -hide_banner -loglevel error -y \
    -f lavfi -i testsrc2=size=320x180:rate=24 \
    -f lavfi -i sine=frequency=440:sample_rate=48000 \
    -t 12 -map 0:v -map 1:a "${encoder[@]}" -pix_fmt yuv420p \
    -c:a "$audio" -b:a 192k -ac 2 -movflags +faststart "$destination/$pair.mp4"
  ffmpeg -hide_banner -loglevel error -y -i "$destination/$pair.mp4" -c copy "$destination/$pair.mkv"
done
python3 - "$destination" <<'PY'
import hashlib,json,pathlib,subprocess,sys
root=pathlib.Path(sys.argv[1]); result=[]
for f in sorted(root.iterdir()):
 if f.suffix not in ('.mp4','.mkv'): continue
 p=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_streams','-show_format','-of','json',str(f)]))
 result.append({'file':f.name,'sha256':hashlib.file_digest(f.open('rb'),'sha256').hexdigest(),
                'streams':p['streams'],'format':p['format']})
pathlib.Path('artifacts/media-fixtures.json').write_text(json.dumps(result,indent=2))
PY
