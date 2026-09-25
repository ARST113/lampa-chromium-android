# Built-in software AC3 / EAC3

This build enables AC3 and EAC3 in FFmpeg's actual configure inputs, regenerates
Android ARM64/x64 configs and the GN source list, and adds the missing Chromium
AudioCodec-to-AVCodecID mapping. The Chromium Dolby format flag enables demuxing
and MIME recognition. Bitstream output is disabled for this variant: decoding
produces PCM and does not require an Android Dolby decoder or HDMI receiver.

Source identities are pinned in dependencies/native-engine.json. GitHub Actions
owns the build; the unprivileged VPS runner compiles the native ARM64 engine,
packages the same Lampa frontend, and produces an APK with a checksummed AAR.
The default Gradle build still uses the published SDK for baseline comparison.

Run infra/bootstrap-native.sh once as administrator after the base worker
bootstrap. Chromium sources and compiler outputs persist under
/build/lampa-ci/native-152. The scripts reject unexpected revisions and never
automatically reset or clean an existing source checkout.

The native build records GN options, Chromium patches, generated FFmpeg configs
and source list, dependency revisions, compiler logs, and AAR/APK hashes.
The first build is ARM64 only. Emulator runs and the full x64 engine build are
not in the critical path, as requested by the user.

The APK includes a separate diagnostic page with original 12-second colour
patterns and a 440 Hz tone (AAC/AC3/EAC3 in MP4/MKV). Open it on a device with:

```sh
adb shell am force-stop dev.lampa.cefrium.probe
adb shell am start -n dev.lampa.cefrium.probe/dev.lampa.cefrium.MainActivity --ez codec_probe true
```

The normal launcher opens Lampa. No successful playback claim is made merely
because compilation finishes; runtime playback measurements are recorded
separately when performed.
