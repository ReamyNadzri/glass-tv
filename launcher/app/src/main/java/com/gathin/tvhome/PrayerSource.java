package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Where the waktu solat widget gets its times. JAKIM's e-Solat API gives a month for a zone; the month
 * is kept on the TV, so the widget goes online once a month (and when the zone changes) and keeps
 * working when the internet drops. The zone is the one picked in Glass TV's settings, or, on Auto,
 * worked out once a month from the TV's location: Android's own when the TV has one, otherwise an
 * approximate one from the internet connection (ipwho.is), turned into a JAKIM zone by
 * api.waktusolat.app. All network work runs on its own thread, never on Home's.
 */
final class PrayerSource {
    private static final String TAG = "TvHome.Prayer";
    static final TimeZone MALAYSIA = TimeZone.getTimeZone("Asia/Kuala_Lumpur");
    private static final long AUTO_ZONE_MS = 30L * 24 * 3600 * 1000;
    /** After a failed download, wait this long before trying again. */
    private static final long RETRY_MS = 30L * 60 * 1000;

    interface Listener {
        void onPrayerTimes();
    }

    static final class Zone {
        final String code, state, area;

        Zone(String code, String state, String area) {
            this.code = code;
            this.state = state;
            this.area = area;
        }

        /** The first place JAKIM names for the zone: "Kota Bharu" from "Kota Bharu, Bachok, ...". */
        String place() {
            int comma = area.indexOf(',');
            return (comma > 0 ? area.substring(0, comma) : area).trim();
        }
    }

    private final Context ctx;
    private final Handler main;
    private final HomePrefs prefs;
    private final SharedPreferences cache;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private List<Zone> zones;

    /** What the widget shows; read and written on the main thread only. */
    private PrayerTimes times;
    private String timesKey;
    private boolean busy;
    private long failedAt, autoTriedAt;
    /** Auto could not find the zone: the widget asks the owner to pick one. */
    private boolean zoneUnknown;

    PrayerSource(Context ctx, Handler main) {
        this.ctx = ctx.getApplicationContext();
        this.main = main;
        this.prefs = new HomePrefs(ctx);
        this.cache = ctx.getSharedPreferences("prayer_cache", Context.MODE_PRIVATE);
    }

    PrayerTimes times() {
        return times;
    }

    boolean zoneUnknown() {
        return zoneUnknown;
    }

    /** The zone in use: the one picked, or Auto's last answer; null while Auto has none yet. */
    String zone() {
        String z = prefs.prayerZone();
        return HomePrefs.AUTO_ZONE.equals(z) ? cache.getString("auto_zone", null) : z;
    }

    /**
     * Makes sure this month's times for the zone in use are loaded: from the TV when they are there,
     * else from JAKIM. Cheap when nothing is due, so it is called on every resume and every minute.
     */
    void refresh(Listener done) {
        if (busy) return;
        final boolean auto = HomePrefs.AUTO_ZONE.equals(prefs.prayerZone());
        final String zone = zone();
        long now = System.currentTimeMillis();
        final boolean autoDue = auto && now - autoTriedAt > RETRY_MS
                && (zone == null || now - cache.getLong("auto_zone_at", 0) > AUTO_ZONE_MS);
        if (autoDue) autoTriedAt = now;
        final String month = monthKey(Calendar.getInstance(MALAYSIA));
        if (!autoDue && zone != null && (zone + month).equals(timesKey)) return;
        if (System.currentTimeMillis() - failedAt < RETRY_MS) return;
        busy = true;
        worker.execute(() -> {
            String z = zone;
            if (autoDue) {
                String found = detectZone();
                if (found != null) {
                    cache.edit().putString("auto_zone", found).putLong("auto_zone_at", System.currentTimeMillis()).apply();
                    z = found;
                }
            }
            PrayerTimes loaded = z == null ? null : load(z, month);
            final String finalZone = z;
            final PrayerTimes result = loaded;
            main.post(() -> {
                busy = false;
                zoneUnknown = finalZone == null;
                if (result != null) {
                    times = result;
                    timesKey = finalZone + month;
                } else {
                    failedAt = System.currentTimeMillis();
                }
                done.onPrayerTimes();
            });
        });
    }

