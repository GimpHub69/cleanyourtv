package io.github.cleanyourtv.news;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * The full article: the story's link in the TV's WebView (Silk is disabled on a debloated Fire TV, and a
 * browser would need the leanback intent anyway). Up and Down scroll, Back returns to the headlines.
 * JavaScript is on because news sites render their text and pictures with it; nothing else is enabled.
 */
public final class ArticleActivity extends Activity {
    static final String EXTRA_URL = "url";
    private WebView web;

    @Override
    @SuppressWarnings("SetJavaScriptEnabled")
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        String url = getIntent().getStringExtra(EXTRA_URL);
        if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) {
            finish();
            return;
        }
        web = new WebView(this);
        web.setBackgroundColor(0xFF14161A);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setMediaPlaybackRequiresUserGesture(true);
        web.setWebViewClient(new WebViewClient());
        setContentView(web);
        web.loadUrl(url);
        web.requestFocus();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // A page's own focus handling can swallow the arrows; scroll by a third of the screen ourselves.
        if (web != null && (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_UP)) {
            web.scrollBy(0, (keyCode == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1) * web.getHeight() / 3);
            return true;
        }
        if (web != null && keyCode == KeyEvent.KEYCODE_BACK && web.canGoBack()) {
            web.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
