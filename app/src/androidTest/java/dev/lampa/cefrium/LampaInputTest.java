package dev.lampa.cefrium;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** End-to-end tests: real Android input, real Cefrium renderer, real Lampa UI. */
@RunWith(AndroidJUnit4.class)
public class LampaInputTest {
    private MainActivity activity;

    private JSONObject query(String expression) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            activity.browser.setQueryHandler((id, request, origin, callback) -> {
                result.set(request);
                callback.success("ok");
                done.countDown();
                return true;
            });
            activity.browser.evaluateJavaScript("(()=>{try{window.cefriumQuery({request:JSON.stringify({value:("
                + expression + ")}),onSuccess:()=>{},onFailure:()=>{}})}catch(e){window.cefriumQuery({request:JSON.stringify({error:String(e)}),onSuccess:()=>{},onFailure:()=>{}})}})()");
        });
        assertTrue("Cefrium JavaScript bridge did not respond", done.await(10, TimeUnit.SECONDS));
        JSONObject object = new JSONObject(result.get());
        assertFalse(object.toString(), object.has("error"));
        return object;
    }

    private String value(String expression) throws Exception {
        return query(expression).optString("value", "");
    }

    private void waitFor(String expression) throws Exception {
        long end = SystemClock.uptimeMillis() + 90000;
        while (SystemClock.uptimeMillis() < end) {
            if (query(expression).optBoolean("value")) return;
            SystemClock.sleep(700);
        }
        fail("Timed out waiting for: " + expression + "; body=" + value("document.body.innerText.slice(0,1500)"));
    }

    private void screenshot(String name) throws Exception {
        File directory = new File(activity.getExternalFilesDir(null), "evidence");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull("No screenshot returned", bitmap);
        try (FileOutputStream out = new FileOutputStream(new File(directory, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
        }
        bitmap.recycle();
        exportEvidence(name + ".png");
    }

    private void exportEvidence(String name) throws Exception {
        // UTP uninstalls the app after the suite. Preserve evidence outside app data.
        File source = new File(activity.getExternalFilesDir(null), "evidence/" + name);
        String command = "mkdir -p /sdcard/Download/lampa-probe-evidence && cp '"
            + source.getAbsolutePath() + "' '/sdcard/Download/lampa-probe-evidence/" + name + "'";
        try (ParcelFileDescriptor fd = InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);
             java.io.InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(fd)) {
            while (in.read() != -1) { /* Wait for the shell copy before teardown. */ }
        }
    }

    private void tap(String selector) throws Exception {
        JSONObject point = query("(()=>{const e=document.querySelector(" + JSONObject.quote(selector)
            + ");if(!e)throw new Error('Missing element');const r=e.getBoundingClientRect();return {x:(r.x+r.width/2)*devicePixelRatio,y:(r.y+r.height/2)*devicePixelRatio,w:r.width,h:r.height}})()").getJSONObject("value");
        assertTrue(point.toString(), point.getDouble("w") > 0 && point.getDouble("h") > 0);
        int[] offset = new int[2];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> activity.browser.getSurfaceContainer().getLocationOnScreen(offset));
        float x = (float)point.getDouble("x") + offset[0];
        float y = (float)point.getDouble("y") + offset[1];
        Log.i("LampaProbe", "Tap " + selector + " page=" + point + " screen=" + x + "," + y
            + " viewport=" + value("JSON.stringify({w:innerWidth,h:innerHeight,dpr:devicePixelRatio,scale:visualViewport.scale})"));
        long time = SystemClock.uptimeMillis();
        for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
            MotionEvent event = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action, x, y, 0);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            InstrumentationRegistry.getInstrumentation().sendPointerSync(event);
            event.recycle();
            SystemClock.sleep(100);
        }
        SystemClock.sleep(600);
    }

    private void key(int code) {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(code);
        SystemClock.sleep(500);
    }

    private void exercise(String mode) throws Exception {
        Intent intent = new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(), MainActivity.class);
        intent.putExtra("input_mode", mode);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(a -> activity = a);
            try {
            SystemClock.sleep(4000);
            waitFor("Boolean(window.appready && window.show_app && document.querySelector('.open--settings'))");
            assertTrue("Not Chromium 152: " + value("navigator.userAgent"), value("navigator.userAgent").contains("152."));
            assertEquals("Lampa navigation setting was not applied", mode.equals("touch") ? "touch" : "controll", value("Lampa.Storage.field('navigation_type')"));
            assertEquals("Wrong Lampa layout", mode.equals("tv"), query("Lampa.Platform.screen('tv')").getBoolean("value"));
            assertEquals("Language must be stored as a scalar string", "ru", value("Lampa.Storage.get('language')"));
            screenshot(mode + "-01-lampa");

            // The test instruments observation, not navigation or input behavior.
            query("(()=>{window.__input={touch:0,keys:[]};document.addEventListener('touchstart',e=>{if(e.isTrusted)window.__input.touch++},true);document.addEventListener('keydown',e=>window.__input.keys.push({code:e.keyCode,trusted:e.isTrusted}),true);return true})()");
            String settingsButton = mode.equals("touch") ? ".navigation-bar__item[data-action=settings]" : ".open--settings";
            tap(settingsButton);
            waitFor("Lampa.Controller.enabled().name === 'settings'");
            assertTrue("A real touchscreen event was not delivered", query("window.__input.touch>0").getBoolean("value"));
            screenshot(mode + "-02-touch-settings");
            waitFor("Boolean(Navigator.getFocusedElement())");
            String initial = value("(()=>{window.__probeInitialFocus=Navigator.getFocusedElement();return window.__probeInitialFocus.textContent.trim()})()");
            key(KeyEvent.KEYCODE_DPAD_DOWN);
            waitFor("Navigator.getFocusedElement() && Navigator.getFocusedElement() !== window.__probeInitialFocus");
            screenshot(mode + "-03-dpad-focus");
            key(KeyEvent.KEYCODE_DPAD_UP);
            assertTrue("DPAD_UP should restore selection: " + initial,
                query("Navigator.getFocusedElement() === window.__probeInitialFocus").getBoolean("value"));
            key(KeyEvent.KEYCODE_DPAD_CENTER);
            waitFor("Lampa.Controller.enabled().name === 'settings_component'");
            screenshot(mode + "-04-ok-opened");
            key(KeyEvent.KEYCODE_BACK);
            waitFor("Lampa.Controller.enabled().name === 'settings'");
            key(KeyEvent.KEYCODE_BACK);
            waitFor("!document.body.classList.contains('settings--open')");
            SystemClock.sleep(500);
            tap(settingsButton);
            waitFor("Lampa.Controller.enabled().name === 'settings'");
            screenshot(mode + "-05-touch-after-remote");
            String report = query("({mode:" + JSONObject.quote(mode) + ",ua:navigator.userAgent,platform:Lampa.Platform.get(),controller:Lampa.Controller.enabled().name,input:window.__input,viewport:[innerWidth,innerHeight,devicePixelRatio]})").toString(2);
            try (FileOutputStream out = new FileOutputStream(new File(activity.getExternalFilesDir(null), "evidence/"+mode+"-report.json"))) {
                out.write(report.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            exportEvidence(mode + "-report.json");
            } catch (Exception | AssertionError failure) {
                try { screenshot(mode + "-FAILED"); } catch (Exception captureFailure) { failure.addSuppressed(captureFailure); }
                throw failure;
            }
        }
    }

    @Test public void touchAndRemoteOnTvLayout() throws Exception { exercise("tv"); }
    @Test public void touchAndRemoteOnMobileLayout() throws Exception { exercise("touch"); }
}
