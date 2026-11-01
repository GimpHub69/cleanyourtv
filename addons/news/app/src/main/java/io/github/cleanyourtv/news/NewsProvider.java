package io.github.cleanyourtv.news;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/**
 * The latest headlines for other apps on the TV: {@code content://io.github.cleanyourtv.news/headlines} answers
 * one row per item, newest first, with {@code title}, {@code source} ("BBC News"), {@code summary}, {@code link},
 * {@code published} and {@code fetched} (both epoch ms). The Clean Your TV launcher's news_provider key rotates
 * {@code title} under its clock. The rows come from this app's cache; a query older than 15 minutes starts one
 * fetch in the background, so the next query is fresh. No permission: it is what the app already shows.
 */
public final class NewsProvider extends ContentProvider {
    private static final String[] COLUMNS = {"title", "source", "summary", "link", "published", "fetched"};

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        SharedPreferences cache = Feed.cache(getContext());
        if (Feed.stale(cache)) Feed.refresh(getContext(), null);
        MatrixCursor out = new MatrixCursor(COLUMNS);
        long fetched = cache.getLong("fetched", 0);
        for (Feed.Item i : Feed.decode(cache.getString("items", ""))) {
            out.addRow(new Object[]{i.title, i.source, i.summary, i.link, i.published, fetched});
        }
        return out;
    }

    @Override public String getType(Uri uri) { return "vnd.android.cursor.dir/vnd.news.headline"; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
