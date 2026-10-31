package com.gathin.tvhome;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ThemeSwitchTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private File theme(File dir, String name, String json, String... pictures) throws IOException {
        File t = new File(new File(dir, "themes"), name);
        assertTrue(t.mkdirs());
        Files.write(new File(t, "config.json").toPath(), json.getBytes(StandardCharsets.UTF_8));
        if (pictures.length > 0) {
            File w = new File(t, "wallpapers");
            assertTrue(w.mkdirs());
            for (String p : pictures) Files.write(new File(w, p).toPath(), p.getBytes(StandardCharsets.UTF_8));
        }
        return t;
    }

    @Test public void listsOnlyFoldersWithAConfig() throws IOException {
        File dir = tmp.getRoot();
        theme(dir, "sweden", "{}");
        theme(dir, "aurora", "{}");
        assertTrue(new File(new File(dir, "themes"), "empty").mkdirs());
        assertTrue(new File(new File(dir, "themes"), "bad name").mkdirs());
        List<File> themes = ThemeSwitch.themes(dir);
        assertEquals(2, themes.size());
        assertEquals("aurora", themes.get(0).getName());
        assertEquals("sweden", themes.get(1).getName());
        assertTrue(ThemeSwitch.themes(tmp.newFolder("nothing")).isEmpty());
        assertTrue(ThemeSwitch.themes(null).isEmpty());
    }

    @Test public void applyReplacesTheCurrentThemeFiles() throws IOException {
        File dir = tmp.getRoot();
        File old = new File(dir, "wallpapers");
        assertTrue(old.mkdirs());
        Files.write(new File(dir, "config.json").toPath(), "{\"columns\": 7}".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "wallpaper.jpg").toPath(), new byte[]{1});
        Files.write(new File(old, "stale.jpg").toPath(), new byte[]{2});
        Files.write(new File(old, "captions.txt").toPath(), new byte[]{3});
        File sweden = theme(dir, "sweden", "{\"columns\": 6}", "001.jpg", "captions.txt", "notes.sh");
        assertTrue(ThemeSwitch.apply(dir, sweden));
        assertEquals("{\"columns\": 6}", new String(Files.readAllBytes(new File(dir, "config.json").toPath()), StandardCharsets.UTF_8));
        assertFalse(new File(dir, "wallpaper.jpg").exists());
        assertFalse(new File(old, "stale.jpg").exists());
        assertTrue(new File(old, "001.jpg").isFile());
        assertEquals("captions.txt", new String(Files.readAllBytes(new File(old, "captions.txt").toPath()), StandardCharsets.UTF_8));
        assertFalse(new File(old, "notes.sh").exists());
        File revision = new File(dir, "theme_revision");
        assertEquals(32, revision.length());
        String first = new String(Files.readAllBytes(revision.toPath()), StandardCharsets.US_ASCII);
        // The theme's own folder is left as it was, so it can be applied again.
        assertTrue(new File(sweden, "config.json").isFile());
        assertTrue(ThemeSwitch.apply(dir, sweden));
        assertFalse(first.equals(new String(Files.readAllBytes(revision.toPath()), StandardCharsets.US_ASCII)));
    }

    @Test public void resetLeavesNoThemeFilesAndANewToken() throws IOException {
        File dir = tmp.getRoot();
        File old = new File(dir, "wallpapers");
        assertTrue(old.mkdirs());
        Files.write(new File(dir, "config.json").toPath(), "{}".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(old, "stale.jpg").toPath(), new byte[]{2});
        Files.write(new File(dir, "theme_revision").toPath(), "old".getBytes(StandardCharsets.US_ASCII));
        assertTrue(ThemeSwitch.reset(dir));
        assertFalse(new File(dir, "config.json").exists());
        assertFalse(new File(old, "stale.jpg").exists());
        assertEquals(32, new File(dir, "theme_revision").length());
        assertFalse(ThemeSwitch.reset(null));
    }

    @Test public void folderNamesReadAsTitles() {
        assertEquals("Nature Calm", ThemeSwitch.title("nature-calm"));
        assertEquals("Six Columns", ThemeSwitch.title("six_columns"));
        assertEquals("Glass", ThemeSwitch.title("glass"));
        assertEquals("", ThemeSwitch.title(""));
    }

    @Test public void aThemeWithoutAConfigFails() throws IOException {
        File dir = tmp.getRoot();
        assertFalse(ThemeSwitch.apply(dir, tmp.newFolder("themes", "broken")));
        assertFalse(ThemeSwitch.apply(null, dir));
    }
}
