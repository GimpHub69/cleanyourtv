package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The dock band's corners are concentric with its tiles' corners. */
public class DockCornersTest {
    private static final float DENSITY = 2f;   // 1080p TV, 320 dpi
    /** activity_main.xml: 56dp root padding and 10dp grid padding on each side of a 1920 px screen. */
    private static final int INNER = 1920 - 2 * 56 * 2 - 2 * 10 * 2;

    @Test public void bandRadiusIsTileRadiusPlusPadding() {
        assertEquals(77f, DockDecoration.radiusPx(25f, 52f), 0f);
    }

    @Test public void evenPaddingIsTheGapBetweenTilesUnlessTheEdgeIsTighter() {
        assertEquals(50f, DockDecoration.evenPaddingPx(1000, 4, 200, 80f), 0.001f);
        assertEquals(30f, DockDecoration.evenPaddingPx(1000, 4, 200, 30f), 0.001f);
        assertEquals(0f, DockDecoration.evenPaddingPx(1000, 0, 200, 30f), 0.001f);
    }

    @Test public void evenPaddingStaysPositiveAndBelowTheCellFromFourToSevenColumns() {
        for (int columns = 4; columns <= 7; columns++) {
            int tile = AppAdapter.tileWidthPx(INNER, columns, DENSITY);
            float gap = INNER / (float) columns - tile;
            float pad = DockDecoration.evenPaddingPx(INNER, columns, tile, Float.MAX_VALUE);
            assertEquals("columns " + columns, gap, pad, 0.001f);
            float radius = DockDecoration.radiusPx(AppAdapter.cornerRadius(tile, tile * 101 / 180, DENSITY), pad);
            assertEquals("columns " + columns, pad, radius - AppAdapter.cornerRadius(tile, tile * 101 / 180, DENSITY), 0.001f);
        }
    }

    /** tile_shape: square tiles keep a 3dp corner, pills take half the shorter side, rounded stays 16dp. */
    @Test
    public void shapesChangeTheRadius() {
        try {
            AppAdapter.tileShape = "square";
            assertEquals(3f * DENSITY, AppAdapter.cornerRadius((int) (180 * DENSITY), (int) (101 * DENSITY), DENSITY), 0.001f);
            AppAdapter.tileShape = "pill";
            assertEquals(101 * DENSITY / 2f, AppAdapter.cornerRadius((int) (180 * DENSITY), (int) (101 * DENSITY), DENSITY), 0.001f);
            AppAdapter.tileShape = "rounded";
            assertEquals(16f * DENSITY, AppAdapter.cornerRadius((int) (180 * DENSITY), (int) (101 * DENSITY), DENSITY), 0.001f);
        } finally {
            AppAdapter.tileShape = "rounded";
        }
    }
}
