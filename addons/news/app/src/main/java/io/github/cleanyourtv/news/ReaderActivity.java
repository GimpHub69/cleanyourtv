package io.github.cleanyourtv.news;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.KeyEvent;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.List;

/**
 * The article as text: title, source and time, the story's cached picture, then the paragraphs Reader found on the
 * page. Up and Down scroll, OK opens the publisher's page (ArticleActivity), Back returns to the headlines. A page
 * with fewer than two paragraphs goes straight to the publisher's page.
 */
public final class ReaderActivity extends Activity {
    static final String EXTRA_URL = "url", EXTRA_TITLE = "title", EXTRA_META = "meta", EXTRA_IMAGE = "image";
    private String url;
    private TextView body;
    private boolean alive = true;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        url = getIntent().getStringExtra(EXTRA_URL);
        if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) {
            finish();
            return;
        }
        setContentView(R.layout.activity_reader);
        ScrollView scroll = findViewById(R.id.scroll);
        TextView title = findViewById(R.id.readerTitle);
        TextView meta = findViewById(R.id.readerMeta);
        ImageView picture = findViewById(R.id.readerPicture);
        body = findViewById(R.id.readerBody);
        title.setText(getIntent().getStringExtra(EXTRA_TITLE));
        meta.setText(getIntent().getStringExtra(EXTRA_META));
        String image = getIntent().getStringExtra(EXTRA_IMAGE);
        File f = image == null || image.isEmpty() ? null : Feed.imageFile(this, image);
        if (f != null && f.isFile()) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            picture.setImageBitmap(BitmapFactory.decodeFile(f.getPath(), o));
        } else {
            picture.setVisibility(android.view.View.GONE);
        }
        body.setText(R.string.reader_loading);
        scroll.requestFocus();
        new Thread(() -> {
            Reader.Article article = null;
            try {
                article = Reader.extract(Reader.fetch(url));
            } catch (Exception e) {
                android.util.Log.w("news", "reader failed: " + e);
            }
            final Reader.Article got = article;
            runOnUiThread(() -> show(got, title));
        }, "news-reader").start();
    }

    private void show(Reader.Article article, TextView title) {
        if (!alive) return;
        if (article == null || article.paragraphs.size() < 2) {
            // Nothing readable on the page: the publisher's own page instead.
            openWeb();
            finish();
            return;
        }
        if (title.getText().length() == 0 && !article.title.isEmpty()) title.setText(article.title);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        List<String> ps = article.paragraphs;
        for (int i = 0; i < ps.size(); i++) {
            String p = ps.get(i);
            int start = sb.length();
            if (p.startsWith("§ ")) {
                sb.append(p.substring(2));
                sb.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new RelativeSizeSpan(1.15f), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                sb.append(p);
            }
            if (i < ps.size() - 1) sb.append("\n\n");
        }
        body.setText(sb);
    }

    private void openWeb() {
        startActivity(new Intent(this, ArticleActivity.class).putExtra(ArticleActivity.EXTRA_URL, url));
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            openWeb();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        alive = false;
        super.onDestroy();
    }
}
