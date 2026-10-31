package com.gathin.tvhome;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The Theme row in Launcher settings: themes kept on the TV under {@code <files>/themes/<name>/}, each a folder as
 * in the repo's themes/ (config.json, an optional wallpaper.jpg or wallpapers/ collection with captions.txt), put
 * there with {@code content write} through ThemeProvider. Applying one does on the TV what tools/theme.sh does
 * over adb: the current theme files go, the chosen theme's are copied in and theme_revision gets a new token,
 * so Config clears the on-TV choices as after a push. Home rebuilds through the settings change count.
 */
final class ThemeSwitch {
    private ThemeSwitch() {}

    /** A folder name as a title: "nature-calm" reads "Nature Calm". The folder name stays the key. */
    static String title(String folder) {
        StringBuilder b = new StringBuilder();
        boolean up = true;
        for (char c : folder.toCharArray()) {
            if (c == '-' || c == '_') {
                b.append(' ');
                up = true;
            } else {
                b.append(up ? Character.toUpperCase(c) : c);
                up = false;
            }
        }
        return b.toString();
    }

    /** A stored theme's background_color and accent_color from its config.json, opaque; black and 0 (none) when unreadable. */
    static int[] colors(File theme) {
        int[] out = {0xFF000000, 0};
        try (InputStream in = new FileInputStream(new File(theme, Config.FILE_NAME))) {
            byte[] buf = new byte[(int) Math.min(64 * 1024, new File(theme, Config.FILE_NAME).length())];
            int n = in.read(buf);
            org.json.JSONObject json = new org.json.JSONObject(new String(buf, 0, Math.max(0, n), StandardCharsets.UTF_8));
            String bg = json.optString("background_color", ""), accent = json.optString("accent_color", "");
            if (!bg.isEmpty()) out[0] = android.graphics.Color.parseColor(bg) | 0xFF000000;
            if (!accent.isEmpty()) out[1] = android.graphics.Color.parseColor(accent) | 0xFF000000;
        } catch (Exception ignored) {
            // A theme without a readable config keeps the plain swatch.
        }
        return out;
    }

    /** The theme folders under {@code dir/themes} that carry a config.json, by name. */
    static List<File> themes(File dir) {
        List<File> out = new ArrayList<>();
        File[] entries = dir == null ? null : new File(dir, ThemeFiles.THEMES).listFiles();
        if (entries == null) return out;
        for (File f : entries) {
            if (f.isDirectory() && ThemeFiles.themeName(f.getName()) && new File(f, Config.FILE_NAME).isFile()) out.add(f);
        }
        Collections.sort(out);
        return out;
    }

    /** Replaces the theme in {@code dir} with {@code theme}'s files; false when a copy failed (the rest still went in). */
    static boolean apply(File dir, File theme) {
        if (dir == null || theme == null) return false;
        File wallpapers = new File(dir, ThemeFiles.WALLPAPERS);
        clear(dir);
        boolean ok = copy(new File(theme, Config.FILE_NAME), new File(dir, Config.FILE_NAME));
        File single = new File(theme, Config.WALLPAPER_NAME);
        if (single.isFile()) ok &= copy(single, new File(dir, Config.WALLPAPER_NAME));
        File[] pictures = new File(theme, ThemeFiles.WALLPAPERS).listFiles();
        if (pictures != null) {
            wallpapers.mkdirs();
            for (File f : pictures) {
                if (ThemeFiles.collectionImage(f.getName()) || WallpaperCaptions.FILE_NAME.equals(f.getName())) {
                    ok &= copy(f, new File(wallpapers, f.getName()));
                }
            }
        }
        return stamp(dir) && ok;
    }

    /** Built-in: no theme files at all, as tools/theme.sh --reset leaves the TV. */
    static boolean reset(File dir) {
        if (dir == null) return false;
        clear(dir);
        return stamp(dir);
    }

    private static void clear(File dir) {
        delete(new File(dir, Config.FILE_NAME));
        delete(new File(dir, Config.WALLPAPER_NAME));
        File[] old = new File(dir, ThemeFiles.WALLPAPERS).listFiles();
        if (old != null) for (File f : old) if (ThemeFiles.collectionImage(f.getName()) || WallpaperCaptions.FILE_NAME.equals(f.getName())) delete(f);
    }

    /** A fresh theme_revision token, so Config clears the on-TV choices as after a push. */
    private static boolean stamp(File dir) {
        try (OutputStream out = new FileOutputStream(new File(dir, "theme_revision"))) {
            out.write(UUID.randomUUID().toString().replace("-", "").getBytes(StandardCharsets.US_ASCII));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void delete(File f) {
        if (f.isFile()) f.delete();
    }

    private static boolean copy(File from, File to) {
        if (!from.isFile()) return false;
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
