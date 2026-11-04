package com.atabesha.kinotv;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

/**
 * Two WebViews:
 *  - portal: the custom remote-first KinoTV interface (assets/kinotv.html). D-pad keys are
 *    forwarded to its tvKey() function.
 *  - site:   the real KinoGoline page (watch page / website). Keys get normal WebView handling
 *    so the remote works there, and Back returns to the portal exactly where you left it.
 */
public class MainActivity extends Activity {
    private static final String HOME = "https://1.kinogoline.net/";
    private WebView portal;
    private WebView site;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private FrameLayout root;

    private static boolean allowed(String url) {
        if (url == null || !url.startsWith("https://")) return false;
        try {
            String host = android.net.Uri.parse(url).getHost();
            return host != null && (host.equals("kinogoline.net") || host.endsWith(".kinogoline.net"));
        } catch (Exception e) { return false; }
    }

    private class Bridge {
        @JavascriptInterface public void openExternal(final String url) {
            runOnUiThread(() -> { if (allowed(url)) showSite(url); });
        }
        @JavascriptInterface public void exitApp() {
            runOnUiThread(MainActivity.this::finish);
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        immersive();
        CookieManager.getInstance().setAcceptCookie(true);
        root = new FrameLayout(this);
        portal = newWebView();
        portal.addJavascriptInterface(new Bridge(), "KinoTV");
        root.addView(portal, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        try {
            java.io.InputStream in = getAssets().open("kinotv.html");
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            String html = new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
            // Local TV interface, but with KinoGoline as its origin for same-site requests.
            portal.loadDataWithBaseURL(HOME, html, "text/html", "UTF-8", null);
        } catch (Exception e) {
            showSite(HOME);
        }
        portal.requestFocus();
    }

    private WebView newWebView() {
        WebView w = new WebView(this);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadsImagesAutomatically(true);
        s.setBlockNetworkImage(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setUserAgentString("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true);
        w.setFocusable(true);
        w.setFocusableInTouchMode(true);
        w.setWebViewClient(new WebViewClient());
        w.setWebChromeClient(new WebChromeClient() {
            @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                if (fullscreenView != null) { callback.onCustomViewHidden(); return; }
                fullscreenView = view; fullscreenCallback = callback;
                root.addView(view, new FrameLayout.LayoutParams(-1, -1));
                view.setFocusable(true);
                view.requestFocus();
                immersive();
            }
            @Override public void onHideCustomView() { hideFullscreen(); }
        });
        return w;
    }

    private void showSite(String url) {
        if (site == null) {
            site = newWebView();
            root.addView(site, new FrameLayout.LayoutParams(-1, -1));
        }
        site.setVisibility(View.VISIBLE);
        portal.setVisibility(View.GONE);
        site.loadUrl(url);
        site.requestFocus();
    }

    private void closeSite() {
        if (site == null) return;
        site.stopLoading();
        site.loadUrl("about:blank"); // stops video/audio
        root.removeView(site);
        site.destroy();
        site = null;
        portal.setVisibility(View.VISIBLE);
        portal.requestFocus();
    }

    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    private void hideFullscreen() {
        if (fullscreenView == null) return;
        root.removeView(fullscreenView); fullscreenView = null;
        if (fullscreenCallback != null) fullscreenCallback.onCustomViewHidden();
        fullscreenCallback = null; immersive();
        if (site != null) site.requestFocus(); else portal.requestFocus();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;

        if (code == KeyEvent.KEYCODE_BACK) {
            if (!down) return true;
            if (fullscreenView != null) { hideFullscreen(); return true; }
            if (site != null) {
                if (site.canGoBack()) site.goBack(); else closeSite();
                return true;
            }
            portal.evaluateJavascript("if(typeof goBack==='function')goBack();", null);
            return true;
        }

        // On the real website or in fullscreen video, let Android/WebView handle the remote normally.
        if (site != null || fullscreenView != null) return super.dispatchKeyEvent(event);

        if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN ||
            code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT ||
            code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_DPAD_CENTER) {
            if (down) portal.evaluateJavascript("if(typeof tvKey==='function')tvKey(" + code + ");", null);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override protected void onResume() {
        super.onResume();
        if (portal != null) portal.onResume();
        if (site != null) site.onResume();
    }
    @Override protected void onPause() {
        if (portal != null) portal.onPause();
        if (site != null) site.onPause();
        super.onPause();
    }
    @Override protected void onDestroy() {
        if (site != null) { site.stopLoading(); site.destroy(); site = null; }
        if (portal != null) { portal.stopLoading(); portal.destroy(); portal = null; }
        super.onDestroy();
    }
}
