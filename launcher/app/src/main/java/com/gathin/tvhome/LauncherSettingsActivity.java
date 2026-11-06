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
    /** Dock band opacity, 0 to 100 in steps of 10: the row steps it, the bar under it shows the level. */
    private TextView dockOpacityChoice;
    private android.widget.ProgressBar dockOpacityBar;
    private TextView themeChoice;
    private TextView wallpaperChoice;
    private TextView cycleChoice;
    private TextView motionChoice;
    private boolean motion;
    /** The wallpaper picker's list, for Left and Right on the Wallpaper row. */
    private List<WallpaperPicker.Entry> pictures;
    private List<File> themes;
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
    private TextView clockWeightChoice, clockHoursChoice, clockSecondsChoice, tileShapeChoice;
    /** -1 as the layout, 0 thin, 1 light, 2 bold (Config.CLOCK_WEIGHT_KEY); 0 auto, 12 or 24; seconds shown. */
    private int clockWeight, clockHours;
    private boolean clockSeconds;
    private static final String[] SHAPES = {"rounded", "square", "pill"};
    private String tileShape;
    private android.widget.TextClock previewClock;
    /** News: seconds per story, stories in the card, place (0 under the clock, 1 under the pills, 2 off). */
    private TextView newsOnChoice, newsSpeedChoice, newsStoriesChoice, newsPositionChoice, idleClockChoice;
    private static final int[] NEWS_SPEEDS = {5, 8, 10, 15, 20, 30, 60};
    private static final int[] NEWS_COUNTS = {3, 4, 5, 6, 8, 10, 12};
    /** Minutes before the idle clock on Home: off, then 1 to 30. */
    private static final int[] IDLE_MINUTES = {0, 1, 2, 5, 10, 30};
    private int newsSeconds, newsStories, newsPlace, idleMinutes;
    private boolean newsOn;

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
        previewClock = findViewById(R.id.previewClock);
        previewWallpaper.post(this::loadPreviewPicture);

        // Appearance: the Theme row shows only where a theme is stored on the TV (ThemeSwitch).
        themeChoice = findViewById(R.id.themeChoice);
        themes = ThemeSwitch.themes(Config.dir(this));
        boolean hasThemes = !themes.isEmpty();
        themeChoice.setVisibility(hasThemes ? View.VISIBLE : View.GONE);
        themeChoice.setOnClickListener(v -> showThemePicker());
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> showWallpaperPicker());
        stepKeys(themeChoice, () -> stepTheme(-1), () -> stepTheme(1));
        stepKeys(wallpaperChoice, () -> stepWallpaper(-1), () -> stepWallpaper(1));
        motionChoice = findViewById(R.id.motionChoice);
        Runnable toggleMotion = () -> {
            motion = !motion;
            choices.edit().putBoolean(Config.WALLPAPER_MOTION_KEY, motion).apply();
            changed();
            refresh();
        };
        motionChoice.setOnClickListener(v -> toggleMotion.run());
        stepKeys(motionChoice, toggleMotion, toggleMotion);
        cycleChoice = findViewById(R.id.cycleChoice);
        cycleChoice.setOnClickListener(v -> stepCycle(1));
        stepKeys(cycleChoice, () -> stepCycle(-1), () -> stepCycle(1));

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
        dockOpacityChoice = findViewById(R.id.dockOpacityChoice);
        dockOpacityBar = findViewById(R.id.dockOpacityBar);
        dockOpacityChoice.setOnClickListener(v -> stepOpacity(10));
        stepKeys(dockOpacityChoice, () -> stepOpacity(-10), () -> stepOpacity(10));
        // Keep the numeric choices increasing in the Hebrew reading direction. Reorder the
        // actual views so D-pad focus follows their positions without changing the saved values.
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(columnChoices);
            reverseRow(rowChoices);
        }

        // Clock and pills: on-TV choices over the theme's keys, until Follow theme or another theme.
        greetingChoice = findViewById(R.id.greetingChoice);
        Runnable toggleGreeting = () -> {
            greeting = !greeting;
            choices.edit().putBoolean(Config.GREETING_KEY, greeting).apply();
            changed();
            refresh();
        };
        greetingChoice.setOnClickListener(v -> toggleGreeting.run());
        stepKeys(greetingChoice, toggleGreeting, toggleGreeting);
        clockWeightChoice = findViewById(R.id.clockWeightChoice);
        clockWeightChoice.setOnClickListener(v -> stepClockWeight(1));
        stepKeys(clockWeightChoice, () -> stepClockWeight(-1), () -> stepClockWeight(1));
        clockHoursChoice = findViewById(R.id.clockHoursChoice);
        clockHoursChoice.setOnClickListener(v -> stepClockHours(1));
        stepKeys(clockHoursChoice, () -> stepClockHours(-1), () -> stepClockHours(1));
        clockSecondsChoice = findViewById(R.id.clockSecondsChoice);
        Runnable toggleSeconds = () -> {
            clockSeconds = !clockSeconds;
            choices.edit().putBoolean(Config.CLOCK_SECONDS_KEY, clockSeconds).apply();
            changed();
            refresh();
        };
        clockSecondsChoice.setOnClickListener(v -> toggleSeconds.run());
        stepKeys(clockSecondsChoice, toggleSeconds, toggleSeconds);
        tileShapeChoice = findViewById(R.id.tileShapeChoice);
        tileShapeChoice.setOnClickListener(v -> stepShape(1));
        stepKeys(tileShapeChoice, () -> stepShape(-1), () -> stepShape(1));
        headerWhiteChoice = findViewById(R.id.headerWhiteChoice);
        Runnable toggleWhite = () -> {
            headerWhite = !headerWhite;
            choices.edit().putBoolean(Config.HEADER_WHITE_KEY, headerWhite).apply();
            changed();
            refresh();
        };
        headerWhiteChoice.setOnClickListener(v -> toggleWhite.run());
        stepKeys(headerWhiteChoice, toggleWhite, toggleWhite);
        wifiPillChoice = findViewById(R.id.wifiPillChoice);
        wifiPillChoice.setOnClickListener(v -> stepPill(1));
        stepKeys(wifiPillChoice, () -> stepPill(-1), () -> stepPill(1));
        weatherChoice = findViewById(R.id.weatherChoice);
        if (!config.weatherProvider.isEmpty()) {
            weatherChoice.setVisibility(View.VISIBLE);
            Runnable toggleWeather = () -> {
                weather = !weather;
                choices.edit().putBoolean(Config.WEATHER_KEY, weather).apply();
                changed();
                refresh();
            };
            weatherChoice.setOnClickListener(v -> toggleWeather.run());
            stepKeys(weatherChoice, toggleWeather, toggleWeather);
        }
        wifiColorChoice = findViewById(R.id.wifiColorChoice);
        Runnable toggleColor = () -> {
            wifiColor = !wifiColor;
            choices.edit().putBoolean(Config.WIFI_COLOR_KEY, wifiColor).apply();
            changed();
            refresh();
        };
        wifiColorChoice.setOnClickListener(v -> toggleColor.run());
        stepKeys(wifiColorChoice, toggleColor, toggleColor);

        // News: only with a provider; Left and Right step each row.
        newsOnChoice = findViewById(R.id.newsOnChoice);
        newsSpeedChoice = findViewById(R.id.newsSpeedChoice);
        newsStoriesChoice = findViewById(R.id.newsStoriesChoice);
        newsPositionChoice = findViewById(R.id.newsPositionChoice);
        idleClockChoice = findViewById(R.id.idleClockChoice);
        idleClockChoice.setOnClickListener(v -> stepIdle(1));
        stepKeys(idleClockChoice, () -> stepIdle(-1), () -> stepIdle(1));
        if (!config.newsProvider.isEmpty()) {
            findViewById(R.id.newsSection).setVisibility(View.VISIBLE);
            findViewById(R.id.newsGroup).setVisibility(View.VISIBLE);
            Runnable toggleNews = () -> {
                newsOn = !newsOn;
                choices.edit().putBoolean(Config.NEWS_ON_KEY, newsOn).apply();
                changed();
                refresh();
            };
            newsOnChoice.setOnClickListener(v -> toggleNews.run());
            stepKeys(newsOnChoice, toggleNews, toggleNews);
            newsSpeedChoice.setOnClickListener(v -> stepNews(0, 1));
            stepKeys(newsSpeedChoice, () -> stepNews(0, -1), () -> stepNews(0, 1));
            newsStoriesChoice.setOnClickListener(v -> stepNews(1, 1));
            stepKeys(newsStoriesChoice, () -> stepNews(1, -1), () -> stepNews(1, 1));
            newsPositionChoice.setOnClickListener(v -> stepNews(2, 1));
            stepKeys(newsPositionChoice, () -> stepNews(2, -1), () -> stepNews(2, 1));
        }

        animationsChoice = findViewById(R.id.animationsChoice);
        Runnable toggleAnimations = () -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            changed();
            refresh();
        };
        animationsChoice.setOnClickListener(v -> toggleAnimations.run());
        stepKeys(animationsChoice, toggleAnimations, toggleAnimations);
        advancedChoice = findViewById(R.id.advancedChoice);
        stepKeys(advancedChoice, () -> advancedChoice.performClick(), () -> advancedChoice.performClick());
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
                    .remove(Config.WALLPAPER_SET_KEY).remove(Config.WALLPAPER_CYCLE_KEY).remove(Config.WALLPAPER_MOTION_KEY)
                    .remove(Config.NEWS_SECONDS_KEY).remove(Config.NEWS_STORIES_KEY).remove(Config.NEWS_POSITION_KEY)
                    .remove(Config.NEWS_ON_KEY).remove(Config.IDLE_CLOCK_KEY).commit();
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
        else if (hasThemes) themeChoice.requestFocus();
        else wallpaperChoice.requestFocus();
    }

    /** Left and Right on a row change its value without opening anything (the numbered rows move between their numbers already). */
    private static void stepKeys(View row, Runnable left, Runnable right) {
        row.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) left.run();
            else if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) right.run();
            else return false;
            return true;
        });
    }

    private void stepCycle(int by) {
        int at = 0;
        for (int i = 0; i < CYCLES.length; i++) if (CYCLES[i] == cycle) at = i;
        cycle = CYCLES[((at + by) % CYCLES.length + CYCLES.length) % CYCLES.length];
        choices.edit().putInt(Config.WALLPAPER_CYCLE_KEY, cycle).apply();
        changed();
        readCurrent(Config.load(this));
        loadPreviewPicture();
        refresh();
    }

    private void stepPill(int by) {
        wifiPill = ((wifiPill + by) % 3 + 3) % 3;
        choices.edit().putInt(Config.WIFI_PILL_KEY, wifiPill).apply();
        changed();
        refresh();
    }

    /** 0 to 100 in steps of 10, wrapping; a theme's odd value rounds to the nearest step first. */
    private void stepOpacity(int by) {
        int at = Math.round(dockOpacity / 10f) * 10 + by;
        dockOpacity = ((at % 110) + 110) % 110;
        choices.edit().putInt(Config.DOCK_OPACITY_KEY, dockOpacity).apply();
        changed();
        refresh();
    }

    /** Row 0 the speed, 1 the count, 2 the place, each wrapping through its values. */
    private void stepNews(int row, int by) {
        if (row == 0) {
            int at = 0;
            for (int i = 0; i < NEWS_SPEEDS.length; i++) if (NEWS_SPEEDS[i] <= newsSeconds) at = i;
            newsSeconds = NEWS_SPEEDS[((at + by) % NEWS_SPEEDS.length + NEWS_SPEEDS.length) % NEWS_SPEEDS.length];
            choices.edit().putInt(Config.NEWS_SECONDS_KEY, newsSeconds).apply();
        } else if (row == 1) {
            int at = 0;
            for (int i = 0; i < NEWS_COUNTS.length; i++) if (NEWS_COUNTS[i] <= newsStories) at = i;
            newsStories = NEWS_COUNTS[((at + by) % NEWS_COUNTS.length + NEWS_COUNTS.length) % NEWS_COUNTS.length];
            choices.edit().putInt(Config.NEWS_STORIES_KEY, newsStories).apply();
        } else {
            newsPlace = ((newsPlace + by) % 2 + 2) % 2;
            choices.edit().putInt(Config.NEWS_POSITION_KEY, newsPlace).apply();
        }
        changed();
        refresh();
    }

    /** Off, 1, 2, 5, 10, 30 minutes, wrapping; a theme's other value steps from the nearest below it. */
    private void stepIdle(int by) {
        int at = 0;
        for (int i = 0; i < IDLE_MINUTES.length; i++) if (IDLE_MINUTES[i] <= idleMinutes) at = i;
        idleMinutes = IDLE_MINUTES[((at + by) % IDLE_MINUTES.length + IDLE_MINUTES.length) % IDLE_MINUTES.length];
        choices.edit().putInt(Config.IDLE_CLOCK_KEY, idleMinutes).apply();
        changed();
        refresh();
    }

    private void stepClockWeight(int by) {
        // Light is both the layout's own (-1) and choice 1, so the row cycles thin, light, bold.
        int at = clockWeight < 0 ? 1 : clockWeight;
        clockWeight = ((at + by) % 3 + 3) % 3;
        choices.edit().putInt(Config.CLOCK_WEIGHT_KEY, clockWeight).apply();
        changed();
        refresh();
    }

    private void stepClockHours(int by) {
        int at = clockHours == 12 ? 1 : clockHours == 24 ? 2 : 0;
        at = ((at + by) % 3 + 3) % 3;
        clockHours = at == 1 ? 12 : at == 2 ? 24 : 0;
        choices.edit().putInt(Config.CLOCK_HOURS_KEY, clockHours).apply();
        changed();
        refresh();
    }

    private void stepShape(int by) {
        int at = 0;
        for (int i = 0; i < SHAPES.length; i++) if (SHAPES[i].equals(tileShape)) at = i;
        tileShape = SHAPES[((at + by) % SHAPES.length + SHAPES.length) % SHAPES.length];
        choices.edit().putString(Config.TILE_SHAPE_KEY, tileShape).apply();
        changed();
        refresh();
    }

    /** Built-in, then the stored themes, applied as the picker would. */
    private void stepTheme(int by) {
        if (themes.isEmpty()) return;
        String current = choices.getString(THEME_CHOICE_KEY, null);
        int at = 0;
        for (int i = 0; i < themes.size(); i++) if (themes.get(i).getName().equals(current)) at = i + 1;
        int n = themes.size() + 1;
        at = ((at + by) % n + n) % n;
        applyTheme(at == 0 ? null : themes.get(at - 1));
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code != 7) return;
        pictures = null;
        showWallpaperPicker();
    }

    /** Theme default, None, then every picture, as the picker lists them; the row that asks for pictures is skipped. */
    private void stepWallpaper(int by) {
        if (pictures == null) pictures = WallpaperPicker.entries(this, Config.dir(this), themes);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int at = 0;
        for (int i = 0; i < pictures.size(); i++) {
            String c = pictures.get(i).choice;
            if (c == null ? current == null : c.equals(current)) at = i;
        }
        int n = pictures.size();
        WallpaperPicker.Entry e = pictures.get(((at + by) % n + n) % n);
        if (WallpaperPicker.DEVICE_ALLOW.equals(e.choice)) e = pictures.get(((at + 2 * by) % n + n) % n);
        applyWallpaper(e.choice, e.set);
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
        clockWeight = config.clockWeight;
        clockHours = config.clockHours;
        clockSeconds = config.clockSeconds;
        tileShape = config.tileShape;
        newsSeconds = config.newsSeconds;
        newsStories = config.newsStories;
        newsPlace = "right".equals(config.newsPosition) ? 1 : 0;
        newsOn = config.newsShown;
        idleMinutes = config.homeIdleClockMinutes;
        cycle = choices.getInt(Config.WALLPAPER_CYCLE_KEY, 0);
        motion = choices.getBoolean(Config.WALLPAPER_MOTION_KEY, config.wallpaperMotion);
        previewPicture = config.wallpapers.isEmpty() ? null : config.wallpapers.get(0);
    }

    /** The current wallpaper (a collection's first picture) scaled to the preview, off the main thread. */
    private void loadPreviewPicture() {
        final File picture = previewPicture;
        final int width = previewWallpaper.getWidth();
        new Thread(() -> {
            final android.graphics.Bitmap bitmap = picture == null || width <= 0 ? null : ThemePicker.decode(picture, width);
            previewWallpaper.post(() -> ThemePicker.fadeIn(previewWallpaper, bitmap));
        }, "preview").start();
    }

    /** The preview follows every choice: greeting, date colour, pill form, weather line, dock slots. */
    private void updatePreview() {
        ClockStyle.apply(previewClock, clockWeight, clockHours, clockSeconds);
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
            android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
            shape.setColor(0xFF242B34);
            shape.setCornerRadius("pill".equals(tileShape) ? 99 * dp : "square".equals(tileShape) ? dp : 5 * dp);
            tile.setBackground(shape);
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
        ThemePicker.show(this, themes, choices.getString(THEME_CHOICE_KEY, null), this::applyTheme);
    }

    private void applyTheme(File theme) {
        File dir = Config.dir(this);
        boolean ok = theme == null ? ThemeSwitch.reset(dir) : ThemeSwitch.apply(dir, theme);
        if (!ok) Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        if (theme == null) choices.edit().remove(THEME_CHOICE_KEY).apply();
        else choices.edit().putString(THEME_CHOICE_KEY, theme.getName()).apply();
        changed();
        // The new theme's keys are in force now, and Config has cleared the on-TV choices for them.
        readCurrent(Config.load(this));
        pictures = null;
        loadPreviewPicture();
        refresh();
    }

    /** Every picture on the TV with a preview (WallpaperPicker); a pick holds it and sets the Cycle row's group. */
    private void showWallpaperPicker() {
        File dir = Config.dir(this);
        if (dir == null) return;
        if (pictures == null) pictures = WallpaperPicker.entries(this, dir, themes);
        WallpaperPicker.show(this, pictures, choices.getString(Config.WALLPAPER_CHOICE_KEY, null), this::applyWallpaper);
    }

    private void applyWallpaper(String choice, String set) {
        if (WallpaperPicker.DEVICE_ALLOW.equals(choice)) {
            // The "Pictures on this TV" row: ask for the pictures, or list them again; the picker reopens after.
            requestPermissions(new String[]{WallpaperPicker.DEVICE_PERMISSION}, 7);
            return;
        }
        SharedPreferences.Editor edit = choices.edit();
        if (choice == null) edit.remove(Config.WALLPAPER_CHOICE_KEY).remove(Config.WALLPAPER_SET_KEY);
        else edit.putString(Config.WALLPAPER_CHOICE_KEY, choice);
        if (set != null) edit.putString(Config.WALLPAPER_SET_KEY, set);
        edit.apply();
        changed();
        readCurrent(Config.load(this));
        loadPreviewPicture();
        refresh();
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
        dockOpacityChoice.setText(getString(R.string.dock_opacity_state, dockOpacity + "%"));
        dockOpacityBar.setProgress(dockOpacity);
        String theme = choices.getString(THEME_CHOICE_KEY, null);
        themeChoice.setText(getString(R.string.theme_state, theme != null ? ThemeSwitch.title(theme) : getString(R.string.theme_builtin)));
        greetingChoice.setText(getString(R.string.greeting_state, onOff(greeting)));
        clockWeightChoice.setText(getString(R.string.clock_weight_state, getString(clockWeight == 0 ? R.string.clock_thin
                : clockWeight == 2 ? R.string.clock_bold : R.string.clock_light)));
        clockHoursChoice.setText(getString(R.string.clock_hours_state, getString(clockHours == 12 ? R.string.clock_hours_12
                : clockHours == 24 ? R.string.clock_hours_24 : R.string.clock_hours_auto)));
        clockSecondsChoice.setText(getString(R.string.clock_seconds_state, onOff(clockSeconds)));
        tileShapeChoice.setText(getString(R.string.tile_shape_state, getString("pill".equals(tileShape) ? R.string.shape_pill
                : "square".equals(tileShape) ? R.string.shape_square : R.string.shape_rounded)));
        headerWhiteChoice.setText(getString(R.string.header_white_state, onOff(headerWhite)));
        newsOnChoice.setText(getString(R.string.news_on_state, onOff(newsOn)));
        newsSpeedChoice.setText(getString(R.string.news_speed_state, getString(R.string.news_seconds_value, newsSeconds)));
        idleClockChoice.setText(getString(R.string.idle_clock_state, idleMinutes == 0 ? getString(R.string.off)
                : getString(R.string.minutes_value, idleMinutes)));
        newsStoriesChoice.setText(getString(R.string.news_stories_state, String.valueOf(newsStories)));
        newsPositionChoice.setText(getString(R.string.news_position_state, getString(newsPlace == 1 ? R.string.news_place_right
                : R.string.news_place_left)));
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
        motionChoice.setText(getString(R.string.wallpaper_motion_state, onOff(motion)));
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
                || choices.contains(Config.WALLPAPER_SET_KEY) || choices.contains(Config.WALLPAPER_CYCLE_KEY)
                || choices.contains(Config.WALLPAPER_MOTION_KEY) || choices.contains(Config.CLOCK_WEIGHT_KEY)
                || choices.contains(Config.CLOCK_HOURS_KEY) || choices.contains(Config.CLOCK_SECONDS_KEY)
                || choices.contains(Config.TILE_SHAPE_KEY) || choices.contains(Config.NEWS_SECONDS_KEY)
                || choices.contains(Config.NEWS_STORIES_KEY) || choices.contains(Config.NEWS_POSITION_KEY)
                || choices.contains(Config.NEWS_ON_KEY) || choices.contains(Config.IDLE_CLOCK_KEY);
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
