package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Current weather for one place from Open-Meteo (free, no key). One small request at most every
 * REFRESH_MS; the last answer is kept in preferences so Home shows it at once, even offline.
 */
final class Weather {

    static final long REFRESH_MS = 30 * 60_000L;
    /** Older than this and the reading is not shown at all. */
    static final long STALE_MS = 6 * 60 * 60_000L;

    static final class Now {
        final int temp, high, low, code;
        final boolean day;
        final long at;
        /** Local hour (0-23) of the first likely rain in the next 6 hours, or -1. */
        final int rainHour;

        Now(int temp, int high, int low, int code, boolean day, long at, int rainHour) {
            this.rainHour = rainHour;
            this.temp = temp;
            this.high = high;
            this.low = low;
            this.code = code;
            this.day = day;
            this.at = at;
        }
    }

    interface Listener {
        void onWeather(Now now);
    }

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private final SharedPreferences prefs;
    private final String lat, lon, unit;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean inFlight;

    Weather(Context ctx, String lat, String lon, String unit) {
        this.prefs = ctx.getSharedPreferences("weather", Context.MODE_PRIVATE);
        this.lat = lat;
        this.lon = lon;
        this.unit = "fahrenheit".equalsIgnoreCase(unit) ? "fahrenheit" : "celsius";
    }

    /** The cached reading, or null when there is none or it is too old to show. */
    Now cached() {
        long at = prefs.getLong("at", 0);
        if (at == 0 || System.currentTimeMillis() - at > STALE_MS) return null;
        if (!(lat + "," + lon + "," + unit).equals(prefs.getString("for", ""))) return null;
        return new Now(prefs.getInt("temp", 0), prefs.getInt("high", 0), prefs.getInt("low", 0),
                prefs.getInt("code", 0), prefs.getBoolean("day", true), at, prefs.getInt("rainHour", -1));
    }

    /** Fetches in the background when the cached reading is older than REFRESH_MS. */
    void refreshIfDue(Listener listener) {
        Now c = cached();
        if (inFlight || (c != null && System.currentTimeMillis() - c.at < REFRESH_MS)) return;
        inFlight = true;
        IO.execute(() -> {
            Now fresh = null;
            try {
                fresh = fetch();
            } catch (Exception ignored) {
                // Offline or the service is down: keep what we have and try again next time.
            }
            final Now result = fresh;
            main.post(() -> {
                inFlight = false;
                if (result != null) listener.onWeather(result);
            });
        });
    }

    private Now fetch() throws Exception {
        URL url = new URL(String.format(Locale.ROOT,
                "https://api.open-meteo.com/v1/forecast?latitude=%s&longitude=%s"
                        + "&current=temperature_2m,weather_code,is_day"
                        + "&daily=temperature_2m_max,temperature_2m_min&forecast_days=1"
                        + "&hourly=precipitation_probability,weather_code&forecast_hours=7"
                        + "&timezone=auto&temperature_unit=%s", lat, lon, unit));
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            JSONObject json = new JSONObject(out.toString("UTF-8"));
            JSONObject cur = json.getJSONObject("current");
            JSONObject daily = json.getJSONObject("daily");
            // The first coming hour with rain likely (60% or a rain code), within 6 hours.
            int rainHour = -1;
            JSONObject hourly = json.optJSONObject("hourly");
            if (hourly != null) {
                org.json.JSONArray times = hourly.getJSONArray("time");
                org.json.JSONArray prob = hourly.getJSONArray("precipitation_probability");
                org.json.JSONArray codes = hourly.getJSONArray("weather_code");
                for (int i = 1; i < times.length(); i++) {
                    if (prob.optInt(i, 0) >= 60 || isRain(codes.optInt(i, 0))) {
                        String t = times.getString(i);   // local time, "2026-10-19T16:00"
                        rainHour = Integer.parseInt(t.substring(t.indexOf('T') + 1, t.indexOf('T') + 3));
                        break;
                    }
                }
            }
            Now now = new Now(
                    (int) Math.round(cur.getDouble("temperature_2m")),
                    (int) Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)),
                    (int) Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)),
                    cur.getInt("weather_code"),
                    cur.optInt("is_day", 1) == 1,
                    System.currentTimeMillis(), rainHour);
            prefs.edit().putLong("at", now.at).putInt("temp", now.temp).putInt("high", now.high)
                    .putInt("low", now.low).putInt("code", now.code).putBoolean("day", now.day)
                    .putInt("rainHour", now.rainHour).putString("for", lat + "," + lon + "," + unit).apply();
            return now;
        } finally {
            c.disconnect();
        }
    }

    static boolean isRain(int code) {
        return code >= 51 && code <= 67 || code >= 80 && code <= 82 || code >= 95;
    }

    /** WMO weather code to a condition name. */
    static int label(int code) {
        if (code == 0) return R.string.wx_clear;
        if (code == 1) return R.string.wx_mostly_clear;
        if (code == 2) return R.string.wx_partly_cloudy;
        if (code == 3) return R.string.wx_cloudy;
        if (code == 45 || code == 48) return R.string.wx_fog;
        if (code >= 51 && code <= 57) return R.string.wx_drizzle;
        if (code >= 61 && code <= 67) return R.string.wx_rain;
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return R.string.wx_snow;
        if (code >= 80 && code <= 82) return R.string.wx_showers;
        if (code >= 95) return R.string.wx_thunder;
        return R.string.wx_cloudy;
    }

    static int icon(int code, boolean day) {
        if (code == 0) return day ? R.drawable.wx_clear_day : R.drawable.wx_clear_night;
        if (code == 1 || code == 2) return day ? R.drawable.wx_partly_day : R.drawable.wx_partly_night;
        if (code == 45 || code == 48) return R.drawable.wx_fog;
        if (code >= 51 && code <= 67 || code >= 80 && code <= 82) return R.drawable.wx_rain;
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return R.drawable.wx_snow;
        if (code >= 95) return R.drawable.wx_thunder;
        return R.drawable.wx_cloudy;
    }
}
