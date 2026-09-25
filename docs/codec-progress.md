# AC3/EAC3 implementation

Plan: docs/codec-next.md. Base: 08422b4. Branch: codecs/ac3-eac3.

## Scope

First deliverable: platform AC3/EAC3 in the existing Cefrium/Lampa host on
Android 10+, built by GitHub Actions using VPS resources. Software FFmpeg
decoding is a separate follow-up. Device model/output information is pending.

## Progress

- Source audit: pinned Chromium 152 media_options.gni, mime_util_internal.cc,
  ffmpeg_common.cc and MediaCodecAudioDecoder contain the Android Dolby path.
  Upstream Cefrium codecs build does not set enable_platform_ac3_eac3_audio.
- VPS preflight: 16 CPUs, 31 GiB RAM, 350 GiB available. Native checkout will
  live outside the Actions checkout, with immutable source pins and no reset.
- Baseline playback tests: pending. Build/app integration: pending.
- Native build, regression input tests, media results: pending.
- Actual HDMI/device validation remains dependent on real hardware.

## Decisions

- Use the exact 0.9.2 SDK source revision already used by the input milestone.
  Some upstream build-guide examples and VERSION.stamp still name Chromium 150;
  use CHROMIUM_BUILD_COMPATIBILITY.txt and verify generated version metadata.
- Shallow source checkout on VPS only; do not download Chromium onto the PC.
- Build arm64 and x64 sequentially, bounding compiler concurrency for 32 GiB RAM.
- Platform capability, successful media playback, and decoded PCM evidence are
  distinct results. MIME acceptance alone is never reported as playback success.
