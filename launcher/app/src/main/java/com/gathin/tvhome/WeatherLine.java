package com.gathin.tvhome;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
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
            String text = null, place = null;
            try (Cursor c = resolver.query(Uri.parse(uri), new String[]{"text", "place"}, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int t = c.getColumnIndex("text"), p = c.getColumnIndex("place");
                    text = t >= 0 ? c.getString(t) : null;
                    place = p >= 0 ? c.getString(p) : null;
                }
            } catch (RuntimeException ignored) {
                // The provider is not installed or refused: no line.
            }
            final String weather = text == null || text.trim().isEmpty() ? null : text.trim();
            final String where = place == null || place.trim().isEmpty() ? null : place.trim();
            line.post(() -> {
                if (weather == null) {
                    line.setVisibility(View.GONE);
                    return;
                }
                // The weather, then the place on a smaller, dimmer second line.
                SpannableString shown = new SpannableString(where == null ? weather : weather + "\n" + where);
                if (where != null) {
                    shown.setSpan(new RelativeSizeSpan(0.8f), weather.length() + 1, shown.length(), 0);
                    shown.setSpan(new ForegroundColorSpan(0x99FFFFFF), weather.length() + 1, shown.length(), 0);
                }
                line.setText(shown);
                line.setVisibility(View.VISIBLE);
            });
        }, "WeatherLine").start();
    }
}
