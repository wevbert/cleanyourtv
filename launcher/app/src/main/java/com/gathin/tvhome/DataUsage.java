package com.gathin.tvhome;

import android.app.AppOpsManager;
import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.os.Process;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Wi-Fi data used today, this week and this month, and the apps that used the most this month, from
 * Android's own network statistics. Reading them needs usage access (granted over ADB at install:
 * appops set com.gathin.tvhome GET_USAGE_STATS allow). Only read when the data panel opens.
 */
final class DataUsage {

    static final class App {
        final String label;
        final Drawable icon;
        final long bytes;

        App(String label, Drawable icon, long bytes) {
            this.label = label;
            this.icon = icon;
            this.bytes = bytes;
        }
    }

    /** Icons and names by package, loaded once: a fresh icon per open is ~400 KB that only GC frees. */
    private static final Map<String, App> LOOKS = new HashMap<>();

    final long today, week, month;
    final List<App> top;

    private DataUsage(long today, long week, long month, List<App> top) {
        this.today = today;
        this.week = week;
        this.month = month;
        this.top = top;
    }

    static boolean hasAccess(Context ctx) {
        AppOpsManager ops = ctx.getSystemService(AppOpsManager.class);
        return ops != null && ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), ctx.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }

    /** Reads the statistics; slow enough (tens of ms) to keep off the main thread. Null without access. */
    static DataUsage read(Context ctx, int topCount) {
        if (!hasAccess(ctx)) return null;
        NetworkStatsManager nsm = ctx.getSystemService(NetworkStatsManager.class);
        if (nsm == null) return null;
        long now = System.currentTimeMillis();
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long dayStart = c.getTimeInMillis();
        Calendar w = (Calendar) c.clone();
        // Weeks start on Monday.
        int back = (w.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        w.add(Calendar.DAY_OF_MONTH, -back);
        long weekStart = w.getTimeInMillis();
        Calendar m = (Calendar) c.clone();
        m.set(Calendar.DAY_OF_MONTH, 1);
        long monthStart = m.getTimeInMillis();
        try {
            long today = device(nsm, dayStart, now), week = device(nsm, weekStart, now), month = device(nsm, monthStart, now);
            return new DataUsage(today, week, month, topApps(ctx, nsm, monthStart, now, topCount));
        } catch (Exception e) {
            return null;
        }
    }

    private static long startOfToday() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** Wi-Fi data this month, for the line on Home; -1 without access. One cheap device query. */
    static long month(Context ctx) {
        if (!hasAccess(ctx)) return -1;
        NetworkStatsManager nsm = ctx.getSystemService(NetworkStatsManager.class);
        if (nsm == null) return -1;
        Calendar m = Calendar.getInstance();
        m.setTimeInMillis(startOfToday());
        m.set(Calendar.DAY_OF_MONTH, 1);
        try {
            return device(nsm, m.getTimeInMillis(), System.currentTimeMillis());
        } catch (Exception e) {
            return -1;
        }
    }

    /** The last 7 days (oldest first): each day's total and the share of the week's top app. */
    static final class Week {
        final long[] total = new long[7], top = new long[7];
        final long[] start = new long[7];
        String topLabel;
    }

    static Week week(Context ctx) {
        if (!hasAccess(ctx)) return null;
        NetworkStatsManager nsm = ctx.getSystemService(NetworkStatsManager.class);
        if (nsm == null) return null;
        Week w = new Week();
        long today = startOfToday(), now = System.currentTimeMillis();
        List<Map<Integer, Long>> days = new ArrayList<>();
        Map<Integer, Long> sum = new HashMap<>();
        try {
            for (int d = 0; d < 7; d++) {
                Calendar c = Calendar.getInstance();
                c.setTimeInMillis(today);
                c.add(Calendar.DAY_OF_MONTH, d - 6);
                long s = c.getTimeInMillis();
                c.add(Calendar.DAY_OF_MONTH, 1);
                long e = Math.min(c.getTimeInMillis(), now);
                w.start[d] = s;
                Map<Integer, Long> byUid = new HashMap<>();
                try (NetworkStats stats = nsm.querySummary(ConnectivityManager.TYPE_WIFI, null, s, e)) {
                    NetworkStats.Bucket b = new NetworkStats.Bucket();
                    while (stats.hasNextBucket()) {
                        stats.getNextBucket(b);
                        long n = b.getRxBytes() + b.getTxBytes();
                        byUid.merge(b.getUid(), n, Long::sum);
                        sum.merge(b.getUid(), n, Long::sum);
                        w.total[d] += n;
                    }
                }
                days.add(byUid);
            }
        } catch (Exception e) {
            return null;
        }
        int topUid = -1;
        long best = 0;
        for (Map.Entry<Integer, Long> e : sum.entrySet()) {
            if (e.getKey() >= Process.FIRST_APPLICATION_UID && e.getValue() > best) {
                best = e.getValue();
                topUid = e.getKey();
            }
        }
        if (topUid >= 0) {
            String[] pkgs = ctx.getPackageManager().getPackagesForUid(topUid);
            w.topLabel = pkgs != null && pkgs.length > 0 ? look(ctx.getPackageManager(), pkgs[0]).label : null;
            for (int d = 0; d < 7; d++) w.top[d] = days.get(d).getOrDefault(topUid, 0L);
        }
        return w;
    }

    private static long device(NetworkStatsManager nsm, long start, long end) throws Exception {
        NetworkStats.Bucket b = nsm.querySummaryForDevice(ConnectivityManager.TYPE_WIFI, null, start, end);
        return b == null ? 0 : b.getRxBytes() + b.getTxBytes();
    }

    private static List<App> topApps(Context ctx, NetworkStatsManager nsm, long start, long end, int count) throws Exception {
        Map<Integer, Long> byUid = new HashMap<>();
        try (NetworkStats stats = nsm.querySummary(ConnectivityManager.TYPE_WIFI, null, start, end)) {
            NetworkStats.Bucket b = new NetworkStats.Bucket();
            while (stats.hasNextBucket()) {
                stats.getNextBucket(b);
                int uid = b.getUid();
                // Android's own processes count as one line.
                if (uid < Process.FIRST_APPLICATION_UID) uid = Process.SYSTEM_UID;
                byUid.merge(uid, b.getRxBytes() + b.getTxBytes(), Long::sum);
            }
        }
        List<Map.Entry<Integer, Long>> sorted = new ArrayList<>(byUid.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        PackageManager pm = ctx.getPackageManager();
        List<App> apps = new ArrayList<>();
        for (Map.Entry<Integer, Long> e : sorted) {
            if (apps.size() >= count || e.getValue() < 1_000_000L) break;   // nothing under 1 MB
            int uid = e.getKey();
            String label = ctx.getString(R.string.data_system);
            Drawable icon = null;
            String[] pkgs = uid == Process.SYSTEM_UID ? null : pm.getPackagesForUid(uid);
            if (pkgs != null && pkgs.length > 0) {
                App look = look(pm, pkgs[0]);
                label = look.label;
                icon = look.icon;
            }
            apps.add(new App(label, icon, e.getValue()));
        }
        return apps;
    }

    private static synchronized App look(PackageManager pm, String pkg) {
        App a = LOOKS.get(pkg);
        if (a != null) return a;
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            a = new App(String.valueOf(pm.getApplicationLabel(ai)), pm.getApplicationIcon(ai), 0);
        } catch (PackageManager.NameNotFoundException e) {
            a = new App(pkg, null, 0);
        }
        LOOKS.put(pkg, a);
        return a;
    }

    /** "37.4 GB", "820 MB", "4 MB". */
    static String format(long bytes) {
        double gb = bytes / 1e9;
        if (gb >= 10) return String.format(Locale.getDefault(), "%.0f GB", gb);
        if (gb >= 1) return String.format(Locale.getDefault(), "%.1f GB", gb);
        return String.format(Locale.getDefault(), "%.0f MB", bytes / 1e6);
    }
}
