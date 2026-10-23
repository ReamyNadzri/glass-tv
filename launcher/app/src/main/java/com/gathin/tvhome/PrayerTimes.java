package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A month of prayer times for one JAKIM zone, as JAKIM's e-Solat API gives it
 * ({@code esolatApi/TakwimSolat&period=month&zone=...}), and the arithmetic the widget needs: which
 * prayer is next, how long until it, how far the wait has gone. Times are minutes after midnight,
 * Malaysia time. No Android classes, so it is tested on the computer.
 */
final class PrayerTimes {

    /** What the widget lists, in order; Syuruk (sunrise) is shown but is not a prayer to count down to. */
    static final String[] NAMES = {"Subuh", "Syuruk", "Zohor", "Asar", "Maghrib", "Isyak"};
    static final int SYURUK = 1;
    private static final String[] KEYS = {"fajr", "syuruk", "dhuhr", "asr", "maghrib", "isha"};
    private static final String[] HIJRI_MONTHS = {"Muharram", "Safar", "Rabiulawal", "Rabiulakhir",
            "Jamadilawal", "Jamadilakhir", "Rejab", "Syaaban", "Ramadan", "Syawal", "Zulkaedah", "Zulhijjah"};

    static final class Day {
        final int dayOfMonth;
        /** {@link #NAMES} order, minutes after midnight. */
        final int[] minutes;
        /** JAKIM's yyyy-MM-dd Hijri date. */
        final String hijri;

        Day(int dayOfMonth, int[] minutes, String hijri) {
            this.dayOfMonth = dayOfMonth;
            this.minutes = minutes;
            this.hijri = hijri;
        }
    }

    final String zone;
    final List<Day> days;

    PrayerTimes(String zone, List<Day> days) {
        this.zone = zone;
        this.days = Collections.unmodifiableList(days);
    }

    /** Parses JAKIM's answer; null when it is not one, so a bad download never replaces a good month. */
    static PrayerTimes parse(String json) {
        try {
            JSONObject o = new JSONObject(json);
            if (!o.optString("status").startsWith("OK")) return null;
            JSONArray list = o.getJSONArray("prayerTime");
            List<Day> days = new ArrayList<>();
            for (int i = 0; i < list.length(); i++) {
                JSONObject d = list.getJSONObject(i);
                String date = d.optString("date");   // 22-Okt-2026
                int dash = date.indexOf('-');
                if (dash <= 0) return null;
                int[] m = new int[KEYS.length];
                for (int k = 0; k < KEYS.length; k++) {
                    m[k] = minutes(d.optString(KEYS[k]));
                    if (m[k] < 0) return null;
                }
                days.add(new Day(Integer.parseInt(date.substring(0, dash)), m, d.optString("hijri")));
            }
            return days.isEmpty() ? null : new PrayerTimes(o.optString("zone"), days);
        } catch (Exception e) {
            return null;
        }
    }

    /** "05:49:00" to 349; -1 when it is not a time. */
    static int minutes(String hhmmss) {
        if (hhmmss == null || hhmmss.length() < 5 || hhmmss.charAt(2) != ':') return -1;
        try {
            int h = Integer.parseInt(hhmmss.substring(0, 2)), m = Integer.parseInt(hhmmss.substring(3, 5));
            return h < 24 && m < 60 ? h * 60 + m : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    Day day(int dayOfMonth) {
        for (Day d : days) if (d.dayOfMonth == dayOfMonth) return d;
        return null;
    }

    /** The next prayer from {@code now}: its index in {@link #NAMES}, and when, in minutes from today's midnight. */
    static final class Next {
        final int index;
        /** May be past 1440: tomorrow's Subuh after Isyak. */
        final int at;
        /** The time the wait started (the last listed time, Syuruk included), for the progress bar. */
        final int since;

        Next(int index, int at, int since) {
            this.index = index;
            this.at = at;
            this.since = since;
        }

        float progress(float now) {
            return at <= since ? 1f : Math.max(0f, Math.min(1f, (now - since) / (at - since)));
        }
    }

    /**
     * The next prayer after {@code now} (minutes, with seconds as a fraction). After Isyak it is
     * tomorrow's Subuh, from {@code tomorrow} when known, else today's time a day later.
     */
    static Next next(Day today, Day tomorrow, float now) {
        int[] m = today.minutes;
        for (int i = 0; i < m.length; i++) {
            if (i == SYURUK || m[i] <= now) continue;
            int since = -1;
            for (int k = 0; k < m.length; k++) if (m[k] <= now) since = m[k];
            if (since < 0) since = m[m.length - 1] - 1440;   // before Subuh: since last night's Isyak
            return new Next(i, m[i], since);
        }
        int subuh = (tomorrow != null ? tomorrow.minutes[0] : m[0]) + 1440;
        return new Next(0, subuh, m[m.length - 1]);
    }

    /** "in 1 h 12 min", "in 5 min", "now". */
    static String until(float minutes) {
        int left = (int) Math.ceil(minutes);
        if (left < 1) return "now";
        int h = left / 60, mm = left % 60;
        return h > 0 ? "in " + h + " h " + mm + " min" : "in " + mm + " min";
    }

    /** 1001 to "4:41 PM", or "16:41" on a 24-hour clock. */
    static String clock(int minutes, boolean twentyFour) {
        minutes = ((minutes % 1440) + 1440) % 1440;
        int h = minutes / 60, m = minutes % 60;
        if (twentyFour) return String.format(java.util.Locale.ROOT, "%02d:%02d", h, m);
        return (h % 12 == 0 ? 12 : h % 12) + ":" + (m < 10 ? "0" : "") + m + (h < 12 ? " AM" : " PM");
    }

    /** "1448-05-11" to "11 Jamadilawal 1448"; empty when JAKIM sent something else. */
    static String hijri(String ymd) {
        if (ymd == null) return "";
        String[] p = ymd.split("-");
        try {
            int month = Integer.parseInt(p[1]), day = Integer.parseInt(p[2]);
            if (p.length != 3 || month < 1 || month > 12) return "";
            return day + " " + HIJRI_MONTHS[month - 1] + " " + p[0];
        } catch (RuntimeException e) {
            return "";
        }
    }
}