    /** The zone was changed in the settings: forget the loaded month so the next refresh loads the new one. */
    void zoneChanged() {
        timesKey = null;
        times = null;
        failedAt = 0;
        autoTriedAt = 0;
        zoneUnknown = false;
    }

    void shutdown() {
        worker.shutdownNow();
    }

    /** This month's times for the zone, from the TV or else from JAKIM (kept for next time). */
    private PrayerTimes load(String zone, String month) {
        File dir = new File(ctx.getFilesDir(), "prayer");
        File file = new File(dir, zone + "-" + month + ".json");
        if (file.isFile()) {
            PrayerTimes kept = PrayerTimes.parse(read(file));
            if (kept != null) return kept;
        }
        String json = get("https://www.e-solat.gov.my/index.php?r=esolatApi/TakwimSolat&period=month&zone=" + zone);
        PrayerTimes fresh = json == null ? null : PrayerTimes.parse(json);
        if (fresh == null) return null;
        // One month per zone on the TV: last month's files go.
        File[] old = dir.listFiles();
        if (old != null) for (File f : old) f.delete();
        if (dir.isDirectory() || dir.mkdirs()) {
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                Log.w(TAG, "could not keep the month: " + e.getMessage());
            }
        }
        return fresh;
    }

    /** The JAKIM zone the TV is in, or null. */
    private String detectZone() {
        double[] at = androidLocation();
        if (at == null) at = internetLocation();
        if (at == null) return null;
        String json = get(String.format(Locale.ROOT, "https://api.waktusolat.app/zones/%.4f/%.4f", at[0], at[1]));
        if (json == null) return null;
        try {
            String zone = new JSONObject(json).optString("zone", "");
            return zone.matches("[A-Z]{3}\\d{2}") ? zone : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** The TV's last known position, when Android has one (most TVs do not). */
    private double[] androidLocation() {
        try {
            LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return null;
            for (String p : new String[]{LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER,
                    LocationManager.GPS_PROVIDER}) {
                Location l = lm.getLastKnownLocation(p);
                if (l != null) return new double[]{l.getLatitude(), l.getLongitude()};
            }
        } catch (RuntimeException e) {   // no permission, or no such provider
            Log.i(TAG, "no Android location: " + e.getMessage());
        }
        return null;
    }

    /** Roughly where the internet connection comes out: the city, from the public address. */
    private double[] internetLocation() {
        String json = get("https://ipwho.is/?fields=success,latitude,longitude");
        if (json == null) return null;
        try {
            JSONObject o = new JSONObject(json);
            if (!o.optBoolean("success")) return null;
            return new double[]{o.getDouble("latitude"), o.getDouble("longitude")};
        } catch (Exception e) {
            return null;
        }
    }

    private static String get(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(10_000);
            c.setReadTimeout(15_000);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", "GlassTV");
            if (c.getResponseCode() != 200) return null;
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0 && out.size() < 512 * 1024) out.write(buf, 0, n);
                return out.toString("UTF-8");
            }
        } catch (Exception e) {
            // The host only: the path can carry the TV's position.
            Log.i(TAG, "could not reach " + host(url) + ": " + e.getMessage());
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String host(String url) {
        try {
            return new URL(url).getHost();
        } catch (Exception e) {
            return "?";
        }
    }

    private static String read(File f) {
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    static String monthKey(Calendar c) {
        return String.format(Locale.ROOT, "%04d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1);
    }

    /** JAKIM's 60 zones (res/raw/jakim_zones.json), in JAKIM's order. */
    List<Zone> zones() {
        if (zones != null) return zones;
        List<Zone> list = new ArrayList<>();
        try (InputStream in = ctx.getResources().openRawResource(R.raw.jakim_zones)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            JSONArray a = new JSONArray(out.toString("UTF-8"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                list.add(new Zone(o.getString("code"), o.getString("state"), o.getString("area")));
            }
        } catch (Exception e) {
            Log.w(TAG, "zone list unreadable: " + e.getMessage());
        }
        zones = Collections.unmodifiableList(list);
        return zones;
    }

    Zone zoneInfo(String code) {
        if (code == null) return null;
        for (Zone z : zones()) if (z.code.equals(code)) return z;
        return null;
    }
}
