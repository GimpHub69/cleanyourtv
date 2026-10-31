package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
 * The Theme row's picker: the stored themes down the left, a preview of the focused one on the right from its
 * screenshot.jpg (the one the repo keeps beside each theme), "Built-in" first for the plain config.xml look. OK applies.
 */
final class ThemePicker {
    interface OnPick {
        /** The theme folder, or null for Built-in. */
        void pick(File theme);
    }

    private ThemePicker() {}

    static void show(Activity activity, List<File> themes, String current, OnPick onPick) {
        Dialog dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_theme_picker);
        WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
        lp.dimAmount = 0.8f;
        dialog.getWindow().setAttributes(lp);
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
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
        View focusFirst = null;
        for (int i = -1; i < themes.size(); i++) {
            final File theme = i < 0 ? null : themes.get(i);
            final String label = theme == null ? activity.getString(R.string.theme_builtin) : ThemeSwitch.title(theme.getName());
            TextView row = new TextView(activity);
            row.setText(label);
            row.setTextColor(0xFFFFFFFF);
            row.setTextSize(17);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding((int) (16 * dp), 0, (int) (16 * dp), 0);
            row.setBackgroundResource(R.drawable.settings_choice_bg);
            row.setFocusable(true);
            row.setSelected(theme == null ? current == null : theme.getName().equals(current));
            // A swatch of the theme's background and accent colours before its name.
            if (theme != null) {
                int[] colors = ThemeSwitch.colors(theme);
                android.graphics.drawable.GradientDrawable swatch = new android.graphics.drawable.GradientDrawable();
                swatch.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                swatch.setColor(colors[0]);
                swatch.setStroke((int) (2 * dp), colors[1] != 0 ? colors[1] : 0x66FFFFFF);
                swatch.setSize((int) (16 * dp), (int) (16 * dp));
                row.setCompoundDrawablesRelativeWithIntrinsicBounds(swatch, null, null, null);
                row.setCompoundDrawablePadding((int) (12 * dp));
            }
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int) (40 * dp));
            rlp.bottomMargin = (int) (6 * dp);
            rows.addView(row, rlp);
            row.setOnFocusChangeListener((v, focused) -> {
                if (!focused) return;
                name.setText(label);
                hint.setText(theme == null ? R.string.theme_builtin_hint : R.string.theme_pick_hint);
                File shot = theme == null ? null : new File(theme, ThemeFiles.SCREENSHOT);
                Bitmap bitmap = shot != null && shot.isFile() ? decode(shot, preview.getWidth()) : null;
                fadeIn(preview, bitmap);
                preview.setContentDescription(bitmap == null ? activity.getString(R.string.theme_no_preview) : label);
            });
            row.setOnClickListener(v -> {
                dialog.dismiss();
                onPick.pick(theme);
            });
            if (row.isSelected() || focusFirst == null) focusFirst = row;
        }
        dialog.show();
        focusFirst.requestFocus();
    }

    /** The next preview fades in over the last, instead of a hard swap. */
    static void fadeIn(ImageView preview, Bitmap bitmap) {
        preview.animate().cancel();
        preview.setAlpha(0f);
        preview.setImageBitmap(bitmap);
        preview.animate().alpha(1f).setDuration(220).start();
    }

    /** The screenshot scaled down to about the preview's width, in 16-bit colour: one decode per focus change. */
    static Bitmap decode(File file, int width) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), o);
        int sample = 1;
        while (width > 0 && o.outWidth / (sample * 2) >= width) sample *= 2;
        o.inJustDecodeBounds = false;
        o.inSampleSize = sample;
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(file.getPath(), o);
    }
}
