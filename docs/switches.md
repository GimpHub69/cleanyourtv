# Switches on a TV

Every key a theme can set (the Keys table in [`themes/README.md`](../themes/README.md)) gets a
screenshot of what it does on a real TV. This table is where they are listed: the repo and
[cleanyour.tv/addons#switches](https://cleanyour.tv/addons#switches) both show them from here.

## Adding a key

A new key needs a screenshot. Put it in `docs/switches/<key>.jpg` (an existing screenshot in the
repo that shows the key at work also counts) and add a row below:

- From a real TV, with the key set, as a JPEG or PNG, 16:9 (1280×720 is plenty), under 300 KB.
- Show where the key changes something you can see. For a key that maps a remote button, show the
  screen the button opens; for one that changes behaviour only, show the setting's effect, such as
  the panel, the menu or the app that comes up.
- Blur anything personal: Wi-Fi names, people, addresses.

`tests/test_switches.py` checks that every key has a row here with an image that exists and fits
the rules. Keys from before this rule are on its wanted list; a screenshot for one of them is welcome.

## Screenshots

| Key | Screenshot |
|---|---|
| `columns` | <img src="../themes/six-columns/screenshot.jpg" width="320" alt="columns on a TV"> |
| `glass_style` | <img src="switches/glass_style.jpg" width="320" alt="glass_style halo on a TV"> |
| `sky_intensity` | <img src="sky-sunset.jpg" width="320" alt="sky_intensity on a TV"> |
| `wallpaper_rotation_minutes` | <img src="../themes/scenes/screenshot.jpg" width="320" alt="wallpaper_rotation_minutes on a TV"> |
| `festival` | <img src="../themes/dussehra/screenshot.jpg" width="320" alt="festival on a TV"> |
| `settings_key_panel` | <img src="quick-settings-panel.jpg" width="320" alt="settings_key_panel on a TV"> |
| `home_idle_clock_minutes` | <img src="clock-screensaver.png" width="320" alt="home_idle_clock_minutes on a TV"> |
| `gamepad_passthrough` | <img src="switches/gamepad_passthrough.jpg" width="320" alt="gamepad_passthrough on a TV"> |
| `consume_volume_keys` | <img src="switches/consume_volume_keys.jpg" width="320" alt="consume_volume_keys on a TV"> |
| `wifi_status_warnings` | <img src="switches/wifi_status_warnings.jpg" width="320" alt="wifi_status_warnings on a TV"> |
| `top_bar_apps` | <img src="switches/top_bar_apps.jpg" width="320" alt="top_bar_apps on a TV"> |
| `show_greeting` | <img src="switches/show_greeting.jpg" width="320" alt="show_greeting on a TV"> |
| `header_white` | <img src="switches/header_white.jpg" width="320" alt="header_white on a TV"> |
| `clock_wallpaper` | <img src="switches/clock_wallpaper.jpg" width="320" alt="clock_wallpaper on a TV"> |
| `clock_wallpaper_dim` | <img src="switches/clock_wallpaper.jpg" width="320" alt="clock_wallpaper_dim on a TV"> |
| `clock_glow` | <img src="switches/clock_glow.jpg" width="320" alt="clock_glow on a TV"> |
| `clock_slideshow_minutes` | <img src="switches/clock_wallpaper.jpg" width="320" alt="clock_slideshow_minutes on a TV"> |
| `open_on_start_choice` | <img src="switches/open_on_start_choice.jpg" width="320" alt="open_on_start_choice on a TV"> |
| `single_row` | <img src="switches/single_row.jpg" width="320" alt="single_row on a TV"> |
| `dock_band_all` | <img src="switches/single_row.jpg" width="320" alt="dock_band_all on a TV"> |
| `uniform_tiles` | <img src="switches/uniform_tiles.jpg" width="320" alt="uniform_tiles on a TV"> |
| `home_hook` | Fire TV Stick 4K Max (Fire OS 8.1.8.5): the launcher's Home a moment after the remote's HOME button, which Fire OS had routed to Amazon's home screen; HomeHookService covered it and started this launcher over it | <img src="switches/home_hook.jpg" width="320" alt="home_hook"> |
| `settings_menu_on_click` | Fire TV Stick 4K Max: OK on the Settings pill drops the launcher's menu, TV settings as its second row | <img src="switches/settings_menu_on_click.jpg" width="320" alt="settings_menu_on_click"> |
| `weather_provider` | Fire TV Stick 4K Max with the weather add-on installed: its "12° · Cloudy" line under the date, read from the add-on's content provider | <img src="switches/weather_provider.jpg" width="320" alt="weather_provider"> |
| `news_provider` | Fire TV Stick 4K Max with the news add-on installed: one BBC headline under the clock, the next every 20 seconds | <img src="switches/news_provider.jpg" width="320" alt="news_provider"> |
| `news_card` | Fire TV Stick 4K Max with the news add-on: the story picture with the source and headline beside it under the clock | <img src="switches/news_card.jpg" width="320" alt="news_card"> |
| `news_seconds` | Fire TV Stick 4K Max: the same story card, the next story every 10 seconds instead of 20 | <img src="switches/news_card.jpg" width="320" alt="news_seconds"> |
| `news_stories` | Fire TV Stick 4K Max: the story card cycling six stories, six dots | <img src="switches/news_card.jpg" width="320" alt="news_stories"> |
| `news_position` | Fire TV Stick 4K Max: the story card under the Wi-Fi and Settings pills instead of the clock | <img src="switches/news_position.jpg" width="320" alt="news_position"> |
| `wallpaper_motion` | Fire TV Stick 4K Max: the Sweden picture drawn a few percent in, ready to slide against the focused tile | <img src="switches/wallpaper_motion.jpg" width="320" alt="wallpaper_motion"> |
| `tile_shape` | Fire TV Stick 4K Max: `pill` tiles, the dock band and focus glass rounded to match | <img src="switches/tile_shape.jpg" width="320" alt="tile_shape"> |
| `now_playing` | Fire TV Stick 4K Max: "▶ Playing on Stremio" with the title under the weather line while Stremio plays behind Home | <img src="switches/now_playing.jpg" width="320" alt="now_playing"> |
| `notification_line` | Fire TV Stick 4K Max: the latest notification's title under the now-playing line for a minute | <img src="switches/now_playing.jpg" width="320" alt="notification_line"> |
| `now_playing_card` | Fire TV Stick 4K Max: the paused app's tile with the film's artwork and its title | <img src="switches/now_playing_card.jpg" width="320" alt="now_playing_card"> |
| `now_playing_art` | Fire TV Stick 4K Max: the paused film's artwork, soft and dark, behind Home | <img src="switches/now_playing_card.jpg" width="320" alt="now_playing_art"> |
| `header_scrim` | Fire TV Stick 4K Max: the top of the Ruska sky darkened behind the clock and the lines under it | <img src="switches/header_scrim.jpg" width="320" alt="header_scrim"> |
| `header_mood` | Fire TV Stick 4K Max: the weather line in the condition's tint (rain, cool blue) under the clock | <img src="switches/header_mood.jpg" width="320" alt="header_mood"> |
