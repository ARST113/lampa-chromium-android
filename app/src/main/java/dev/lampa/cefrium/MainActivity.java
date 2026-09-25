package dev.lampa.cefrium;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.util.Log;
import com.cefrium.CefriumBrowser;

/** Feasibility host for the real Lampa frontend in the unmodified Cefrium SDK. */
public final class MainActivity extends Activity {
    CefriumBrowser browser;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
        browser = CefriumBrowser.createWithSurface(this);
        browser.setOnRenderProcessTerminatedListener((status, error) -> Log.e("LampaProbe", "Renderer terminated: " + status + "/" + error));
        setContentView(browser.getSurfaceContainer());
        String mode = "touch".equals(getIntent().getStringExtra("input_mode")) ? "touch" : "tv";
        browser.loadUrl("file:///android_asset/lampa/index.html?input=" + mode);
    }

    @Override public void onBackPressed() {
        if (browser != null) browser.evaluateJavaScript("if(window.appready && window.Lampa){Lampa.Controller.back()}else{history.back()}");
    }

    @Override protected void onActivityResult(int request, int result, android.content.Intent data) {
        if (browser == null || !browser.onActivityResult(request, result, data)) super.onActivityResult(request, result, data);
    }

    @Override protected void onDestroy() {
        if (browser != null) { browser.close(); browser = null; }
        super.onDestroy();
    }
}
