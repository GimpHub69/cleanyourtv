package io.github.cleanyourtv.news;

import android.content.Context;
import android.content.SharedPreferences;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * The feeds in options.xml, read every REFRESH_MS into one SharedPreferences cache ("cache": the encoded items,
 * {@code fetched} = the last success, {@code tried} = the last attempt). RSS 2.0 and Atom, parsed with the
 * platform's DOM parser, external entities off. Nothing else on the network.
 */
final class Feed {
    static final long REFRESH_MS = 15 * 60 * 1000L, RETRY_MS = 5 * 60 * 1000L;
    static final int TIMEOUT_MS = 10_000, MAX_BYTES = 512 * 1024;
    static final String CACHE = "cache";
    /** The owner's feed list from the app's Feeds page ("settings": {@code feeds}, one URL per line); unset, options.xml. */
    static final String SETTINGS = "settings";
    private static final AtomicBoolean fetching = new AtomicBoolean();
    private static final char FIELD = '\u001F', RECORD = '\u001E';

    private Feed() {}

    static final class Item {
        final String source, title, summary, link;
        /** The story's picture (media:thumbnail or an image enclosure), empty when the feed gives none. */
        final String image;
        /** Epoch milliseconds, 0 when the feed gave no usable date. */
        final long published;

        Item(String source, String title, String summary, String link, String image, long published) {
            this.source = source;
            this.title = title;
            this.summary = summary;
            this.link = link;
            this.image = image;
            this.published = published;
        }
    }

