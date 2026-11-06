# News

The latest headlines on the TV, from RSS or Atom feeds: a full-screen list down the left, the focused story's
summary on the right, newest first across every feed. For the [Clean Your TV](../../README.md) launcher it
declares a [module card](../../docs/modules.md), so once you answer **Show card** its tile reads "News"; on any
other Android TV home it is an ordinary app. There is no browser on most TVs, so a story is read here, not opened.

## Sources

The feeds are a list in `app/src/main/res/values/options.xml`, read every 15 minutes while the page is on
screen or while the launcher asks for a headline. The defaults need no account or key:

| Feed | URL | Size |
|---|---|---|
| BBC News, front page | `https://feeds.bbci.co.uk/news/rss.xml` | about 24 KB |
| BBC News, technology | `https://feeds.bbci.co.uk/news/technology/rss.xml` | about 15 KB |

The BBC's [terms](https://www.bbc.co.uk/usingthebbc/terms-of-use/) allow personal use of its feeds. Any other
RSS 2.0 or Atom feed works (Sky News, a newspaper, a blog); replace or add `<item>` lines and rebuild. Each feed
is downloaded whole, so keep them small: a feed over 512 KB is refused (The Guardian's is about 500 KB).

## Options

Set in `app/src/main/res/values/options.xml` before building.

| Option | Default | What it does |
|---|---|---|
| `feeds` | the two BBC feeds | The feed URLs read until the owner picks others on the TV, merged newest first. |
| `max_items` | `30` | Headlines kept across all feeds. |
| `preset_names`, `preset_urls` | 13 BBC and Sky News feeds | The feeds offered on the app's Feeds page. |

### Feeds page

The Feeds button at the top of the app lists the presets with a tick each, any address the owner added, and "Add
another feed address…", which takes one RSS or Atom URL with the TV's keyboard. OK saves the ticked list and fetches
it at once. Each feed is downloaded whole every 15 minutes and must stay under 512 KB; the presets were 6 to 53 KB
on 2026-11-06 (the Guardian's is about 500 KB, the New York Times' 124 KB). A feed without `media:thumbnail` or an
image enclosure gives stories without pictures.

### The full article

OK on a headline opens reader mode (`ReaderActivity`): the page is fetched once (at most 2 MB, the add-on's user
agent) and reduced to its title and the paragraphs and subheadings of its `<article>` (else `<main>`, else the
body), with scripts, navigation, figures, asides and footers cut out (`Reader.java`, plain regex). Up and Down
scroll, Back returns to the headlines. OK on the text opens the publisher's own page in the TV's WebView
(`ArticleActivity`, JavaScript and DOM storage on, with its banners and sign-in walls); a page with fewer than two
readable paragraphs goes there at once.

### Pictures

After each fetch the app downloads the picture of every kept story (within 30 seconds per pass, over HTTPS only,
at most 2 MB each) shrunk to 800 px wide and saved as JPEG in its cache. The app shows them as thumbnails in the
list and large beside the summary; the launcher's `news_card` reads them through the provider.

## The headlines for the launcher

The app answers `content://io.github.cleanyourtv.news/headlines` with one row per story, newest first: `title`,
`source` (the feed's name, "BBC News"), `summary`, `link`, `image`, `published` and `fetched` (both epoch
milliseconds), from its own cache. `image` is a `content://io.github.cleanyourtv.news/image/<id>` URI for the
story's picture (the feed's `media:thumbnail` or image enclosure, BBC's asked for at 800 px), once the app has it:
after each fetch it downloads every kept story's picture, shrinks them to about 800 px wide and keeps them as
JPEGs in its cache folder (about 30 KB each, old ones deleted); any app can open them read-only. A query older than 15 minutes starts one fetch in the background, so the next query is fresh;
5 minutes between attempts after a failure. The Clean Your TV launcher's `news_provider` key points at it and
shows "BBC News · headline" under the clock, the next story every 20 seconds, and hides the line once `fetched`
is over six hours old; with its `news_card` key the line becomes a picture card with the headline beside it. Any app on the TV can read it (no permission): it is what the page already shows.

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | The feeds in `options.xml`. |

That is all. What is sent: a plain GET for each feed URL, with the User-Agent `cleanyourtv-news/1.0`. No
account, no telemetry, no ads, no background service, no location. External entities are refused when the XML is
parsed. The cache lives in the app's own storage (backups are off).

## Build and install

JDK 17 and Android SDK platform 34 with build-tools 34.0.0, as for the launcher:

```bash
cd addons/news && ./gradlew assembleRelease
TV_HOST=<tv ip> ../../tools/venv/bin/python -I ../../tools/tv.py --push app/build/outputs/apk/release/app-release.apk /data/local/tmp/addon.apk
TV_HOST=<tv ip> ../../tools/venv/bin/python -I ../../tools/tv.py 'pm install -r /data/local/tmp/addon.apk; rm -f /data/local/tmp/addon.apk'
```

Press Home: the launcher asks once whether to show the card. Change the answer later by holding OK on
Settings, then Module cards.

The release build is signed with the debug key so it installs over ADB like the launcher. Sign your own builds
with your own key if you hand them out; the launcher pins its Show answer to the signing key.

## Tested on

Amazon Fire TV Stick 4K Max (2nd gen, AFTKRT), Fire OS 8.1.8.5 (Android 11): the list, the summary panel, the
card in the launcher's grid, and the headline line under its clock. With Fire OS's Data Saver on, a fetch
started by the launcher's query (the app in the background) gets no DNS; allow the app background data
(`cmd netpolicy add restrict-background-whitelist <uid>` over ADB, or Settings) or the line only refreshes
while this page is open.

## Maintainer

[@GimpHub69](https://github.com/GimpHub69)
