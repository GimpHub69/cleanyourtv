package com.gathin.tvhome;

import java.util.List;
import java.util.regex.Pattern;

/** The files ThemeProvider may write or delete under Config.dir, by the URI's path segments. */
final class ThemeFiles {
    static final String WALLPAPERS = "wallpapers";
    /** Themes kept on the TV for the Theme row in Launcher settings (ThemeSwitch): themes/<name>/<theme files>. */
    static final String THEMES = "themes";
    /** The names tools/theme.sh accepts for a collection image, and the types WallpaperRotation reads. */
    private static final Pattern IMAGE = Pattern.compile("[A-Za-z0-9_.-]+\\.(jpg|jpeg|png|webp)",
            Pattern.CASE_INSENSITIVE);
    /** A theme folder's name, as in the repo's themes/. */
    private static final Pattern THEME = Pattern.compile("[A-Za-z0-9_-]+");
    /** A theme's notes beside its files (CREDITS.md, README.md). */
    private static final Pattern NOTE = Pattern.compile("[A-Za-z0-9_.-]+\\.(md|txt)", Pattern.CASE_INSENSITIVE);

    private ThemeFiles() {}

    /** The path relative to the theme directory, e.g. "config.json" or "wallpapers/dusk.jpg", or null if refused. */
    static String path(List<String> segments) {
        if (segments == null) return null;
        // A stored theme holds the same files under themes/<name>/, plus its credits.
        if (segments.size() >= 3 && THEMES.equals(segments.get(0)) && themeName(segments.get(1))) {
            List<String> rest = segments.subList(2, segments.size());
            String inner = rest.size() == 1 && NOTE.matcher(rest.get(0)).matches() ? rest.get(0) : path(rest);
            return inner == null ? null : THEMES + "/" + segments.get(1) + "/" + inner;
        }
        if (segments.size() == 1) {
            String name = segments.get(0);
            return Config.FILE_NAME.equals(name) || "wallpaper.jpg".equals(name) || "theme_revision".equals(name)
                    ? name : null;
        }
        if (segments.size() == 2 && WALLPAPERS.equals(segments.get(0))
                && (collectionImage(segments.get(1)) || WallpaperCaptions.FILE_NAME.equals(segments.get(1)))) {
            return WALLPAPERS + "/" + segments.get(1);
        }
        return null;
    }

    /** True for "wallpapers" alone: a delete of it clears the collection's images. */
    static boolean isCollection(List<String> segments) {
        return segments != null && segments.size() == 1 && WALLPAPERS.equals(segments.get(0));
    }

    /** True for "themes/<name>" alone: a delete of it removes that stored theme. */
    static boolean isTheme(List<String> segments) {
        return segments != null && segments.size() == 2 && THEMES.equals(segments.get(0)) && themeName(segments.get(1));
    }

    static boolean themeName(String name) {
        return name != null && THEME.matcher(name).matches();
    }

    static boolean collectionImage(String name) {
        return name != null && !name.startsWith(".") && IMAGE.matcher(name).matches();
    }
}
