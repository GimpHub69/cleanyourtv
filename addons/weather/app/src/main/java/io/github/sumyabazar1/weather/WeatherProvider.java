package io.github.sumyabazar1.weather;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The weather now, for other apps on the TV: {@code content://io.github.sumyabazar1.weather/now} answers one row
 * with {@code text} ("12° · Cloudy"), {@code temp_c}, {@code sky}, {@code place} and {@code updated} (epoch ms).
 * The Clean Your TV launcher shows {@code text} under its clock (its weather_provider key). The row comes from
 * this app's cache; a query older than REFRESH_MS starts one fetch in the background, so the next query is
 * fresh. No row until a city has been chosen in the app. No permission: the city name and the weather are
 * what the app already shows.
 */
public final class WeatherProvider extends ContentProvider {
    private static final String CACHE = "now";
    private static final String[] COLUMNS = {"text", "temp_c", "sky", "place", "updated", "glyph", "color"};
    /** {@code /days}: one row per coming day, tomorrow first, from the same cache. */
    private static final String[] DAY_COLUMNS = {"date", "glyph", "max_c", "min_c", "sky"};
    private static final AtomicBoolean fetching = new AtomicBoolean();

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        SharedPreferences place = getContext().getSharedPreferences("place", 0);
        SharedPreferences cache = getContext().getSharedPreferences(CACHE, 0);
        boolean days = "days".equals(uri.getLastPathSegment());
        MatrixCursor out = new MatrixCursor(days ? DAY_COLUMNS : COLUMNS);
        if (!place.contains("lat")) return out;
        long updated = cache.getLong("updated", 0);
        // Older than the refresh interval, or cached by a build without the glyph or days columns: fetch again.
        if (System.currentTimeMillis() - updated > Forecast.REFRESH_MS || !cache.contains("color") || !cache.contains("days")
                || !place.contains("country")) refresh(place, cache);
        if (days) {
            for (Forecast.Day d : Forecast.decodeDays(cache.getString("days", ""))) {
                out.addRow(new Object[]{d.date.toString(), glyph(d.code, true), d.maxC, d.minC, getContext().getString(Forecast.sky(d.code, true))});
            }
            return out;
        }
        if (updated > 0) {
            String country = place.getString("country", "");
            String where = place.getString("name", "") + (country.isEmpty() ? "" : ", " + country);
            out.addRow(new Object[]{cache.getString("text", ""), cache.getInt("temp_c", 0),
                    cache.getString("sky", ""), where, updated, cache.getString("glyph", ""), cache.getString("color", "")});
        }
        return out;
    }

    /** One fetch at a time, off the binder thread; the result waits for the next query. */
    private void refresh(SharedPreferences place, SharedPreferences cache) {
        if (!fetching.compareAndSet(false, true)) return;
        double lat = Double.longBitsToDouble(place.getLong("lat", 0));
        double lon = Double.longBitsToDouble(place.getLong("lon", 0));
        new Thread(() -> {
            // A city chosen by an older build has no country saved: the nearest search result for its name fills it in.
            if (!place.contains("country")) {
                String country = "";
                try {
                    Forecast.Place best = null;
                    for (Forecast.Place p : Forecast.search(place.getString("name", ""))) {
                        if (best == null || Math.hypot(p.latitude - lat, p.longitude - lon) < Math.hypot(best.latitude - lat, best.longitude - lon)) best = p;
                    }
                    if (best != null && Math.hypot(best.latitude - lat, best.longitude - lon) < 0.5) country = best.country;
                } catch (Exception ignored) {
                    // Offline: try again on the next query.
                }
                if (!country.isEmpty()) place.edit().putString("country", country).apply();
            }
            try {
                Forecast.Report r = Forecast.fetch(lat, lon, Forecast.MIN_DAYS);
                String sky = getContext().getString(Forecast.sky(r.code, r.day));
                cache.edit().putString("text", String.format(Locale.ROOT, "%d° · %s", r.tempC, sky))
                        .putInt("temp_c", r.tempC).putString("sky", sky).putString("glyph", glyph(r.code, r.day)).putString("color", color(r.code, r.day))
                        .putString("days", Forecast.encodeDays(r.days))
                        .putLong("updated", System.currentTimeMillis()).apply();
            } catch (Exception ignored) {
                // Offline or refused: the cached row stays, the next query tries again.
            } finally {
                fetching.set(false);
            }
        }, "weather-now").start();
    }

    /** The condition as one emoji, by the WMO code Open-Meteo gives (after snowIfCold): the "glyph" column. */
    static String glyph(int code, boolean day) {
        if (code == 0) return day ? "\u2600\uFE0F" : "\uD83C\uDF19";            // sun, moon
        if (code <= 2) return day ? "\uD83C\uDF24\uFE0F" : "\u2601\uFE0F";     // sun behind cloud, cloud
        if (code == 3) return "\u2601\uFE0F";
        if (code == 45 || code == 48) return "\uD83C\uDF2B\uFE0F";               // fog
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return "\uD83C\uDF28\uFE0F"; // snow cloud
        if (code >= 95) return "\u26C8\uFE0F";                                    // thunder
        if (code >= 51) return "\uD83C\uDF27\uFE0F";                             // rain cloud
        return "\u2601\uFE0F";
    }

    /** A tint for the condition, #RRGGBB: warm for clear days, cool for rain, pale for snow (the "color" column). */
    static String color(int code, boolean day) {
        if (code == 0) return day ? "#FFD98A" : "#B9C7FF";
        if (code <= 2) return day ? "#FFE3B0" : "#C9D2E8";
        if (code == 3) return "#CFD8DC";
        if (code == 45 || code == 48) return "#B0BEC5";
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return "#E3F2FD";
        if (code >= 95) return "#FFB74D";
        if (code >= 51) return "#8EC3F5";
        return "#CFD8DC";
    }

    @Override public String getType(Uri uri) {
        return "days".equals(uri.getLastPathSegment()) ? "vnd.android.cursor.dir/vnd.weather.day" : "vnd.android.cursor.item/vnd.weather.now";
    }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
