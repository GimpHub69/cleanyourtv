package io.github.cleanyourtv.news;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reader mode: the article's own page fetched once and reduced to its title and paragraphs, so it reads on a TV
 * without the publisher's banners, sign-in walls and scripts. Plain regex over the HTML, no parser library: the
 * {@code <article>} (else {@code <main>}, else the body) with its scripts, navigation, figures and asides cut out,
 * then every {@code <p>} and {@code <h2>} of it in order.
 */
final class Reader {
    static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final Pattern CUT = Pattern.compile(
            "(?is)<(script|style|noscript|svg|iframe|aside|nav|footer|header|form|figure|figcaption|button|picture|video)\\b[^>]*>.*?</\\1>");
    private static final Pattern SCOPE_ARTICLE = Pattern.compile("(?is)<article\\b[^>]*>(.*?)</article>");
    private static final Pattern SCOPE_MAIN = Pattern.compile("(?is)<main\\b[^>]*>(.*?)</main>");
    private static final Pattern BLOCK = Pattern.compile("(?is)<(p|h2)\\b[^>]*>(.*?)</\\1>");
    private static final Pattern OG_TITLE = Pattern.compile(
            "(?is)<meta\\b[^>]*(?:property|name)=[\"']og:title[\"'][^>]*content=[\"']([^\"']*)[\"']|<meta\\b[^>]*content=[\"']([^\"']*)[\"'][^>]*(?:property|name)=[\"']og:title[\"']");
    private static final Pattern H1 = Pattern.compile("(?is)<h1\\b[^>]*>(.*?)</h1>");
    private static final Pattern TITLE = Pattern.compile("(?is)<title\\b[^>]*>(.*?)</title>");
    private static final Pattern CHARSET = Pattern.compile("(?i)charset=([\\w-]+)");

    private Reader() {}

    static final class Article {
        final String title;
        /** Paragraphs in page order; a subheading is one of them, starting with {@code § } so the page can style it. */
        final List<String> paragraphs;

        Article(String title, List<String> paragraphs) {
            this.title = title;
            this.paragraphs = paragraphs;
        }
    }

    static Article extract(String html) {
        String title = "";
        Matcher og = OG_TITLE.matcher(html);
        if (og.find()) title = Feed.clean(og.group(1) != null ? og.group(1) : og.group(2));
        if (title.isEmpty()) {
            Matcher h1 = H1.matcher(html);
            if (h1.find()) title = Feed.clean(h1.group(1));
        }
        if (title.isEmpty()) {
            Matcher t = TITLE.matcher(html);
            if (t.find()) title = Feed.clean(t.group(1));
        }
        String scope = html;
        Matcher a = SCOPE_ARTICLE.matcher(html);
        if (a.find()) scope = a.group(1);
        else {
            Matcher m = SCOPE_MAIN.matcher(html);
            if (m.find()) scope = m.group(1);
        }
        // Nested blocks of the same kind: cut until nothing changes, within reason.
        for (int pass = 0; pass < 3; pass++) {
            String cut = CUT.matcher(scope).replaceAll(" ");
            if (cut.equals(scope)) break;
            scope = cut;
        }
        List<String> out = new ArrayList<>();
        Matcher b = BLOCK.matcher(scope);
        while (b.find()) {
            String text = Feed.clean(b.group(2));
            boolean heading = "h2".equalsIgnoreCase(b.group(1));
            if (text.isEmpty() || (!heading && text.length() < 40 && !text.endsWith("."))) continue;
            String line = heading ? "§ " + text : text;
            if (!out.isEmpty() && out.get(out.size() - 1).equals(line)) continue;
            out.add(line);
        }
        // A heading with nothing under it (the page's footer) adds nothing.
        while (!out.isEmpty() && out.get(out.size() - 1).startsWith("§ ")) out.remove(out.size() - 1);
        return new Article(title, out);
    }

    /** The page over HTTP(S), at most MAX_BYTES, in the charset its headers name (UTF-8 otherwise). */
    static String fetch(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(Feed.TIMEOUT_MS);
        c.setReadTimeout(Feed.TIMEOUT_MS);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "cleanyourtv-news/1.0");
        c.setRequestProperty("Accept", "text/html");
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[16384];
            int n;
            while ((n = in.read(b)) > 0) {
                buf.write(b, 0, n);
                if (buf.size() > MAX_BYTES) throw new IOException("page over " + MAX_BYTES + " bytes");
            }
            String type = c.getContentType();
            Matcher cs = type == null ? null : CHARSET.matcher(type);
            String charset = cs != null && cs.find() ? cs.group(1) : StandardCharsets.UTF_8.name();
            try {
                return buf.toString(charset);
            } catch (java.io.UnsupportedEncodingException e) {
                return buf.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            c.disconnect();
        }
    }
}
