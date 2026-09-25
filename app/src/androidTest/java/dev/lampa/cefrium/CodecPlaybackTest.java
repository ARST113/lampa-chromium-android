package dev.lampa.cefrium;

import android.content.Intent;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.KeyEvent;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Real HTMLMediaElement -> WebAudio PCM, plus seek and platform codec inventory. */
@RunWith(AndroidJUnit4.class)
public class CodecPlaybackTest {
    private MainActivity activity;

    private JSONObject query(String expression) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            activity.browser.setQueryHandler((id, request, origin, callback) -> {
                result.set(request); callback.success("ok"); done.countDown(); return true;
            });
            activity.browser.evaluateJavaScript("window.cefriumQuery({request:JSON.stringify({value:(" + expression
                + ")}),onSuccess:()=>{},onFailure:()=>{}})");
        });
        assertTrue("Renderer did not answer", done.await(10, TimeUnit.SECONDS));
        return new JSONObject(result.get());
    }

    private String shell(String command) throws Exception {
        try (ParcelFileDescriptor fd = InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);
             java.io.InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(fd)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return out.toString("UTF-8");
        }
    }

    private void save(JSONObject report) throws Exception {
        File source = new File(activity.getExternalFilesDir(null), "codec-report.json");
        try (FileOutputStream out = new FileOutputStream(source)) {out.write(report.toString(2).getBytes("UTF-8"));}
        String dest = "/sdcard/Download/lampa-probe-evidence/codec-report.json";
        shell("mkdir -p /sdcard/Download/lampa-probe-evidence");
        shell("cp " + source.getAbsolutePath() + " " + dest);
        assertEquals(Long.toString(source.length()), shell("stat -c %s " + dest).trim());
    }

    @Test public void playbackAndDolbyCapability() throws Exception {
        Intent intent = new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(), MainActivity.class);
        intent.putExtra("codec_probe", true);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(a -> activity = a);
            SystemClock.sleep(4000);
            long deadline = SystemClock.uptimeMillis() + 60000;
            while (!query("Boolean(window.__codecReady)").optBoolean("value") && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(500);
            assertTrue("Codec page did not load", query("Boolean(window.__codecReady)").optBoolean("value"));
            query("document.querySelector('#run').focus() || true");
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            deadline = SystemClock.uptimeMillis() + 180000;
            while (!query("Boolean(window.__codecResults && window.__codecResults.done)").optBoolean("value") && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(1000);
            JSONObject report = query("window.__codecResults || {}").getJSONObject("value");
            JSONArray decoders = new JSONArray();
            boolean ac3 = false, eac3 = false;
            for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos()) {
                if (info.isEncoder()) continue;
                for (String type : info.getSupportedTypes()) {
                    if (type.startsWith("audio/") || type.equals("video/hevc")) {
                        decoders.put(new JSONObject().put("name", info.getName()).put("type", type));
                    }
                    if (type.equals("audio/ac3")) ac3 = true;
                    if (type.equals("audio/eac3")) eac3 = true;
                }
            }
            report.put("platformDecoders", decoders).put("platformAc3", ac3).put("platformEac3", eac3)
                .put("androidApi", android.os.Build.VERSION.SDK_INT).put("model", android.os.Build.MODEL);
            save(report); // Keep the full measurements even when the baseline fails.
            assertTrue(report.toString(), report.optBoolean("done"));
            assertFalse(report.toString(), report.has("error"));
            JSONArray rows = report.getJSONArray("results");
            assertEquals(8, rows.length());
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                String name = row.getString("file");
                if (name.startsWith("h264-aac")) assertTrue("AAC control failed: " + row, row.getBoolean("passed"));
            }
            assertFalse("AC3 MIME is disabled in this engine", report.getJSONObject("mime").getString("ac3").isEmpty());
            assertFalse("EAC3 MIME is disabled in this engine", report.getJSONObject("mime").getString("eac3").isEmpty());
            // Emulator images may have no Dolby decoder. Never turn MIME-only success
            // into a playback claim; require measured PCM when a decoder is present.
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i); String name = row.getString("file");
                if ((ac3 && name.startsWith("h264-ac3")) || (eac3 && name.startsWith("h264-eac3"))) {
                    assertTrue("Platform decoder advertised but playback failed: " + row, row.getBoolean("passed"));
                }
            }
        }
    }
}
