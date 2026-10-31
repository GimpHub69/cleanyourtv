package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.List;

/**
 * The Wallpaper row's picker, on the Theme picker's panel: Theme default and None first, then every picture the TV
 * holds, grouped: the current theme's, each stored theme's (themes/<name>/) and the Custom folder (custom/, written
 * through ThemeProvider). The focused picture shows on the right. OK holds that picture, and remembers its group as
 * the set the Cycle row rotates through ({@link Config#WALLPAPER_SET_KEY}).
 */
final class WallpaperPicker {
    static final String CUSTOM = "custom";

    interface OnPick {
        /** The picture's path relative to the theme folder (or "none" / null for Theme default) and its group's folder. */
        void pick(String choice, String set);
    }

    private WallpaperPicker() {}

    static void show(Activity activity, File dir, List<File> themes, String current, OnPick onPick) {
        Dialog dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_theme_picker);
        WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
        lp.dimAmount = 0.8f;
        dialog.getWindow().setAttributes(lp);
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        ((TextView) dialog.findViewById(R.id.themeTitle)).setText(R.string.wallpaper_label);
        ImageView preview = dialog.findViewById(R.id.themePreview);
        TextView name = dialog.findViewById(R.id.themeName);
        TextView hint = dialog.findViewById(R.id.themeHint);
        LinearLayout rows = dialog.findViewById(R.id.themeRows);
        float dp = activity.getResources().getDisplayMetrics().density;
        preview.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 18 * dp);
            }
        });
        preview.setClipToOutline(true);
        hint.setText(R.string.theme_pick_hint);
        View[] first = {null};
        Row.Sink sink = (label, file, choice, set) -> {
            TextView row = new TextView(activity);
            row.setText(label);
            row.setTextColor(0xFFFFFFFF);
            row.setTextSize(17);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding((int) (16 * dp), 0, (int) (16 * dp), 0);
            row.setBackgroundResource(R.drawable.settings_choice_bg);
            row.setFocusable(true);
            row.setSelected(choice == null ? current == null : choice.equals(current));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int) (40 * dp));
            rlp.bottomMargin = (int) (6 * dp);
            rows.addView(row, rlp);
            row.setOnFocusChangeListener((v, focused) -> {
                if (!focused) return;
                name.setText(label);
                Bitmap bitmap = file != null && file.isFile() ? ThemePicker.decode(file, preview.getWidth()) : null;
                preview.setImageBitmap(bitmap);
            });
            row.setOnClickListener(v -> {
                dialog.dismiss();
                onPick.pick(choice, set);
            });
            if (row.isSelected() || first[0] == null) first[0] = row;
        };
        Row.Header header = text -> {
            TextView h = new TextView(activity);
            h.setText(text);
            h.setTextColor(0xFF9AA4AE);
            h.setTextSize(12);
            h.setAllCaps(true);
            h.setLetterSpacing(0.08f);
            h.setPadding((int) (16 * dp), (int) (10 * dp), 0, (int) (6 * dp));
            rows.addView(h);
        };
        sink.add(activity.getString(R.string.wallpaper_theme_default), null, null, null);
        sink.add(activity.getString(R.string.wallpaper_none), null, WallpaperRotation.CHOICE_NONE, null);
        // The current theme's pictures keep their bare names: the choice Config always understood.
        List<File> collection = WallpaperRotation.images(new File(dir, ThemeFiles.WALLPAPERS));
        File single = new File(dir, Config.WALLPAPER_NAME);
        if (!collection.isEmpty() || single.isFile()) header.add(activity.getString(R.string.wallpaper_group_current));
        if (single.isFile()) sink.add(single.getName(), single, single.getName(), "");
        for (File f : collection) sink.add(f.getName(), f, f.getName(), ThemeFiles.WALLPAPERS);
        for (File theme : themes) {
            String base = ThemeFiles.THEMES + "/" + theme.getName();
            File themeSingle = new File(theme, Config.WALLPAPER_NAME);
            List<File> pictures = WallpaperRotation.images(new File(theme, ThemeFiles.WALLPAPERS));
            if (!themeSingle.isFile() && pictures.isEmpty()) continue;
            header.add(ThemeSwitch.title(theme.getName()));
            if (themeSingle.isFile()) sink.add(themeSingle.getName(), themeSingle, base + "/" + themeSingle.getName(), base);
            for (File f : pictures) sink.add(f.getName(), f, base + "/" + ThemeFiles.WALLPAPERS + "/" + f.getName(), base + "/" + ThemeFiles.WALLPAPERS);
        }
        List<File> custom = WallpaperRotation.images(new File(dir, CUSTOM));
        if (!custom.isEmpty()) {
            header.add(activity.getString(R.string.wallpaper_custom));
            for (File f : custom) sink.add(f.getName(), f, CUSTOM + "/" + f.getName(), CUSTOM);
        }
        dialog.show();
        first[0].requestFocus();
    }

    /** Row builders, so the groups above read as a list. */
    private interface Row {
        interface Sink {
            void add(String label, File file, String choice, String set);
        }

        interface Header {
            void add(String text);
        }
    }
}
