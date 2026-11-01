package com.gathin.tvhome;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.view.View;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * One headline under the clock from another app on this TV (config.xml news_provider): the launcher queries that
 * content provider for rows with {@code title} and {@code source} columns, on each return to Home and each
 * minute, and shows the next one every ROTATE_MS while Home is on screen. No network here: the provider is a
 * local app (the News add-on), which fetches on its own. Rows older than STALE_MS by the provider's
 * {@code fetched} column are not shown: an offline TV keeps no day-old headline under the clock.
 */
final class NewsLine {
    static final long ROTATE_MS = 20_000L, STALE_MS = 6 * 60 * 60 * 1000L;
    private final TextView line;
    private final String uri;
    private List<String> headlines = new ArrayList<>();
    private int next;
    private final Runnable rotate = this::showNext;

    NewsLine(TextView line, String uri) {
        this.line = line;
        this.uri = uri;
    }

    /** On Home: a fresh read, and the rotation from now. */
    void start(ContentResolver resolver) {
        refresh(resolver);
        line.removeCallbacks(rotate);
        line.postDelayed(rotate, ROTATE_MS);
    }

    void stop() {
        line.removeCallbacks(rotate);
    }

    /** Reads the provider off the main thread; the list swaps in on the UI thread, keeping its place. */
    void refresh(ContentResolver resolver) {
        new Thread(() -> {
            List<String> fresh = new ArrayList<>();
            try (Cursor c = resolver.query(Uri.parse(uri), new String[]{"title", "source", "fetched"}, null, null, null)) {
                if (c != null) {
                    int t = c.getColumnIndex("title"), s = c.getColumnIndex("source"), f = c.getColumnIndex("fetched");
                    while (c.moveToNext()) {
                        if (f >= 0 && System.currentTimeMillis() - c.getLong(f) > STALE_MS) break;
                        String title = t >= 0 ? c.getString(t) : null;
                        if (title == null || title.trim().isEmpty()) continue;
                        String source = s >= 0 ? c.getString(s) : null;
                        fresh.add(source == null || source.trim().isEmpty() ? title.trim() : source.trim() + " · " + title.trim());
                    }
                }
            } catch (RuntimeException ignored) {
                // The provider is not installed or refused: no line.
            }
            line.post(() -> {
                boolean first = headlines.isEmpty();
                headlines = fresh;
                if (fresh.isEmpty()) {
                    line.setVisibility(View.GONE);
                } else if (first) {
                    next = 0;
                    show();
                }
            });
        }, "NewsLine").start();
    }

    private void showNext() {
        if (!headlines.isEmpty()) {
            next = (next + 1) % headlines.size();
            show();
        }
        line.postDelayed(rotate, ROTATE_MS);
    }

    private void show() {
        line.setText(headlines.get(next % headlines.size()));
        line.setVisibility(View.VISIBLE);
    }
}
