package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * What the owner chose in Glass TV's own settings, the glass panel behind the tile at the end of the
 * apps. These win over the theme's config.json (see {@link Config}), which wins over config.xml, so a
 * family can change them from the remote without a computer. Each choice is one of a short list,
 * stepped through with Left and Right.
 */
final class HomePrefs {
    private static final String FILE = "glass_settings";

    static final String[] ZOOMS = {"card", "glass", "lift", "stretch", "off"};
    /** frame: the selection's glass shows round the app; subtle: it hides behind it, as Glass TV first had it. */
    static final String[] SELECTIONS = {"frame", "subtle"};
    static final String[] SPEEDS = {"slow", "normal", "fast"};
    static final String[] PRAYER_STYLES = {"card", "pill", "strip", "off"};
    static final int[] REMINDERS = {10, 15, 5, 0};
    static final String AUTO_ZONE = "auto";

    private final SharedPreferences prefs;

    HomePrefs(Context ctx) {
        prefs = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    String zoom() {
        return oneOf(prefs.getString("zoom", ZOOMS[0]), ZOOMS);
    }

    /** How long the zoom takes compared with normal: slower is longer. */
    float zoomTime() {
        switch (oneOf(prefs.getString("zoom_speed", "normal"), SPEEDS)) {
            case "slow": return 1.5f;
            case "fast": return 0.7f;
            default: return 1f;
        }
    }

    String zoomSpeed() {
        return oneOf(prefs.getString("zoom_speed", "normal"), SPEEDS);
    }

    String selection() {
        return oneOf(prefs.getString("selection", SELECTIONS[0]), SELECTIONS);
    }

    String prayerStyle() {
        return oneOf(prefs.getString("prayer", PRAYER_STYLES[0]), PRAYER_STYLES);
    }

    /** A JAKIM zone code, or {@link #AUTO_ZONE} to work it out from the TV's location. */
    String prayerZone() {
        return prefs.getString("prayer_zone", AUTO_ZONE);
    }

    /** Minutes before each prayer that Home shows a reminder; 0 for none. */
    int reminderMinutes() {
        int m = prefs.getInt("prayer_reminder", REMINDERS[0]);
        for (int r : REMINDERS) if (r == m) return m;
        return REMINDERS[0];
    }

    /** The owner's choice for calm motion, or null when they left it to the theme. */
    Boolean reducedMotion() {
        return prefs.contains("reduced_motion") ? prefs.getBoolean("reduced_motion", false) : null;
    }

    /** The owner's choice for the Wi-Fi button, or null when they left it to the theme. */
    Boolean showNetworkPill() {
        return prefs.contains("show_network_pill") ? prefs.getBoolean("show_network_pill", true) : null;
    }

    void put(String key, String value) {
        prefs.edit().putString(key, value).apply();
    }

    void put(String key, int value) {
        prefs.edit().putInt(key, value).apply();
    }

    void put(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
    }

    /** The next (or previous) value in {@code list}, wrapping round. */
    static String step(String[] list, String now, int by) {
        int i = 0;
        for (int k = 0; k < list.length; k++) if (list[k].equals(now)) i = k;
        return list[((i + by) % list.length + list.length) % list.length];
    }

    static int step(int[] list, int now, int by) {
        int i = 0;
        for (int k = 0; k < list.length; k++) if (list[k] == now) i = k;
        return list[((i + by) % list.length + list.length) % list.length];
    }

    private static String oneOf(String v, String[] list) {
        for (String s : list) if (s.equals(v)) return v;
        return list[0];
    }
}
