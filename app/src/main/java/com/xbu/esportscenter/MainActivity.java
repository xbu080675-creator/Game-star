package com.xbu.esportscenter;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    private FrameLayout root;
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            configureWindow();
            root = new FrameLayout(this);
            root.setBackgroundColor(Color.rgb(2, 7, 11));
            setContentView(root);
            createWebView(false);
            root.post(this::hideSystemBars);
        } catch (Throwable t) {
            showFatal("STARTUP", t);
        }
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(lp);
        }
    }

    private void createWebView(boolean safeMode) {
        if (webView != null) {
            root.removeView(webView);
            webView.destroy();
        }

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(2, 7, 11));

        if (safeMode) {
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        webView.addJavascriptInterface(new NativeBridge(), "GSBNative");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                final boolean didCrash = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && detail.didCrash();
                runOnUiThread(() -> {
                    try {
                        createWebView(true);
                    } catch (Throwable t) {
                        showFatal(didCrash ? "WEBVIEW_RENDERER_CRASH" : "WEBVIEW_RENDERER_KILLED", t);
                    }
                });
                return true;
            }
        });

        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        webView.loadUrl("file:///android_asset/index.html");
    }

    private class NativeBridge {
        @JavascriptInterface
        public void haptic(String cue) {
            runOnUiThread(() -> emitHaptic(cue));
        }

        @JavascriptInterface
        public boolean canHaptic() {
            return canUseRefinedHaptics();
        }
    }

    private boolean isSystemHapticsEnabled() {
        try {
            return Settings.System.getInt(
                    getContentResolver(),
                    Settings.System.HAPTIC_FEEDBACK_ENABLED,
                    1
            ) == 1;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private Vibrator getRefinedVibrator() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null;
        try {
            VibratorManager manager = getSystemService(VibratorManager.class);
            if (manager == null) return null;
            Vibrator vibrator = manager.getDefaultVibrator();
            if (vibrator == null || !vibrator.hasVibrator()) return null;
            // No amplitude control usually means a coarse ERM-style motor. Stay silent rather than buzz.
            if (!vibrator.hasAmplitudeControl()) return null;
            return vibrator;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean canUseRefinedHaptics() {
        return isSystemHapticsEnabled() && getRefinedVibrator() != null;
    }

    private void emitHaptic(String cue) {
        if (!isSystemHapticsEnabled()) return;

        Vibrator vibrator = getRefinedVibrator();
        if (vibrator == null) return;

        try {
            final int effectId;
            if ("tick".equals(cue)) {
                effectId = VibrationEffect.EFFECT_TICK;
            } else if ("click".equals(cue) || "test".equals(cue)) {
                effectId = VibrationEffect.EFFECT_CLICK;
            } else {
                return;
            }

            // Android 12+ refined predefined haptics. No long one-shot or waveform fallback.
            vibrator.vibrate(VibrationEffect.createPredefined(effectId));
        } catch (Throwable ignored) {
            // Unsupported device/effect: intentionally degrade to animation + audio only.
        }
    }

    private void hideSystemBars() {
        try {
            Window window = getWindow();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false);
                WindowInsetsController controller = window.getInsetsController();
                if (controller != null) {
                    controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    controller.setSystemBarsBehavior(
                            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    );
                }
            } else {
                window.getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                | View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                );
            }
        } catch (Throwable ignored) {
        }
    }

    private void showFatal(String stage, Throwable t) {
        try {
            if (root == null) {
                root = new FrameLayout(this);
                root.setBackgroundColor(Color.rgb(2, 7, 11));
                setContentView(root);
            } else {
                root.removeAllViews();
            }

            TextView text = new TextView(this);
            text.setTextColor(Color.WHITE);
            text.setTextSize(16f);
            text.setGravity(Gravity.CENTER);
            text.setPadding(48, 48, 48, 48);
            text.setText(
                    "电竞中心启动失败\n\n" +
                    "Stage: " + stage + "\n" +
                    "Error: " + t.getClass().getSimpleName() + "\n" +
                    String.valueOf(t.getMessage())
            );
            root.addView(text, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
