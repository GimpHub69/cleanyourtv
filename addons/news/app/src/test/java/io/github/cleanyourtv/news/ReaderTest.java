package io.github.cleanyourtv.news;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReaderTest {
    private static final String PAGE = "<html><head><title>Site | Page</title>"
            + "<meta property=\"og:title\" content=\"Met chief warns of &amp; reform\"></head><body>"
            + "<nav><p>Home News Sport and a long navigation paragraph that must not appear here.</p></nav>"
            + "<article><header><h1>Met chief warns</h1></header>"
            + "<script>var x = '<p>not this one, it is script text that is long enough</p>';</script>"
            + "<figure><img src=x><figcaption><p>A caption long enough to pass the length rule, but a caption.</p></figcaption></figure>"
            + "<div><p>Sir Mark Rowley will argue that the current system is unable to cope with modern criminals.</p></div>"
            + "<h2>What he proposes</h2>"
            + "<p>Fewer, larger forces, he says, with <b>shared</b> services and a national lead on serious crime.</p>"
            + "<p>Short.</p><p>tiny</p>"
            + "<aside><p>Related stories you might like, which run on and on and on but are not the article.</p></aside>"
            + "<h2>Footer heading</h2></article>"
            + "<footer><p>Copyright notice long enough to pass the length rule, but it is the footer.</p></footer></body></html>";

    @Test
    public void titleFromOpenGraphAndParagraphsFromTheArticleOnly() {
        Reader.Article a = Reader.extract(PAGE);
        assertEquals("Met chief warns of & reform", a.title);
        assertEquals(4, a.paragraphs.size());
        assertTrue(a.paragraphs.get(0).startsWith("Sir Mark Rowley"));
        assertEquals("\u00A7 What he proposes", a.paragraphs.get(1));
        assertEquals("Fewer, larger forces, he says, with shared services and a national lead on serious crime.", a.paragraphs.get(2));
        assertEquals("Short.", a.paragraphs.get(3));
    }

    @Test
    public void pageWithoutArticleUsesMainThenBody() {
        Reader.Article a = Reader.extract("<html><body><main><p>One paragraph that is certainly long enough to count here.</p>"
                + "<p>And a second one that is also long enough to count as a paragraph.</p></main></body></html>");
        assertEquals(2, a.paragraphs.size());
        assertEquals("", a.title);
    }
}
