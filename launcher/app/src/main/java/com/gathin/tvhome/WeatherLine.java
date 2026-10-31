package com.gathin.tvhome;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.view.View;
import android.widget.TextView;

/**
 * One line under the clock from another app on this TV (config.xml weather_provider): the launcher queries that
 * content provider for a row with a {@code text} column, on each return to Home and each minute, and shows it.
 * No network here: the provider is a local app (the Weather add-on), which fetches on its own.
 * Each read runs off the main thread and takes whatever the provider has; nothing, and the line stays hidden.
 */
final class WeatherLine {
    private WeatherLine() {}

    static void refresh(ContentResolver resolver, String uri, TextView line) {
        new Thread(() -> {
            String text = null;
            try (Cursor c = resolver.query(Uri.parse(uri), new String[]{"text"}, null, null, null)) {
                if (c != null && c.moveToFirst()) text = c.getString(0);
            } catch (RuntimeException ignored) {
                // The provider is not installed or refused: no line.
            }
            final String shown = text == null || text.trim().isEmpty() ? null : text.trim();
            line.post(() -> {
                line.setText(shown);
                line.setVisibility(shown == null ? View.GONE : View.VISIBLE);
            });
        }, "WeatherLine").start();
    }
}
