package com.gathin.tvhome;

import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.net.Uri;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * One headline under the clock from another app on this TV (config.xml news_provider): the launcher queries that
 * content provider for rows with {@code title} and {@code source} columns, on each return to Home and each
 * minute, and shows the next one every ROTATE_MS while Home is on screen. With news_card the headline is a story
 * card instead: the row's {@code image} (a content URI the provider serves) as a small picture, the source and
 * headline beside it, the picture crossfading in. No network here: the provider is a local app (the News add-on),
 * which fetches on its own. Rows older than STALE_MS by the provider's {@code fetched} column are not shown: an
 * offline TV keeps no day-old headline under the clock.
 */
final class NewsLine {
    static final long ROTATE_MS = 20_000L, STALE_MS = 6 * 60 * 60 * 1000L;
    /** The card cycles the newest CARD_STORIES only (the ones the provider keeps pictures for), one dot each. */
    static final int CARD_STORIES = 8;
    private static final int FADE_MS = 400;
    private final TextView line;
    private final String uri;
    /** news_card's views, or null for the plain line. */
    private final View card;
    private final ImageView image;
    private final TextView source, headline, dots;
    private ContentResolver resolver;
    private List<String[]> stories = new ArrayList<>();   // {source, title, image uri}
    private int next;
    private final Runnable rotate = this::showNext;
    /** The picture's fade-in (imageAlpha only, so the dark frame behind it stays). */
    private android.animation.ObjectAnimator fade;

    NewsLine(TextView line, String uri, View card) {
        this.line = line;
        this.uri = uri;
        this.card = card;
        image = card == null ? null : card.findViewById(R.id.newsImage);
        source = card == null ? null : card.findViewById(R.id.newsSource);
        headline = card == null ? null : card.findViewById(R.id.newsHeadline);
        dots = card == null ? null : card.findViewById(R.id.newsDots);
        if (image != null) {
            image.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 8 * v.getResources().getDisplayMetrics().density);
                }
            });
            image.setClipToOutline(true);
        }
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
        this.resolver = resolver;
        new Thread(() -> {
            List<String[]> fresh = new ArrayList<>();
            try (Cursor c = resolver.query(Uri.parse(uri), new String[]{"title", "source", "image", "fetched"}, null, null, null)) {
                if (c != null) {
                    int t = c.getColumnIndex("title"), s = c.getColumnIndex("source"), i = c.getColumnIndex("image"), f = c.getColumnIndex("fetched");
                    while (c.moveToNext()) {
                        if (f >= 0 && System.currentTimeMillis() - c.getLong(f) > STALE_MS) break;
                        String title = t >= 0 ? c.getString(t) : null;
                        if (title == null || title.trim().isEmpty()) continue;
                        String src = s >= 0 && c.getString(s) != null ? c.getString(s).trim() : "";
                        String img = i >= 0 && c.getString(i) != null ? c.getString(i).trim() : "";
                        if (card != null && fresh.size() >= CARD_STORIES) break;
                        fresh.add(new String[]{src, title.trim(), img});
                    }
                }
            } catch (RuntimeException ignored) {
                // The provider is not installed or refused: no line.
            }
            line.post(() -> {
                boolean first = stories.isEmpty();
                // The story on screen keeps its place when newer ones push it down the list; gone, the rotation
                // starts the new list from its top.
                String shown = first ? null : stories.get(next % stories.size())[1];
                int at = -1;
                for (int k = 0; shown != null && k < fresh.size() && at < 0; k++) if (fresh.get(k)[1].equals(shown)) at = k;
                stories = fresh;
                if (fresh.isEmpty()) {
                    line.setVisibility(View.GONE);
                    if (card != null) card.setVisibility(View.GONE);
                } else if (first) {
                    next = 0;
                    show();
                } else {
                    next = at >= 0 ? at : fresh.size() - 1;
                }
            });
        }, "NewsLine").start();
    }

    private void showNext() {
        if (!stories.isEmpty()) {
            next = (next + 1) % stories.size();
            show();
        }
        line.postDelayed(rotate, ROTATE_MS);
    }

    private void show() {
        String[] story = stories.get(next % stories.size());
        if (card == null) {
            line.setText(story[0].isEmpty() ? story[1] : story[0] + " · " + story[1]);
            line.setVisibility(View.VISIBLE);
            return;
        }
        source.setText(story[0]);
        headline.setText(story[1]);
        StringBuilder d = new StringBuilder();
        for (int k = 0; k < stories.size(); k++) d.append(k == next % stories.size() ? '\u25CF' : '\u25CB');
        dots.setText(d);
        card.setVisibility(View.VISIBLE);
        loadPicture(story[2]);
    }

    /** The story's picture read from the provider off the main thread, then faded in; none, and the frame stays dark. */
    private void loadPicture(String img) {
        final int shown = next;
        if (img.isEmpty()) {
            if (fade != null) fade.cancel();
            image.setImageBitmap(null);
            return;
        }
        new Thread(() -> {
            Bitmap b = null;
            try (InputStream in = resolver.openInputStream(Uri.parse(img))) {
                if (in != null) {
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inPreferredConfig = Bitmap.Config.RGB_565;
                    b = BitmapFactory.decodeStream(in, null, o);
                }
            } catch (Exception ignored) {
                // Unreadable picture: the frame stays dark.
            }
            final Bitmap got = b;
            image.post(() -> {
                if (shown != next) {
                    if (got != null) got.recycle();
                    return;
                }
                if (fade != null) fade.cancel();
                image.setImageBitmap(got);
                image.setImageAlpha(0);
                fade = android.animation.ObjectAnimator.ofInt(image, "imageAlpha", 0, 255).setDuration(FADE_MS);
                fade.start();
            });
        }, "NewsPicture").start();
    }
}
