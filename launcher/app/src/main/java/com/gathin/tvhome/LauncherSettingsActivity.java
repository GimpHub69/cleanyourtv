package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    static final String EXTRA_REORDER = "com.gathin.tvhome.extra.REORDER_FROM_SETTINGS";
    /** The stored theme applied from the Theme row (ThemeSwitch); the label's memory, not what Config reads. */
    static final String THEME_CHOICE_KEY = "theme_choice";
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private final TextView[] rowChoices = new TextView[4];
    private static final int[] DOCK_OPACITIES = {100, 70, 50, 30};
    private final TextView[] dockChoices = new TextView[DOCK_OPACITIES.length];
    private TextView themeChoice;
    private TextView wallpaperChoice;
    private TextView cycleChoice;
    /** Cycle wallpaper, in the order the row cycles: off, 5 and 15 minutes, an hour, time of day, each return to Home. */
    private static final int[] CYCLES = {0, 5, 15, 60, -1, -2};
    private int cycle;
    private TextView greetingChoice;
    private TextView headerWhiteChoice;
    private TextView wifiPillChoice;
    private TextView wifiColorChoice;
    private TextView weatherChoice;
    private boolean weather;
    /** The preview of Home on the left: the wallpaper, a small header and dock drawn from the choices. */
    private ImageView previewWallpaper;
    private View previewGreeting, previewWeather, previewWifi;
    private TextView previewDate;
    private LinearLayout previewDock;
    private File previewPicture;
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeChoice;
    private TextView openOnStartChoice;
    private int columns;
    private int rowsBelowDock;
    private int dockOpacity;
    private boolean animations;
    private boolean advancedEffects;
    private boolean greeting;
    private boolean headerWhite;
    /** 0 label, 1 icon only, 2 hidden (Config.WIFI_PILL_KEY). */
    private int wifiPill;
    private boolean wifiColor;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        View reorderChoice = findViewById(R.id.reorderChoice);
        reorderChoice.setVisibility(config.launcherSettingsButton ? View.VISIBLE : View.GONE);
        reorderChoice.setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_REORDER, true));
            finish();
        });
        readCurrent(config);
        previewWallpaper = findViewById(R.id.previewWallpaper);
        previewGreeting = findViewById(R.id.previewGreeting);
        previewDate = findViewById(R.id.previewDate);
        previewWeather = findViewById(R.id.previewWeather);
        previewWifi = findViewById(R.id.previewWifi);
        previewDock = findViewById(R.id.previewDock);
        previewWallpaper.post(this::loadPreviewPicture);

        // Appearance: the Theme row shows only where a theme is stored on the TV (ThemeSwitch).
        themeChoice = findViewById(R.id.themeChoice);
        boolean themes = !ThemeSwitch.themes(Config.dir(this)).isEmpty();
        themeChoice.setVisibility(themes ? View.VISIBLE : View.GONE);
        themeChoice.setOnClickListener(v -> showThemePicker());
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> showWallpaperPicker());
        cycleChoice = findViewById(R.id.cycleChoice);
        cycleChoice.setOnClickListener(v -> {
            int at = 0;
            for (int i = 0; i < CYCLES.length; i++) if (CYCLES[i] == cycle) at = i;
            cycle = CYCLES[(at + 1) % CYCLES.length];
            choices.edit().putInt(Config.WALLPAPER_CYCLE_KEY, cycle).apply();
            changed();
            readCurrent(Config.load(this));
            loadPreviewPicture();
            refresh();
        });

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                changed();
                refresh();
            });
        }
        int[] rowIds = {R.id.rows0, R.id.rows1, R.id.rows2, R.id.rows3};
        for (int i = 0; i < rowIds.length; i++) {
            final int value = i;
            rowChoices[i] = findViewById(rowIds[i]);
            rowChoices[i].setOnClickListener(v -> {
                if (rowsBelowDock == value) return;
                rowsBelowDock = value;
                choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, value).apply();
                changed();
                refresh();
            });
        }
        int[] dockIds = {R.id.dock100, R.id.dock70, R.id.dock50, R.id.dock30};
        for (int i = 0; i < dockIds.length; i++) {
            final int value = DOCK_OPACITIES[i];
            dockChoices[i] = findViewById(dockIds[i]);
            dockChoices[i].setOnClickListener(v -> {
                if (dockOpacity == value) return;
                dockOpacity = value;
                choices.edit().putInt(Config.DOCK_OPACITY_KEY, value).apply();
                changed();
                refresh();
            });
        }
        // Keep the numeric choices increasing in the Hebrew reading direction. Reorder the
        // actual views so D-pad focus follows their positions without changing the saved values.
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(columnChoices);
            reverseRow(rowChoices);
            reverseRow(dockChoices);
        }

        // Clock and pills: on-TV choices over the theme's keys, until Follow theme or another theme.
        greetingChoice = findViewById(R.id.greetingChoice);
        greetingChoice.setOnClickListener(v -> {
            greeting = !greeting;
            choices.edit().putBoolean(Config.GREETING_KEY, greeting).apply();
            changed();
            refresh();
        });
        headerWhiteChoice = findViewById(R.id.headerWhiteChoice);
        headerWhiteChoice.setOnClickListener(v -> {
            headerWhite = !headerWhite;
            choices.edit().putBoolean(Config.HEADER_WHITE_KEY, headerWhite).apply();
            changed();
            refresh();
        });
        wifiPillChoice = findViewById(R.id.wifiPillChoice);
        wifiPillChoice.setOnClickListener(v -> {
            wifiPill = (wifiPill + 1) % 3;
            choices.edit().putInt(Config.WIFI_PILL_KEY, wifiPill).apply();
            changed();
            refresh();
        });
        weatherChoice = findViewById(R.id.weatherChoice);
        if (!config.weatherProvider.isEmpty()) {
            weatherChoice.setVisibility(View.VISIBLE);
            weatherChoice.setOnClickListener(v -> {
                weather = !weather;
                choices.edit().putBoolean(Config.WEATHER_KEY, weather).apply();
                changed();
                refresh();
            });
        }
        wifiColorChoice = findViewById(R.id.wifiColorChoice);
        wifiColorChoice.setOnClickListener(v -> {
            wifiColor = !wifiColor;
            choices.edit().putBoolean(Config.WIFI_COLOR_KEY, wifiColor).apply();
            changed();
            refresh();
        });

        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            changed();
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        openOnStartChoice = findViewById(R.id.openOnStartChoice);
        if (config.openOnStartChoice) {
            openOnStartChoice.setVisibility(View.VISIBLE);
            openOnStartChoice.setOnClickListener(v -> showOpenOnStartPicker());
        }
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY)
                    .remove(Config.DOCK_OPACITY_KEY).remove(OpenOnStart.KEY)
                    .remove(Config.GREETING_KEY).remove(Config.HEADER_WHITE_KEY)
                    .remove(Config.WIFI_PILL_KEY).remove(Config.WIFI_COLOR_KEY).remove(Config.WEATHER_KEY)
                    .remove(Config.WALLPAPER_SET_KEY).remove(Config.WALLPAPER_CYCLE_KEY).commit();
            loadPreviewPicture();
            readCurrent(Config.load(this));
            changed();
            refresh();
        });
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v -> {
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
            open(new Intent(Settings.ACTION_HOME_SETTINGS));
        });
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
        refresh();
        if (config.launcherSettingsButton) reorderChoice.requestFocus();
        else if (themes) themeChoice.requestFocus();
        else wallpaperChoice.requestFocus();
    }

    /** The values in force now: the theme's, or the on-TV choices over them, as Config resolves them. */
    private void readCurrent(Config config) {
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        dockOpacity = config.dockOpacity;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;
        greeting = config.showGreeting;
        headerWhite = config.headerWhite;
        wifiPill = !config.showNetworkPill ? 2 : config.iconOnlyButtons ? 1 : 0;
        wifiColor = config.wifiStatusColor;
        weather = config.weatherLine;
        cycle = choices.getInt(Config.WALLPAPER_CYCLE_KEY, 0);
        previewPicture = config.wallpapers.isEmpty() ? null : config.wallpapers.get(0);
    }

    /** The current wallpaper (a collection's first picture) scaled to the preview, off the main thread. */
    private void loadPreviewPicture() {
        final File picture = previewPicture;
        final int width = previewWallpaper.getWidth();
        new Thread(() -> {
            final android.graphics.Bitmap bitmap = picture == null || width <= 0 ? null : ThemePicker.decode(picture, width);
            previewWallpaper.post(() -> previewWallpaper.setImageBitmap(bitmap));
        }, "preview").start();
    }

    /** The preview follows every choice: greeting, date colour, pill form, weather line, dock slots. */
    private void updatePreview() {
        previewGreeting.setVisibility(greeting ? View.VISIBLE : View.GONE);
        previewDate.setTextColor(headerWhite ? 0xFFFFFFFF : 0xA6FFFFFF);
        previewWeather.setVisibility(weather && weatherChoice.getVisibility() == View.VISIBLE ? View.VISIBLE : View.GONE);
        float dp = getResources().getDisplayMetrics().density;
        previewWifi.setVisibility(wifiPill == 2 ? View.GONE : View.VISIBLE);
        previewWifi.getLayoutParams().width = (int) ((wifiPill == 1 ? 11 : 30) * dp);
        previewWifi.requestLayout();
        previewDock.setAlpha(dockOpacity / 100f);
        previewDock.removeAllViews();
        for (int i = 0; i < columns; i++) {
            View tile = new View(this);
            tile.setBackgroundResource(R.drawable.settings_choice_bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
            lp.setMarginStart((int) (3 * dp));
            lp.setMarginEnd((int) (3 * dp));
            previewDock.addView(tile, lp);
        }
    }

    private void reverseRow(TextView[] views) {
        LinearLayout row = (LinearLayout) views[0].getParent();
        row.removeAllViews();
        for (int i = views.length - 1; i >= 0; i--) row.addView(views[i]);
    }

    /** The stored themes with a preview each, Built-in first; applying one does on the TV what tools/theme.sh does over adb. */
    private void showThemePicker() {
        File dir = Config.dir(this);
        ThemePicker.show(this, ThemeSwitch.themes(dir), choices.getString(THEME_CHOICE_KEY, null), theme -> {
            boolean ok = theme == null ? ThemeSwitch.reset(dir) : ThemeSwitch.apply(dir, theme);
            if (!ok) Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
            if (theme == null) choices.edit().remove(THEME_CHOICE_KEY).apply();
            else choices.edit().putString(THEME_CHOICE_KEY, theme.getName()).apply();
            changed();
            // The new theme's keys are in force now, and Config has cleared the on-TV choices for them.
            readCurrent(Config.load(this));
            loadPreviewPicture();
            refresh();
        });
    }

    /** Every picture on the TV with a preview (WallpaperPicker); a pick holds it and sets the Cycle row's group. */
    private void showWallpaperPicker() {
        File dir = Config.dir(this);
        if (dir == null) return;
        WallpaperPicker.show(this, dir, ThemeSwitch.themes(dir), choices.getString(Config.WALLPAPER_CHOICE_KEY, null), (choice, set) -> {
            SharedPreferences.Editor edit = choices.edit();
            if (choice == null) edit.remove(Config.WALLPAPER_CHOICE_KEY).remove(Config.WALLPAPER_SET_KEY);
            else edit.putString(Config.WALLPAPER_CHOICE_KEY, choice);
            if (set != null) edit.putString(Config.WALLPAPER_SET_KEY, set);
            edit.apply();
            changed();
            readCurrent(Config.load(this));
            loadPreviewPicture();
            refresh();
        });
    }

    /** Off, then every app with a TV entry point by name. Saved at once; it is read on the next boot. */
    private void showOpenOnStartPicker() {
        PackageManager pm = getPackageManager();
        List<String[]> apps = new ArrayList<>();
        Intent tv = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(tv, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            boolean seen = false;
            for (String[] a : apps) seen |= a[1].equals(pkg);
            if (!seen) apps.add(new String[]{r.loadLabel(pm).toString(), pkg});
        }
        Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        String[] labels = new String[apps.size() + 1];
        labels[0] = getString(R.string.off);
        String current = choices.getString(OpenOnStart.KEY, "");
        int checked = 0;
        for (int i = 0; i < apps.size(); i++) {
            labels[i + 1] = apps.get(i)[0];
            if (apps.get(i)[1].equals(current)) checked = i + 1;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.open_on_start_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) choices.edit().remove(OpenOnStart.KEY).apply();
                    else {
                        OpenOnStart.skipThisBoot(this);
                        choices.edit().putString(OpenOnStart.KEY, apps.get(which - 1)[1]).apply();
                    }
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** The picked app's name, its package when it is gone, or Off. */
    private String openOnStartLabel() {
        String pkg = choices.getString(OpenOnStart.KEY, "");
        if (pkg.isEmpty()) return getString(R.string.off);
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private static int indexOf(List<File> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    /**
     * A choice was saved. Home rebuilds when it next resumes because the change count moved, whether these
     * settings are left with Back or with the remote's Home button (which cancels the activity result).
     */
    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        changed();
        refresh();
    }

    private String onOff(boolean on) {
        return getString(on ? R.string.on : R.string.off);
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        for (int i = 0; i < rowChoices.length; i++) {
            rowChoices[i].setSelected(rowsBelowDock == i);
        }
        for (int i = 0; i < dockChoices.length; i++) {
            dockChoices[i].setSelected(dockOpacity == DOCK_OPACITIES[i]);
        }
        String theme = choices.getString(THEME_CHOICE_KEY, null);
        themeChoice.setText(getString(R.string.theme_state, theme != null ? ThemeSwitch.title(theme) : getString(R.string.theme_builtin)));
        greetingChoice.setText(getString(R.string.greeting_state, onOff(greeting)));
        headerWhiteChoice.setText(getString(R.string.header_white_state, onOff(headerWhite)));
        wifiPillChoice.setText(getString(R.string.wifi_pill_state, getString(wifiPill == 2 ? R.string.wifi_pill_hidden
                : wifiPill == 1 ? R.string.wifi_pill_icon : R.string.wifi_pill_label)));
        wifiColorChoice.setText(getString(R.string.wifi_color_state, onOff(wifiColor)));
        weatherChoice.setText(getString(R.string.weather_state, onOff(weather)));
        animationsChoice.setText(getString(R.string.animations_state, onOff(animations)));
        advancedChoice.setText(getString(R.string.advanced_effects_state, onOff(advancedEffects)));
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        String wallpaperCurrent = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        wallpaperChoice.setText(getString(R.string.wallpaper_state,
                "none".equals(wallpaperCurrent) ? getString(R.string.wallpaper_none)
                        : wallpaperCurrent != null ? wallpaperCurrent.substring(wallpaperCurrent.lastIndexOf('/') + 1)
                        : getString(R.string.wallpaper_theme_default)));
        cycleChoice.setText(getString(R.string.wallpaper_cycle_state, getString(cycle == 0 ? R.string.off : cycle == 5 ? R.string.cycle_5
                : cycle == 15 ? R.string.cycle_15 : cycle == 60 ? R.string.cycle_hour : cycle == -1 ? R.string.cycle_day : R.string.cycle_home)));
        openOnStartChoice.setText(getString(R.string.open_on_start_state, openOnStartLabel()));
        followThemeChoice.setSelected(!hasOnTvChoices());
        updatePreview();
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations") || choices.contains("advanced_effects")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY) || choices.contains(Config.DOCK_OPACITY_KEY)
                || choices.contains(OpenOnStart.KEY) || choices.contains(Config.GREETING_KEY)
                || choices.contains(Config.HEADER_WHITE_KEY) || choices.contains(Config.WIFI_PILL_KEY)
                || choices.contains(Config.WIFI_COLOR_KEY) || choices.contains(Config.WEATHER_KEY)
                || choices.contains(Config.WALLPAPER_SET_KEY) || choices.contains(Config.WALLPAPER_CYCLE_KEY);
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
