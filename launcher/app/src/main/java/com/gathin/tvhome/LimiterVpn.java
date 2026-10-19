package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Streaming speed limiter: a VPN that only the capped apps' traffic enters (Netflix, Apple TV, Showmax,
 * DStv) and that never leaves the TV. hev-socks5-tunnel (src/main/jni) turns its packets into SOCKS5
 * connections to Socks5Proxy, which makes the real connections and paces downloads. At 8 Mbps Netflix
 * settles on its top 1080p streams by itself (at 6 it chose 720p).
 *
 * Fail-open: if this service stops, Android takes the VPN down and the apps go back to plain Wi-Fi,
 * uncapped, rather than losing its connection. Started by Android as the always-on VPN, so it is up
 * after a restart; pre-approved over ADB (appops ACTIVATE_VPN), so no consent dialog.
 */
public class LimiterVpn extends VpnService {

    static final String NETFLIX = "com.netflix.ninja";
    /** The streaming apps that pass through the cap. They share it; only one plays at a time. */
    static final String[] CAPPED = {NETFLIX, "com.apple.atve.androidtv.appletv",
            "com.showmax.showmax.google", "com.dstvmobile.android"};
    static final int DEFAULT_MBPS = 8;
    private static final String TAG = "TvHome";

    // Implemented by hev-socks5-tunnel's JNI (registered on this class name, see app/build.gradle).
    private static native boolean TProxyStartService(String configPath, int fd);

    private static native boolean TProxyStopService();

    private static native long[] TProxyGetStats();

    // Registered by the JNI too: RegisterNatives fails as a whole if any one of its methods is missing.
    private static native boolean TProxyIsRunning();

    private static boolean loaded;
    private static Socks5Proxy proxy;
    private ParcelFileDescriptor tun;

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("limiter", Context.MODE_PRIVATE);
    }

    /** The cap in force: 0 when switched off. */
    static int capMbps(Context ctx) {
        SharedPreferences p = prefs(ctx);
        return p.getBoolean("on", true) ? p.getInt("mbps", DEFAULT_MBPS) : 0;
    }

    /** Switches the cap; the VPN stays up so Android's always-on setting is undisturbed. */
    static void setOn(Context ctx, boolean on) {
        prefs(ctx).edit().putBoolean("on", on).apply();
        if (proxy != null) proxy.bucket.setRate(capMbps(ctx) * 125_000L);
    }

    static boolean running() {
        return proxy != null;
    }

    /** Bytes Netflix has downloaded and uploaded through the limiter since it started. */
    static long[] traffic() {
        Socks5Proxy p = proxy;
        return p == null ? new long[]{0, 0} : new long[]{p.down.get(), p.up.get()};
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        start();
        return START_STICKY;
    }

    private synchronized void start() {
        if (tun != null) return;
        try {
            if (!loaded) {
                System.loadLibrary("hev-socks5-tunnel");
                loaded = true;
            }
            Socks5Proxy p = new Socks5Proxy(realDns());
            p.bucket.setRate(capMbps(this) * 125_000L);
            int port = p.start();
            // The capped apps only; addresses from the benchmark range hev documents. 198.18.0.2 is the
            // DNS address they are given: their queries are relayed to the Wi-Fi network's real resolver.
            Builder b = new Builder()
                    .setSession(getString(R.string.limiter_session))
                    .addAddress("198.18.0.1", 32)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer("198.18.0.2")
                    .setMtu(8500)
                    .setBlocking(false);
            int apps = 0;
            for (String pkg : CAPPED) {
                try {
                    b.addAllowedApplication(pkg);
                    apps++;
                } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
                    // Not installed (or disabled): nothing to cap.
                }
            }
            tun = apps == 0 ? null : b.establish();
            if (tun == null) {   // not approved
                p.stop();
                stopSelf();
                return;
            }
            File conf = new File(getFilesDir(), "limiter.yml");
            String yml = "tunnel:\n  mtu: 8500\n"
                    + "socks5:\n  port: " + port + "\n  address: 127.0.0.1\n  udp: 'udp'\n"
                    + "misc:\n  task-stack-size: 24576\n  log-level: warn\n";
            try (FileOutputStream out = new FileOutputStream(conf)) {
                out.write(yml.getBytes(StandardCharsets.UTF_8));
            }
            if (!TProxyStartService(conf.getAbsolutePath(), tun.getFd())) {
                throw new IllegalStateException("tunnel did not start");
            }
            proxy = p;
            Log.i(TAG, "limiter up for " + apps + " apps, cap " + capMbps(this) + " Mbps, proxy port " + port);
        } catch (Throwable t) {
            // Fail open: no VPN means Netflix simply uses Wi-Fi.
            Log.w(TAG, "limiter failed to start", t);
            stop();
            stopSelf();
        }
    }

    private synchronized void stop() {
        try {
            if (loaded) TProxyStopService();
        } catch (Throwable ignored) {
        }
        if (proxy != null) {
            proxy.stop();
            proxy = null;
        }
        if (tun != null) {
            try {
                tun.close();
            } catch (Exception ignored) {
            }
            tun = null;
        }
    }

    @Override
    public void onRevoke() {
        stop();
        super.onRevoke();
    }

    @Override
    public void onDestroy() {
        stop();
        super.onDestroy();
    }

    /** The Wi-Fi network's DNS server (the router), or a public one when it can't be read. */
    private InetAddress realDns() {
        try {
            ConnectivityManager cm = getSystemService(ConnectivityManager.class);
            for (Network n : cm.getAllNetworks()) {
                android.net.NetworkCapabilities caps = cm.getNetworkCapabilities(n);
                if (caps == null || caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)) continue;
                LinkProperties lp = cm.getLinkProperties(n);
                List<InetAddress> dns = lp != null ? lp.getDnsServers() : null;
                if (dns != null) for (InetAddress a : dns) if (a.getAddress().length == 4) return a;
            }
            return InetAddress.getByName("1.1.1.1");
        } catch (Exception e) {
            try {
                return InetAddress.getByAddress(new byte[]{1, 1, 1, 1});
            } catch (Exception ignored) {
                return null;
            }
        }
    }
}
