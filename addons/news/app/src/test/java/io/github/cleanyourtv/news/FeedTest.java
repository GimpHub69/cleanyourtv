package io.github.cleanyourtv.news;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** The feed parser on an RSS and an Atom document, and the cache line round trip. */
public class FeedTest {
    private static final String RSS = "<?xml version=\"1.0\"?><rss version=\"2.0\" xmlns:media=\"http://search.yahoo.com/mrss/\"><channel>"
            + "<title><![CDATA[BBC News]]></title>"
            + "<item><title><![CDATA[First &amp; foremost]]></title><description><![CDATA[<p>Some <b>HTML</b> here.</p>]]></description>"
            + "<link>https://example.org/1</link><pubDate>Sun, 01 Nov 2026 15:04:56 GMT</pubDate>"
            + "<media:thumbnail url=\"https://ichef.example/ace/standard/240/pic.jpg\" width=\"240\" height=\"135\"><title>not this</title></media:thumbnail></item>"
            + "<item><title>Older</title><pubDate>Sat, 31 Oct 2026 09:00:00 GMT</pubDate></item>"
            + "<item><title> </title></item>"
            + "</channel></rss>";
    private static final String ATOM = "<?xml version=\"1.0\"?><feed xmlns=\"http://www.w3.org/2005/Atom\"><title>Example</title>"
            + "<entry><title>Atom one</title><link rel=\"alternate\" href=\"https://example.org/a\"/><updated>2026-11-01T10:00:00Z</updated>"
            + "<summary>Short.</summary></entry></feed>";

    private static List<Feed.Item> parse(String xml) throws Exception {
        return Feed.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void rssItemsKeepTheirOwnTitleAndCleanText() throws Exception {
        List<Feed.Item> items = parse(RSS);
        assertEquals(2, items.size());
        assertEquals("BBC News", items.get(0).source);
        assertEquals("First & foremost", items.get(0).title);
        assertEquals("Some HTML here.", items.get(0).summary);
        assertEquals("https://example.org/1", items.get(0).link);
        assertEquals("https://ichef.example/ace/standard/480/pic.jpg", items.get(0).image);
        assertEquals("", items.get(1).image);
        assertEquals(Feed.parseDate("Sun, 01 Nov 2026 15:04:56 GMT"), items.get(0).published);
        assertTrue(items.get(0).published > items.get(1).published);
    }

    @Test
    public void atomEntriesParseToo() throws Exception {
        List<Feed.Item> items = parse(ATOM);
        assertEquals(1, items.size());
        assertEquals("Example", items.get(0).source);
        assertEquals("https://example.org/a", items.get(0).link);
        assertEquals(Feed.parseDate("2026-11-01T10:00:00Z"), items.get(0).published);
        assertEquals("Short.", items.get(0).summary);
    }

    @Test
    public void newestFirstAndCapped() throws Exception {
        List<Feed.Item> all = parse(RSS);
        all.addAll(parse(ATOM));
        List<Feed.Item> top = Feed.newest(all, 2);
        assertEquals(2, top.size());
        assertEquals("First & foremost", top.get(0).title);
        assertEquals("Atom one", top.get(1).title);
    }

    @Test
    public void theCacheLineRoundTrips() throws Exception {
        List<Feed.Item> items = parse(RSS);
        List<Feed.Item> back = Feed.decode(Feed.encode(items) + "\u001Ebroken");
        assertEquals(2, back.size());
        assertEquals(items.get(1).title, back.get(1).title);
        assertEquals(items.get(0).published, back.get(0).published);
        assertEquals(items.get(0).image, back.get(0).image);
        assertEquals(40, Feed.hash(items.get(0).image).length());
        assertEquals(0, Feed.decode("").size());
    }

    @Test
    public void badDatesAreZero() {
        assertEquals(0, Feed.parseDate("yesterday"));
        assertEquals(0, Feed.parseDate(""));
    }

    @Test
    public void doctypeIsRefused() {
        boolean refused = false;
        try {
            parse("<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><rss><channel><title>&e;</title></channel></rss>");
        } catch (Exception e) {
            refused = true;
        }
        assertTrue(refused);
    }
}
