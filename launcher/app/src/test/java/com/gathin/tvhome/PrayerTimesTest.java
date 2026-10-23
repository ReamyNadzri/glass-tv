package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** JAKIM's month of prayer times, and which prayer comes next. */
public class PrayerTimesTest {

    /** Two days of JAKIM's answer for WLY01, as e-Solat sent it on 22 October 2026. */
    private static final String MONTH = "{\"prayerTime\":["
            + "{\"hijri\":\"1448-05-11\",\"date\":\"22-Okt-2026\",\"day\":\"Thursday\",\"imsak\":\"05:39:00\",\"fajr\":\"05:49:00\","
            + "\"syuruk\":\"06:56:00\",\"dhuha\":\"07:21:00\",\"dhuhr\":\"13:01:00\",\"asr\":\"16:18:00\",\"maghrib\":\"19:00:00\",\"isha\":\"20:10:00\"},"
            + "{\"hijri\":\"1448-05-12\",\"date\":\"23-Okt-2026\",\"day\":\"Friday\",\"imsak\":\"05:38:00\",\"fajr\":\"05:48:00\","
            + "\"syuruk\":\"06:56:00\",\"dhuha\":\"07:21:00\",\"dhuhr\":\"13:00:00\",\"asr\":\"16:18:00\",\"maghrib\":\"19:00:00\",\"isha\":\"20:09:00\"}"
            + "],\"status\":\"OK!\",\"zone\":\"WLY01\"}";

    private static PrayerTimes month() {
        PrayerTimes t = PrayerTimes.parse(MONTH);
        assertNotNull(t);
        return t;
    }

    @Test
    public void readsJakimsMonth() {
        PrayerTimes t = month();
        assertEquals("WLY01", t.zone);
        assertEquals(2, t.days.size());
        assertArrayEquals(new int[]{349, 416, 781, 978, 1140, 1210}, t.day(22).minutes);
        assertEquals("11 Jamadilawal 1448", PrayerTimes.hijri(t.day(22).hijri));
        assertNull(t.day(24));
    }

    @Test
    public void refusesWhatIsNotAMonth() {
        assertNull(PrayerTimes.parse("<html>maintenance</html>"));
        assertNull(PrayerTimes.parse("{\"prayerTime\":[],\"status\":\"OK!\"}"));
        assertNull(PrayerTimes.parse("{\"status\":\"NO_RECORD!\"}"));
        assertNull(PrayerTimes.parse(MONTH.replace("\"13:01:00\"", "\"\"")));
    }

    @Test
    public void nextSkipsSyurukAndCountsDown() {
        PrayerTimes t = month();
        PrayerTimes.Next n = PrayerTimes.next(t.day(22), t.day(23), 16 * 60 + 12);   // 4:12 PM
        assertEquals("Asar", PrayerTimes.NAMES[n.index]);
        assertEquals(978, n.at);
        assertEquals("in 6 min", PrayerTimes.until(n.at - (16 * 60 + 12)));
        // Between Subuh and Syuruk the next prayer is Zohor, not sunrise.
        n = PrayerTimes.next(t.day(22), t.day(23), 6 * 60 + 30);
        assertEquals("Zohor", PrayerTimes.NAMES[n.index]);
        assertEquals(349, n.since);
    }

    @Test
    public void afterIsyakItIsTomorrowsSubuh() {
        PrayerTimes t = month();
        PrayerTimes.Next n = PrayerTimes.next(t.day(22), t.day(23), 21 * 60);
        assertEquals("Subuh", PrayerTimes.NAMES[n.index]);
        assertEquals(1440 + 348, n.at);
        assertEquals("in 8 h 48 min", PrayerTimes.until(n.at - 21 * 60));
        // On the last day of the month, today's Subuh stands in for tomorrow's.
        assertEquals(1440 + 349, PrayerTimes.next(t.day(22), null, 21 * 60).at);
    }

    @Test
    public void beforeSubuhTheWaitStartedAtIsyak() {
        PrayerTimes t = month();
        PrayerTimes.Next n = PrayerTimes.next(t.day(22), t.day(23), 60);
        assertEquals("Subuh", PrayerTimes.NAMES[n.index]);
        assertEquals(1210 - 1440, n.since);
        assertEquals(0.5f, n.progress(60), 0.01f);
    }

    @Test
    public void clocksInBothFormats() {
        assertEquals("4:18 PM", PrayerTimes.clock(978, false));
        assertEquals("12:05 AM", PrayerTimes.clock(5, false));
        assertEquals("12:30 PM", PrayerTimes.clock(750, false));
        assertEquals("16:18", PrayerTimes.clock(978, true));
        assertEquals("05:48", PrayerTimes.clock(1440 + 348, true));
    }

    @Test
    public void settingsStepRoundTheList() {
        assertEquals("glass", HomePrefs.step(HomePrefs.ZOOMS, "card", 1));
        assertEquals("off", HomePrefs.step(HomePrefs.ZOOMS, "card", -1));
        assertEquals("card", HomePrefs.step(HomePrefs.ZOOMS, "off", 1));
        assertEquals(0, HomePrefs.step(HomePrefs.REMINDERS, 5, 1));
        assertEquals(10, HomePrefs.step(HomePrefs.REMINDERS, 0, 1));
    }
}
