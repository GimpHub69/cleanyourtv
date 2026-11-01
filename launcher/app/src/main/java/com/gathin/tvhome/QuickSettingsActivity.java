package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The panel the remote's Settings button opens when {@code settings_key_panel} is on (HomeKeyService):
 * a few glass rows under the Settings pill, over Home or whatever app is playing. Translucent and in a
 * task of its own, so the app behind keeps running and Back returns to it. Pressing Settings again
 * closes the panel.
 */
public final class QuickSettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_quick_settings);
        findViewById(R.id.scrim).setOnClickListener(v -> finish());
        LinearLayout rows = findViewById(R.id.rows);
        addRow(rows, R.drawable.ic_settings, R.string.tv_settings, 0, SystemSettings.intent(this, Settings.ACTION_SETTINGS));
        addRow(rows, R.drawable.ic_picture, R.string.quick_picture, 0, SystemSettings.intent(this, Settings.ACTION_DISPLAY_SETTINGS));
        addRow(rows, R.drawable.ic_wifi, R.string.quick_network, 0, SystemSettings.intent(this, Settings.ACTION_WIFI_SETTINGS));
        addRow(rows, R.drawable.ic_apps, R.string.quick_apps, R.string.quick_apps_hint,
                SystemSettings.intent(this, Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
        // Through Home, so it rebuilds with the new choices when Launcher settings closes.
        addRow(rows, R.drawable.ic_home_settings, R.string.launcher_settings, 0,
                new Intent(this, MainActivity.class).setAction(MainActivity.ACTION_LAUNCHER_SETTINGS));
        rows.getChildAt(0).requestFocus();
        String provider = weatherProvider();
        if (provider != null) addForecast(rows, provider);
    }

    /** weather_provider's URI, or null when the key is empty or the config cannot load. */
    private String weatherProvider() {
        try {
            String uri = Config.load(this).weatherProvider;
            return uri == null || uri.trim().isEmpty() ? null : uri.trim();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * The coming three days from the weather add-on's {@code /days} path, as one line in a row of its own under the
     * others, never focusable: "Mon ☀️ 12°/4°   Tue 🌧️ 9°/3°   Wed ⛅ 10°/5°". Read off the main thread; an add-on
     * without that path answers nothing and the row stays away.
     */
    private void addForecast(LinearLayout rows, String provider) {
        Uri now = Uri.parse(provider);
        Uri days = new Uri.Builder().scheme(now.getScheme()).authority(now.getAuthority()).path("days").build();
        new Thread(() -> {
            StringBuilder line = new StringBuilder();
            try (Cursor c = getContentResolver().query(days, null, null, null, null)) {
                int n = 0;
                while (c != null && c.moveToNext() && n < 3) {
                    int d = c.getColumnIndex("date"), g = c.getColumnIndex("glyph"), hi = c.getColumnIndex("max_c"), lo = c.getColumnIndex("min_c");
                    if (d < 0 || hi < 0 || lo < 0) break;
                    String weekday = java.time.LocalDate.parse(c.getString(d)).getDayOfWeek()
                            .getDisplayName(java.time.format.TextStyle.SHORT, getResources().getConfiguration().getLocales().get(0));
                    if (line.length() > 0) line.append("   ");
                    line.append(weekday).append(' ');
                    String glyph = g >= 0 ? c.getString(g) : null;
                    if (glyph != null && !glyph.isEmpty()) line.append(glyph).append(' ');
                    line.append(c.getInt(hi)).append("°/").append(c.getInt(lo)).append('°');
                    n++;
                }
            } catch (RuntimeException notThere) {
                // No add-on, or an older one without /days: no row.
            }
            if (line.length() == 0) return;
            rows.post(() -> {
                View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
                row.findViewById(R.id.icon).setVisibility(View.GONE);
                row.findViewById(R.id.dot).setVisibility(View.GONE);
                ((TextView) row.findViewById(R.id.title)).setText(R.string.quick_forecast);
                ((TextView) row.findViewById(R.id.status)).setText(line);
                row.setFocusable(false);
                row.setClickable(false);
                rows.addView(row);
            });
        }, "quick-forecast").start();
    }

    /** Settings pressed again while the panel is up. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        finish();
    }

    /** Anything else taking the screen (Home, an app, a screensaver) closes the panel. */
    @Override
    protected void onPause() {
        super.onPause();
        finish();
        overridePendingTransition(0, 0);
    }

    private void addRow(LinearLayout rows, int icon, int title, int status, Intent target) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        row.findViewById(R.id.dot).setVisibility(View.GONE);
        TextView hint = row.findViewById(R.id.status);
        if (status != 0) hint.setText(status);
        else hint.setVisibility(View.GONE);
        row.setOnClickListener(v -> {
            try {
                startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException | SecurityException e) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
            }
            finish();
        });
        rows.addView(row);
    }
}
