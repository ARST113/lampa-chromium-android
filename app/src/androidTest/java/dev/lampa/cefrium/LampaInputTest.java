package dev.lampa.cefrium;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
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
    @Rule public TestWatcher failureScreenshot = new TestWatcher() {
        @Override protected void failed(Throwable error, Description description) {
            if (activity != null) try { screenshot("FAILED-" + description.getMethodName()); } catch (Exception ignored) { }
        }
    };

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
    }

    private void tap(String selector) throws Exception {
        JSONObject point = query("(()=>{const e=document.querySelector(" + JSONObject.quote(selector)
            + ");if(!e)throw new Error('Missing element');const r=e.getBoundingClientRect();return {x:(r.x+r.width/2)*devicePixelRatio,y:(r.y+r.height/2)*devicePixelRatio,w:r.width,h:r.height}})()").getJSONObject("value");
        assertTrue(point.toString(), point.getDouble("w") > 0 && point.getDouble("h") > 0);
        int[] offset = new int[2];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> activity.browser.getSurfaceContainer().getLocationOnScreen(offset));
        float x = (float)point.getDouble("x") + offset[0];
        float y = (float)point.getDouble("y") + offset[1];
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
            SystemClock.sleep(4000);
            waitFor("Boolean(window.appready && window.show_app && document.querySelector('.open--settings'))");
            assertTrue("Not Chromium 152: " + value("navigator.userAgent"), value("navigator.userAgent").contains("152."));
            screenshot(mode + "-01-lampa");

            // The test instruments observation, not navigation or input behavior.
            query("(()=>{window.__input={touch:0,keys:[]};document.addEventListener('touchstart',e=>{if(e.isTrusted)window.__input.touch++},true);document.addEventListener('keydown',e=>window.__input.keys.push({code:e.keyCode,trusted:e.isTrusted}),true);return true})()");
            tap(".open--settings");
            waitFor("Lampa.Controller.enabled().name === 'settings'");
            assertTrue("A real touchscreen event was not delivered", query("window.__input.touch>0").getBoolean("value"));
            screenshot(mode + "-02-touch-settings");
            String initial = value("document.querySelector('.settings .selector.focus')?.textContent.trim() || ''");
            key(KeyEvent.KEYCODE_DPAD_DOWN);
            waitFor("Boolean(document.querySelector('.settings .selector.focus'))");
            String next = value("document.querySelector('.settings .selector.focus')?.textContent.trim() || ''");
            assertFalse("DPAD_DOWN did not move Lampa focus: " + initial, next.equals(initial));
            screenshot(mode + "-03-dpad-focus");
            key(KeyEvent.KEYCODE_DPAD_UP);
            assertEquals("DPAD_UP should restore the first selection", initial,
                value("document.querySelector('.settings .selector.focus')?.textContent.trim() || ''"));
            key(KeyEvent.KEYCODE_DPAD_CENTER);
            waitFor("Lampa.Controller.enabled().name === 'settings_component'");
            screenshot(mode + "-04-ok-opened");
            key(KeyEvent.KEYCODE_BACK);
            waitFor("Lampa.Controller.enabled().name === 'settings'");
            key(KeyEvent.KEYCODE_BACK);
            waitFor("!document.body.classList.contains('settings--open')");
            SystemClock.sleep(500);
            tap(".open--settings");
            waitFor("Lampa.Controller.enabled().name === 'settings'");
            screenshot(mode + "-05-touch-after-remote");
            String report = query("({mode:" + JSONObject.quote(mode) + ",ua:navigator.userAgent,platform:Lampa.Platform.get(),controller:Lampa.Controller.enabled().name,input:window.__input,viewport:[innerWidth,innerHeight,devicePixelRatio]})").toString(2);
            try (FileOutputStream out = new FileOutputStream(new File(activity.getExternalFilesDir(null), "evidence/"+mode+"-report.json"))) {
                out.write(report.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        }
    }

    @Test public void touchAndRemoteOnTvLayout() throws Exception { exercise("tv"); }
    @Test public void touchAndRemoteOnMobileLayout() throws Exception { exercise("touch"); }
}