    /** The items of one RSS 2.0 or Atom document; the channel title names the source. */
    static List<Item> parse(InputStream in) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        try {
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        } catch (ParserConfigurationException ignored) {
            // Android's DOM (Harmony over KXmlParser) knows only the two SAX feature names and throws for this
            // one; it fetches no external entities anyway. The JDK's Xerces, in the unit test, honours it.
        }
        f.setExpandEntityReferences(false);
        Document doc = f.newDocumentBuilder().parse(in);
        Element root = doc.getDocumentElement();
        List<Item> out = new ArrayList<>();
        boolean atom = "feed".equals(root.getLocalName() == null ? root.getTagName() : root.getLocalName());
        // RSS keeps the feed's title inside <channel>; Atom has it on the <feed> itself.
        Element head = root;
        if (!atom) {
            for (Node n = root.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n.getNodeType() == Node.ELEMENT_NODE && "channel".equals(n.getNodeName())) head = (Element) n;
            }
        }
        String source = clean(firstText(head, "title"));
        NodeList entries = root.getElementsByTagName(atom ? "entry" : "item");
        for (int i = 0; i < entries.getLength(); i++) {
            Element e = (Element) entries.item(i);
            String title = clean(firstText(e, "title"));
            if (title.isEmpty()) continue;
            String summary = clean(atom ? firstText(e, "summary", "content") : firstText(e, "description"));
            String link = atom ? linkHref(e) : firstText(e, "link").trim();
            long when = parseDate(atom ? firstText(e, "updated", "published") : firstText(e, "pubDate"));
            out.add(new Item(source, title, summary, link, imageUrl(e), when));
        }
        return out;
    }

    /** Only direct children: an item's own title, not the one inside its media block. */
    private static String firstText(Element parent, String... names) {
        for (String name : names) {
            for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n.getNodeType() == Node.ELEMENT_NODE && name.equals(n.getLocalName() == null ? n.getNodeName() : n.getLocalName())) {
                    String t = n.getTextContent();
                    if (t != null && !t.trim().isEmpty()) return t;
                }
            }
        }
        return "";
    }

    /** media:thumbnail's url, else an image enclosure's; BBC's 240 px thumbnail is asked for at 480 px. */
    static String imageUrl(Element item) {
        String enclosure = "";
        for (Node n = item.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;
            String name = n.getNodeName();
            if (name.endsWith(":thumbnail") || name.endsWith(":content")) {
                String url = ((Element) n).getAttribute("url"), type = ((Element) n).getAttribute("type");
                if (!type.isEmpty() && !type.startsWith("image/")) continue;   // a media:content video
                if (url.startsWith("https://")) return url.replace("/240/", "/480/");
            } else if ("enclosure".equals(name) && ((Element) n).getAttribute("type").startsWith("image/")) {
                enclosure = ((Element) n).getAttribute("url");
            }
        }
        return enclosure.startsWith("https://") ? enclosure : "";
    }

    private static String linkHref(Element entry) {
        for (Node n = entry.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE && "link".equals(n.getLocalName() == null ? n.getNodeName() : n.getLocalName())) {
                String rel = ((Element) n).getAttribute("rel");
                if (rel.isEmpty() || "alternate".equals(rel)) return ((Element) n).getAttribute("href");
            }
        }
        return "";
    }

    /** Tags out, the common entities back to characters, whitespace folded: feed text is often HTML. */
    static String clean(String s) {
        if (s == null) return "";
        s = s.replaceAll("<[^>]+>", " ");
        s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'");
        return s.replaceAll("[\\s\\u001E\\u001F]+", " ").trim();
    }

    /** RFC 1123 (RSS) or ISO-8601 (Atom); 0 for anything else. */
    static long parseDate(String s) {
        if (s == null) return 0;
        s = s.trim();
        if (s.isEmpty()) return 0;
        try {
            return ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();
        } catch (RuntimeException ignored) {
        }
        try {
            return OffsetDateTime.parse(s).toInstant().toEpochMilli();
        } catch (RuntimeException ignored) {
        }
        return 0;
    }

    /** One feed over HTTPS, at most MAX_BYTES. */
    static List<Item> fetch(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setRequestProperty("User-Agent", "cleanyourtv-news/1.0");
        c.setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml");
        try (InputStream in = c.getInputStream()) {
            byte[] buf = new byte[MAX_BYTES];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            if (n >= buf.length) throw new IOException("feed over " + MAX_BYTES + " bytes: " + url);
            return parse(new ByteArrayInputStream(buf, 0, n));
        } finally {
            c.disconnect();
        }
    }

    /** Every feed merged, newest first, at most {@code max}; throws when every feed failed. */
    static List<Item> fetchAll(String[] urls, int max) throws Exception {
        List<Item> all = new ArrayList<>();
        Exception last = null;
        int ok = 0;
        for (String url : urls) {
            try {
                all.addAll(fetch(url));
                ok++;
            } catch (Exception e) {
                last = e;
            }
        }
        if (ok == 0 && last != null) throw last;
        return newest(all, max);
    }

    /** Newest first, capped; a story two feeds both carry (same link, or same title) is kept once. */
    static List<Item> newest(List<Item> all, int max) {
        List<Item> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Item i : all) {
            if (seen.add(i.link.isEmpty() ? "t:" + i.title : i.link) && seen.add("t:" + i.title)) out.add(i);
        }
        Collections.sort(out, (a, b) -> Long.compare(b.published, a.published));
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    /** The cache line: fields split by U+001F, items by U+001E (clean() keeps both out of the text). */
    static String encode(List<Item> items) {
        StringBuilder b = new StringBuilder();
        for (Item i : items) {
            if (b.length() > 0) b.append(RECORD);
            b.append(i.source).append(FIELD).append(i.title).append(FIELD).append(i.summary).append(FIELD)
                    .append(i.link).append(FIELD).append(i.image).append(FIELD).append(i.published);
        }
        return b.toString();
    }

    static List<Item> decode(String line) {
        List<Item> out = new ArrayList<>();
        if (line == null || line.isEmpty()) return out;
        for (String rec : line.split(String.valueOf(RECORD))) {
            String[] f = rec.split(String.valueOf(FIELD), -1);
            if (f.length != 6) continue;
            long when;
            try {
                when = Long.parseLong(f[5]);
            } catch (NumberFormatException e) {
                continue;
            }
            out.add(new Item(f[0], f[1], f[2], f[3], f[4], when));
        }
        return out;
    }

    /** The feeds to read: the list saved on the Feeds page, else options.xml. */
    static String[] feeds(Context ctx) {
        String saved = ctx.getSharedPreferences(SETTINGS, 0).getString("feeds", null);
        List<String> list = saved == null ? null : parseFeeds(saved);
        return list == null || list.isEmpty() ? ctx.getResources().getStringArray(R.array.feeds) : list.toArray(new String[0]);
    }

    /** One URL per line, trimmed, blanks and repeats dropped, http(s) only. */
    static List<String> parseFeeds(String text) {
        List<String> out = new ArrayList<>();
        for (String line : text.split("\n")) {
            String u = line.trim();
            if ((u.startsWith("https://") || u.startsWith("http://")) && u.length() > 10 && !out.contains(u)) out.add(u);
        }
        return out;
    }

    static void saveFeeds(Context ctx, List<String> urls) {
        ctx.getSharedPreferences(SETTINGS, 0).edit().putString("feeds", String.join("\n", urls)).apply();
        // A changed list is fetched now, not after the usual quarter hour.
        cache(ctx).edit().putLong("fetched", 0).putLong("tried", 0).apply();
    }

    /** Pictures for the kept stories, each shrunk to IMAGE_WIDTH and saved as JPEG in the cache dir. */
    static final int IMAGE_WIDTH = 480, IMAGE_MAX_BYTES = 2 * 1024 * 1024;
    private static final long IMAGE_PASS_MS = 30_000L;

    static File imageDir(Context ctx) {
        return new File(ctx.getCacheDir(), "img");
    }

    /** The cached file for a picture URL (sha1 of the URL), whether or not it exists yet. */
    static File imageFile(Context ctx, String url) {
        return new File(imageDir(ctx), hash(url) + ".jpg");
    }

    static String hash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (byte x : d) b.append(String.format("%02x", x));
            return b.toString();
        } catch (Exception e) {
            return "0000000000000000000000000000000000000000";
        }
    }

    /** After the headlines are saved: their pictures, within IMAGE_PASS_MS; files for stories no longer kept go. */
    private static void cacheImages(Context ctx, List<Item> items) {
        File dir = imageDir(ctx);
        if (!dir.isDirectory() && !dir.mkdirs()) return;
        List<String> keep = new ArrayList<>();
        long deadline = System.currentTimeMillis() + IMAGE_PASS_MS;
        for (Item i : items) {
            if (i.image.isEmpty()) continue;
            File f = imageFile(ctx, i.image);
            keep.add(f.getName());
            if (f.isFile() || System.currentTimeMillis() > deadline) continue;
            try {
                fetchImage(i.image, f);
            } catch (Exception e) {
                android.util.Log.w("news", "image failed: " + e);
            }
        }
        File[] old = dir.listFiles();
        if (old != null) for (File f : old) if (!keep.contains(f.getName())) f.delete();
    }

    /** One picture over HTTPS, at most IMAGE_MAX_BYTES, decoded small (RGB_565, about IMAGE_WIDTH wide) and saved as JPEG. */
    private static void fetchImage(String url, File out) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setRequestProperty("User-Agent", "cleanyourtv-news/1.0");
        byte[] buf;
        int n = 0, r;
        try (InputStream in = c.getInputStream()) {
            buf = new byte[IMAGE_MAX_BYTES];
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            if (n >= buf.length) throw new IOException("picture over " + IMAGE_MAX_BYTES + " bytes");
        } finally {
            c.disconnect();
        }
        android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeByteArray(buf, 0, n, o);
        if (o.outWidth <= 0) throw new IOException("not a picture");
        o.inJustDecodeBounds = false;
        o.inSampleSize = 1;
        while (o.outWidth / (o.inSampleSize * 2) >= IMAGE_WIDTH) o.inSampleSize *= 2;
        o.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565;
        android.graphics.Bitmap b = android.graphics.BitmapFactory.decodeByteArray(buf, 0, n, o);
        if (b == null) throw new IOException("undecodable picture");
        File tmp = new File(out.getPath() + ".part");
        try (FileOutputStream fo = new FileOutputStream(tmp)) {
            b.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, fo);
        } finally {
            b.recycle();
        }
        if (!tmp.renameTo(out)) tmp.delete();
    }

    static SharedPreferences cache(Context ctx) {
        return ctx.getSharedPreferences(CACHE, 0);
    }

    static List<Item> cached(Context ctx) {
        return decode(cache(ctx).getString("items", ""));
    }

    /** The cache is older than REFRESH_MS, and the last attempt (failed or not) older than RETRY_MS. */
    static boolean stale(SharedPreferences cache) {
        long now = System.currentTimeMillis();
        return now - cache.getLong("fetched", 0) > REFRESH_MS && now - cache.getLong("tried", 0) > RETRY_MS;
    }

    /**
     * One fetch at a time, off the caller's thread, into the cache; {@code done} runs on that worker thread
     * afterwards (true when the feeds answered), or not at all when a fetch is already running.
     */
    static void refresh(Context ctx, java.util.function.Consumer<Boolean> done) {
        if (!fetching.compareAndSet(false, true)) return;
        Context app = ctx.getApplicationContext();
        new Thread(() -> {
            SharedPreferences cache = cache(app);
            boolean ok = false;
            try {
                String[] urls = feeds(app);
                int max = app.getResources().getInteger(R.integer.max_items);
                List<Item> items = fetchAll(urls, max);
                cache.edit().putString("items", encode(items)).putLong("fetched", System.currentTimeMillis()).apply();
                ok = true;
                cacheImages(app, items);
            } catch (Exception e) {
                // Offline or a broken feed: the cached items stay, the next query tries again after RETRY_MS.
                android.util.Log.w("news", "fetch failed: " + e);
            } finally {
                cache.edit().putLong("tried", System.currentTimeMillis()).apply();
                fetching.set(false);
            }
            if (done != null) done.accept(ok);
        }, "news-fetch").start();
    }
}
