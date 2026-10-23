package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Where the zooming tile starts and how it travels: the arithmetic behind AppZoom. */
public class AppZoomTest {

    @Test
    public void focusZoomGrowsTheTileAroundItsCentre() {
        // A 180 x 100 tile at (100, 200), focused at 1.1: 9 px wider and 5 px taller on each side.
        assertArrayEquals(new float[]{91f, 195f, 289f, 305f},
                AppZoom.scaledAround(100f, 200f, 280f, 300f, 1.1f), 0.001f);
    }

    @Test
    public void unscaledTileStaysPut() {
        assertArrayEquals(new float[]{10f, 20f, 30f, 40f}, AppZoom.scaledAround(10f, 20f, 30f, 40f, 1f), 0f);
    }

    @Test
    public void cardTakesTheColourRoundThePicture() {
        assertEquals(0xFF804020, AppZoom.averageColor(new int[]{0xFF804020, 0xFF804020}));
        assertEquals(0xFF7F7F7F, AppZoom.averageColor(new int[]{0xFFFFFFFF, 0xFF000000}));
        // A see-through edge shows the black behind Home.
        assertEquals(0xFF000000, AppZoom.averageColor(new int[]{0x00FFFFFF}));
    }

    @Test
    public void travelRunsFromStartToEnd() {
        assertEquals(16f, AppZoom.lerp(16f, 0f, 0f), 0f);
        assertEquals(8f, AppZoom.lerp(16f, 0f, 0.5f), 0f);
        assertEquals(0f, AppZoom.lerp(16f, 0f, 1f), 0f);
    }
}
