package dev.lampa.cefrium;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.graphics.drawable.GradientDrawable;

import com.cefrium.CefriumBrowser;
import org.chromium.base.CommandLine;

/**
 * Lampa host with two interchangeable browser engines:
 * Cefrium/Chromium 152 (custom AC3/EAC3 runtime) and Android System WebView.
 *
 * The instrumentation harness still forces the local bundled Lampa + Cefrium,
 * while normal launches expose a persistent server/engine menu to the user.
 */
public final class MainActivity extends Activity {
    private static final String TAG = "LampaHost";
    private static final String PREFS = "lampa_host";
    private static final String PREF_ENGINE = "browser_engine";
    private static final String PREF_SERVER = "lampa_server";
    private static final String ENGINE_CEFRIUM = "cefrium";
    private static final String ENGINE_SYSTEM = "system";
    private static final String DEFAULT_SERVER = LOCAL_SERVER;
    private static final String LOCAL_SERVER = "local";

    CefriumBrowser browser; // package-visible for instrumentation tests
    private WebView systemWebView;
    private FrameLayout root;
    private SharedPreferences prefs;
    private boolean probeMode;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        probeMode = getIntent().hasExtra("input_mode") || getIntent().getBooleanExtra("codec_probe", false);

        root = new FrameLayout(this);
        setContentView(root);

        if (probeMode || ENGINE_CEFRIUM.equals(prefs.getString(PREF_ENGINE, ENGINE_CEFRIUM))) {
            startCefrium();
        } else {
            startSystemWebView();
        }

