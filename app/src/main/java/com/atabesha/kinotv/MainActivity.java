package com.atabesha.kinotv;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.JavascriptInterface;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final String HOME = "https://1.kinogoline.net/";
    private WebView web;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(5894 | 1024 | 512);
        web = new WebView(this);
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadsImagesAutomatically(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setUserAgentString(s.getUserAgentString() + " KinoTV/1.0 AndroidTV");
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.addJavascriptInterface(new TvBridge(), "KinoTV");
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                super.onShowCustomView(view, callback);
                getWindow().getDecorView().setSystemUiVisibility(5894 | 1024 | 512);
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                return !allowedSite(request.getUrl().toString());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !allowedSite(url);
            }
        });

        // Give the custom UI the target site's origin so its same-origin catalogue
        // requests can be made by WebView without a file:// CORS origin.
        try (InputStream in = getAssets().open("kinotv.html");
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            String html = new String(out.toByteArray(), StandardCharsets.UTF_8);
            web.loadDataWithBaseURL(HOME, html, "text/html", "UTF-8", null);
        } catch (Exception e) {
            web.loadData("<h2>KinoTV could not load its interface.</h2>", "text/html", "UTF-8");
        }
    }

    private boolean allowedSite(String url) {
        return url.startsWith("https://1.kinogoline.net/") ||
               url.startsWith("https://www.kinogoline.net/") ||
               url.startsWith("https://kinogoline.net/");
    }

    private class TvBridge {
        @JavascriptInterface public void reload() { runOnUiThread(() -> web.evaluateJavascript("loadHome()", null)); }
        @JavascriptInterface public void openExternal(String url) {
            if (url != null && (url.startsWith("https://1.kinogoline.net/") ||
                url.startsWith("https://www.kinogoline.net/") || url.startsWith("https://kinogoline.net/"))) {
                runOnUiThread(() -> web.loadUrl(url));
            }
        }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int key = event.getKeyCode();
            if (key == KeyEvent.KEYCODE_BACK) {
                if (web != null) web.evaluateJavascript("goBack()", null);
                return true;
            }
            if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN ||
                key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT ||
                key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
                if (web != null) web.evaluateJavascript("tvKey(" + key + ")", null);
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override protected void onDestroy() {
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }
}
