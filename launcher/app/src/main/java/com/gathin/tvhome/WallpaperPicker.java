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
import java.util.ArrayList;
import java.util.List;

/**
 * The Wallpaper row's picker, on the Theme picker's panel: Theme default and None first, then every picture the TV
 * holds, grouped: the current theme's, each stored theme's (themes/<name>/) and the Custom folder (custom/, written
 * through ThemeProvider). The focused picture shows on the right. OK holds that picture, and remembers its group as
 * the set the Cycle row rotates through ({@link Config#WALLPAPER_SET_KEY}). Left and Right on the Wallpaper row step
 * through the same list without opening the picker ({@link #entries}).
 */
final class WallpaperPicker {
    static final String CUSTOM = "custom";

    /** One row of the picker: a group header before it when it opens a group; file null for Theme default and None. */
    static final class Entry {
        final String header, label, choice, set;
        final File file;

        Entry(String header, String label, File file, String choice, String set) {
            this.header = header;
            this.label = label;
            this.file = file;
            this.choice = choice;
            this.set = set;
        }
    }

    interface OnPick {
        /** The picture's path relative to the theme folder (or "none" / null for Theme default) and its group's folder. */
        void pick(String choice, String set);
    }

    private WallpaperPicker() {}

    /** Theme default, None, then the pictures by group. */
    static List<Entry> entries(Activity activity, File dir, List<File> themes) {
        List<Entry> out = new ArrayList<>();
        out.add(new Entry(null, activity.getString(R.string.wallpaper_theme_default), null, null, null));
        out.add(new Entry(null, activity.getString(R.string.wallpaper_none), null, WallpaperRotation.CHOICE_NONE, null));
        // The current theme's pictures keep their bare names: the choice Config always understood.
        List<File> collection = WallpaperRotation.images(new File(dir, ThemeFiles.WALLPAPERS));
        File single = new File(dir, Config.WALLPAPER_NAME);
        String header = !collection.isEmpty() || single.isFile() ? activity.getString(R.string.wallpaper_group_current) : null;
        if (single.isFile()) {
            out.add(new Entry(header, single.getName(), single, single.getName(), ""));
            header = null;
        }
        for (File f : collection) {
            out.add(new Entry(header, f.getName(), f, f.getName(), ThemeFiles.WALLPAPERS));
            header = null;
        }
        for (File theme : themes) {
            String base = ThemeFiles.THEMES + "/" + theme.getName();
            File themeSingle = new File(theme, Config.WALLPAPER_NAME);
            List<File> pictures = WallpaperRotation.images(new File(theme, ThemeFiles.WALLPAPERS));
            header = ThemeSwitch.title(theme.getName());
            if (themeSingle.isFile()) {
                out.add(new Entry(header, themeSingle.getName(), themeSingle, base + "/" + themeSingle.getName(), base));
                header = null;
            }
            for (File f : pictures) {
                out.add(new Entry(header, f.getName(), f, base + "/" + ThemeFiles.WALLPAPERS + "/" + f.getName(), base + "/" + ThemeFiles.WALLPAPERS));
                header = null;
            }
        }
        header = activity.getString(R.string.wallpaper_custom);
        for (File f : WallpaperRotation.images(new File(dir, CUSTOM))) {
            out.add(new Entry(header, f.getName(), f, CUSTOM + "/" + f.getName(), CUSTOM));
            header = null;
        }
        return out;
    }

    static void show(Activity activity, List<Entry> entries, String current, OnPick onPick) {
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
        View first = null;
        for (Entry e : entries) {
            if (e.header != null) {
                TextView h = new TextView(activity);
                h.setText(e.header);
                h.setTextColor(0xFF9AA4AE);
                h.setTextSize(12);
                h.setAllCaps(true);
                h.setLetterSpacing(0.08f);
                h.setPadding((int) (16 * dp), (int) (10 * dp), 0, (int) (6 * dp));
                rows.addView(h);
            }
            TextView row = new TextView(activity);
            row.setText(e.label);
            row.setTextColor(0xFFFFFFFF);
            row.setTextSize(17);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding((int) (16 * dp), 0, (int) (16 * dp), 0);
            row.setBackgroundResource(R.drawable.settings_choice_bg);
            row.setFocusable(true);
            row.setSelected(e.choice == null ? current == null : e.choice.equals(current));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int) (40 * dp));
            rlp.bottomMargin = (int) (6 * dp);
            rows.addView(row, rlp);
            row.setOnFocusChangeListener((v, focused) -> {
                if (!focused) return;
                name.setText(e.label);
                Bitmap bitmap = e.file != null && e.file.isFile() ? ThemePicker.decode(e.file, preview.getWidth()) : null;
                preview.setImageBitmap(bitmap);
            });
            row.setOnClickListener(v -> {
                dialog.dismiss();
                onPick.pick(e.choice, e.set);
            });
            if (row.isSelected() || first == null) first = row;
        }
        dialog.show();
        first.requestFocus();
    }
}
