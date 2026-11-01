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
| `feeds` | the two BBC feeds | The feed URLs, merged newest first. |
| `max_items` | `30` | Headlines kept across all feeds. |

## The headlines for the launcher

The app answers `content://io.github.cleanyourtv.news/headlines` with one row per story, newest first: `title`,
`source` (the feed's name, "BBC News"), `summary`, `link`, `published` and `fetched` (both epoch milliseconds),
from its own cache. A query older than 15 minutes starts one fetch in the background, so the next query is fresh;
5 minutes between attempts after a failure. The Clean Your TV launcher's `news_provider` key points at it and
shows "BBC News · headline" under the clock, the next story every 20 seconds, and hides the line once `fetched`
is over six hours old. Any app on the TV can read it (no permission): it is what the page already shows.

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
card prompt, and the headline line under the launcher's clock.

## Maintainer

[@GimpHub69](https://github.com/GimpHub69)