        if (!probeMode) addSettingsButton();
    }

    private String selectedServerUrl() {
        if (probeMode) {
            String mode = "touch".equals(getIntent().getStringExtra("input_mode")) ? "touch" : "tv";
            return getIntent().getBooleanExtra("codec_probe", false)
                ? "file:///android_asset/codecs/index.html"
                : "file:///android_asset/lampa/index.html?input=" + mode;
        }

        String server = prefs.getString(PREF_SERVER, DEFAULT_SERVER);
        if (server == null || server.trim().isEmpty()) server = DEFAULT_SERVER;
        server = server.trim();

        if (LOCAL_SERVER.equalsIgnoreCase(server)) {
            return "file:///android_asset/lampa/index.html?input=touch";
        }
        if (!server.contains("://")) server = "http://" + server;
        return server;
    }

    private void startCefrium() {
        // Cefrium's embedded Chromium contains the regular Java sandboxed child
        // services, but this runtime does not expose the native-only service path.
        // Android 15+/17 otherwise selects NativeOnlySandboxedProcessService and
        // crashes the process before the first page is rendered.
        if (!CommandLine.isInitialized()) CommandLine.init(null);
        CommandLine.getInstance().appendSwitchWithValue("javaless-renderers", "disabled");
        // Lampa's internal player switches embedded torrent audio streams through
        // HTMLMediaElement.audioTracks. Chromium 152 keeps AudioVideoTracks behind
        // a Blink runtime feature, so expose it explicitly for the embedded engine.
        CommandLine.getInstance().appendSwitchWithValue("enable-blink-features", "AudioVideoTracks");
        browser = CefriumBrowser.createWithSurface(this);
        browser.setOnRenderProcessTerminatedListener((status, error) ->
            Log.e("LampaProbe", "Renderer terminated: " + status + "/" + error));

        View surface = browser.getSurfaceContainer();
        root.addView(surface, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        browser.loadUrl(selectedServerUrl());
    }

    @SuppressWarnings("SetJavaScriptEnabled")
    private void startSystemWebView() {
        systemWebView = new WebView(this);
        systemWebView.setBackgroundColor(Color.BLACK);
        systemWebView.setFocusable(true);
        systemWebView.setFocusableInTouchMode(true);

        WebSettings settings = systemWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        WebView.setWebContentsDebuggingEnabled(true);
        systemWebView.setWebViewClient(new WebViewClient());

        root.addView(systemWebView, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        systemWebView.loadUrl(selectedServerUrl());
    }

    private void addSettingsButton() {
        ImageButton button = new ImageButton(this);
        button.setImageResource(R.drawable.lampa_fab_icon);
        button.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        button.setContentDescription("Меню Lampa");
        button.setPadding(dp(11), dp(11), dp(11), dp(11));

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(Color.rgb(28, 30, 31));
        button.setBackground(background);
        button.setElevation(dp(8));
        button.setOnClickListener(v -> showHostSettings());

        int size = dp(52);
        int margin = dp(16);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
        lp.gravity = Gravity.BOTTOM | Gravity.END;
        lp.bottomMargin = margin;
        lp.rightMargin = margin;
        root.addView(button, lp);
        button.bringToFront();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showHostSettings() {
        String engine = prefs.getString(PREF_ENGINE, ENGINE_CEFRIUM);
        String server = prefs.getString(PREF_SERVER, DEFAULT_SERVER);
        String engineLabel = ENGINE_SYSTEM.equals(engine)
            ? "Системный Android WebView"
            : "Cefrium / Chromium 152 + AC3/EAC3";

        String[] items = {
            "Сменить сервер" + (LOCAL_SERVER.equalsIgnoreCase(server) ? " · встроенная Lampa" : " · " + server),
            "Сменить движок" + " · " + engineLabel,
            "Перезагрузить страницу"
        };

        new AlertDialog.Builder(this)
            .setTitle("Lampa")
            .setItems(items, (dialog, which) -> {
                if (which == 0) showServerDialog();
                else if (which == 1) showEngineDialog();
                else reloadCurrentPage();
            })
            .setNegativeButton("Закрыть", null)
            .show();
    }

    private void showServerDialog() {
        final EditText input = new EditText(this);
        String current = prefs.getString(PREF_SERVER, DEFAULT_SERVER);
        input.setSingleLine(true);
        input.setText(LOCAL_SERVER.equalsIgnoreCase(current) ? DEFAULT_SERVER : current);
        input.setSelection(input.getText().length());
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Сервер Lampa")
            .setMessage("Укажи адрес Lampa, например http://lampa.mx или свой сервер.")
            .setView(input)
            .setPositiveButton("Сохранить", (d, w) -> {
                String value = input.getText().toString().trim();
                if (value.isEmpty()) value = DEFAULT_SERVER;
                prefs.edit().putString(PREF_SERVER, value).apply();
                recreate();
            })
            .setNeutralButton("Встроенная", (d, w) -> {
                prefs.edit().putString(PREF_SERVER, LOCAL_SERVER).apply();
                recreate();
            })
            .setNegativeButton("Отмена", null)
            .create();
        dialog.show();
    }

    private void showEngineDialog() {
        String current = prefs.getString(PREF_ENGINE, ENGINE_CEFRIUM);
        String[] engines = {
            "Cefrium / Chromium 152 + AC3/EAC3",
            "Системный Android WebView"
        };
        int checked = ENGINE_SYSTEM.equals(current) ? 1 : 0;

        new AlertDialog.Builder(this)
            .setTitle("Браузерный движок")
            .setSingleChoiceItems(engines, checked, (dialog, which) -> {
                prefs.edit().putString(PREF_ENGINE, which == 1 ? ENGINE_SYSTEM : ENGINE_CEFRIUM).apply();
                dialog.dismiss();
                recreate();
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private void reloadCurrentPage() {
        if (browser != null) {
            browser.loadUrl(selectedServerUrl());
        } else if (systemWebView != null) {
            systemWebView.loadUrl(selectedServerUrl());
        }
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!probeMode && (keyCode == KeyEvent.KEYCODE_MENU
            || keyCode == KeyEvent.KEYCODE_TV_CONTENTS_MENU
            || keyCode == KeyEvent.KEYCODE_TV_MEDIA_CONTEXT_MENU)) {
            showHostSettings();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (!probeMode && keyCode == KeyEvent.KEYCODE_BACK) {
            showHostSettings();
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    @Override public void onBackPressed() {
        if (systemWebView != null) {
            if (systemWebView.canGoBack()) systemWebView.goBack();
            else super.onBackPressed();
            return;
        }
        if (browser != null) {
            browser.evaluateJavaScript("if(window.appready && window.Lampa){Lampa.Controller.back()}else{history.back()}");
            return;
        }
        super.onBackPressed();
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        if (browser == null || !browser.onActivityResult(request, result, data)) {
            super.onActivityResult(request, result, data);
        }
    }

    @Override protected void onDestroy() {
        if (systemWebView != null) {
            root.removeView(systemWebView);
            systemWebView.destroy();
            systemWebView = null;
        }
        if (browser != null) {
            browser.close();
            browser = null;
        }
        super.onDestroy();
    }
}
