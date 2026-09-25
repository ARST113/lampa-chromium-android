# AC3/EAC3 implementation

Plan: docs/codec-next.md. Base: 08422b4. Branch: codecs/ac3-eac3.

## Scope

Updated by the user: build software AC3/EAC3 immediately, without spending time
on preliminary emulator checks. Target Android 10+ ARM64, GitHub Actions using
VPS resources. FFmpeg decoding must not depend on Android Dolby MediaCodec.

## Progress

- Source audit: pinned Chromium 152 media_options.gni, mime_util_internal.cc,
  ffmpeg_common.cc and MediaCodecAudioDecoder contain the Android Dolby path.
  Upstream Cefrium codecs build does not set enable_platform_ac3_eac3_audio.
- VPS preflight: 16 CPUs, 31 GiB RAM, 350 GiB available. Native checkout will
  live outside the Actions checkout, with immutable source pins and no reset.
- Original baseline detected no platform AC3/EAC3 decoders on API 35. Its
  diagnostic button was not activated by DPAD_CENTER; touch activation was
  implemented. The repeat run was cancelled at the user's explicit request.
- Native source fetch is in progress. Software FFmpeg patch and build/app
  integration are prepared; first native build is ARM64 only.
- Native build, regression input tests, media results: pending.
- HDMI passthrough is outside this software-to-PCM variant.

## Decisions

- Use the exact 0.9.2 SDK source revision already used by the input milestone.
  Some upstream build-guide examples and VERSION.stamp still name Chromium 150;
  use CHROMIUM_BUILD_COMPATIBILITY.txt and verify generated version metadata.
- Shallow source checkout on VPS only; do not download Chromium onto the PC.
- Ruling (user instruction): skip preliminary emulator runs, build ARM64 first.
  Generate both FFmpeg architecture configs together, but defer a full x64
  engine build. This saves a second Chromium compilation before the first APK.
- Ruling: disable passthrough in this variant so ordinary PCM output is used.
  Consequence: this APK does not deliver an encoded Dolby stream to a receiver.
- Native build found stale upstream C API hashes (13300 and 15200) while
  regenerating Chromium 152 wrappers. Regenerate the custom fork's API metadata
  alongside its native library. The AAR is not a drop-in desktop CEF binary;
  all generated changes are retained in the build artifacts.
- Platform capability, successful media playback, and decoded PCM evidence are
  distinct results. MIME acceptance alone is never reported as playback success.
- Patch-series stamp seeded from run 36127826790: 144 applied, 2 intentionally skipped, 0 failed. The subsequent attempt changed no patches (0 applied); its three reverse-check failures came from overlapping upstream patches. Future builds reuse the successfully applied series.
