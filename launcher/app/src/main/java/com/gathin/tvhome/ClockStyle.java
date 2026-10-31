package com.gathin.tvhome;

import android.widget.TextClock;

/** The Clock rows of Launcher settings applied to Home's time and to the settings preview (Config.CLOCK_*_KEY). */
final class ClockStyle {
    private ClockStyle() {}

    /** weight 0 thin, 2 bold, else light (the layout's own); hours 12 or 24 forced, else the TV's; seconds appended. */
    static void apply(TextClock clock, int weight, int hours, boolean seconds) {
        clock.setTypeface(UiFont.get(clock.getContext(), weight == 0 ? R.string.font_extralight
                : weight == 2 ? R.string.font_medium : R.string.font_light));
        String h12 = seconds ? "h:mm:ss" : "h:mm", h24 = seconds ? "HH:mm:ss" : "HH:mm";
        clock.setFormat12Hour(hours == 24 ? h24 : h12);
        clock.setFormat24Hour(hours == 12 ? h12 : h24);
    }
}
