package io.github.cleanyourtv.news;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * The latest headlines for other apps on the TV: {@code content://io.github.cleanyourtv.news/headlines} answers
 * one row per item, newest first, with {@code title}, {@code source} ("BBC News"), {@code summary}, {@code link},
 * {@code image} (a {@code .../image/<id>} URI of this provider for the story's cached picture, empty when there is
 * none yet), {@code published} and {@code fetched} (both epoch ms). The Clean Your TV launcher's news_provider key rotates
 * {@code title} under its clock. The rows come from this app's cache; a query older than 15 minutes starts one
 * fetch in the background, so the next query is fresh. No permission: it is what the app already shows.
 */
public final class NewsProvider extends ContentProvider {
    private static final String[] COLUMNS = {"title", "source", "summary", "link", "image", "published", "fetched"};
    private static final Uri IMAGES = Uri.parse("content://io.github.cleanyourtv.news/image");

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
            String image = "";
            if (!i.image.isEmpty()) {
                File f = Feed.imageFile(getContext(), i.image);
                if (f.isFile()) image = Uri.withAppendedPath(IMAGES, Feed.hash(i.image)).toString();
            }
            out.addRow(new Object[]{i.title, i.source, i.summary, i.link, image, i.published, fetched});
        }
        return out;
    }

    /** {@code /image/<sha1>}: the cached JPEG, read-only; the id is checked before it touches a path. */
    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String id = uri.getLastPathSegment();
        if (!"image".equals(uri.getPathSegments().isEmpty() ? "" : uri.getPathSegments().get(0))
                || id == null || !id.matches("[0-9a-f]{40}") || !"r".equals(mode)) {
            throw new FileNotFoundException(uri.toString());
        }
        return ParcelFileDescriptor.open(new File(Feed.imageDir(getContext()), id + ".jpg"), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public String getType(Uri uri) {
        return "image".equals(uri.getPathSegments().isEmpty() ? "" : uri.getPathSegments().get(0)) ? "image/jpeg" : "vnd.android.cursor.dir/vnd.news.headline";
    }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
