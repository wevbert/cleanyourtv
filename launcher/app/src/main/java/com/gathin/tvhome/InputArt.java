package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.util.HashMap;
import java.util.Map;

/**
 * Face for an HDMI tile that belongs to a service with an app on the TV (the DStv decoder): that app's own
 * banner, as installed on this TV and drawn at runtime, with the port's name on a pill so it is not mistaken
 * for the app. Drawn once into a 400x240 bitmap and cached.
 */
final class InputArt {

    private static final int W = 400, H = 240;
    private static final Map<String, Drawable> CACHE = new HashMap<>();

    private InputArt() {
    }

    static synchronized Drawable fromApp(Context ctx, String pkg, String portName) {
        String key = pkg + "|" + portName;
        Drawable cached = CACHE.get(key);
        if (cached != null) return cached;
        Drawable banner = banner(ctx, pkg);
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        if (banner != null) {
            // Centre-crop the banner to the tile, as the app's own tile is drawn.
            float bw = Math.max(1, banner.getIntrinsicWidth()), bh = Math.max(1, banner.getIntrinsicHeight());
            float scale = Math.max(W / bw, H / bh);
            int dw = Math.round(bw * scale), dh = Math.round(bh * scale);
            int left = (W - dw) / 2, top = (H - dh) / 2;
            Drawable d = banner.getConstantState() != null ? banner.getConstantState().newDrawable().mutate() : banner;
            d.setBounds(left, top, left + dw, top + dh);
            d.draw(c);
        } else {
            c.drawColor(0xFF1C1C1E);
        }
        // The port's name on a dark pill, bottom right.
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(Fonts.sf(ctx, "'wght' 600, 'opsz' 20"));
        p.setTextSize(24);
        p.setColor(0xF2FFFFFF);
        float tw = p.measureText(portName), padX = 14, h = 34;
        RectF r = new RectF(W - 18 - (tw + 2 * padX), H - 18 - h, W - 18, H - 18);
        Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
        pill.setColor(0x99000000);
        c.drawRoundRect(r, h / 2, h / 2, pill);
        Paint.FontMetrics fm = p.getFontMetrics();
        c.drawText(portName, r.left + padX, r.centerY() - (fm.ascent + fm.descent) / 2, p);
        Drawable out = new BitmapDrawable(ctx.getResources(), b);
        CACHE.put(key, out);
        return out;
    }

    /** "DStv · HDMI 3": the app's name with the port, for the label and screen readers. */
    static String appLabel(Context ctx, String pkg, String fallback) {
        try {
            PackageManager pm = ctx.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)) + " · " + fallback;
        } catch (PackageManager.NameNotFoundException e) {
            return fallback;
        }
    }

    private static Drawable banner(Context ctx, String pkg) {
        PackageManager pm = ctx.getPackageManager();
        try {
            Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setPackage(pkg);
            ResolveInfo ri = pm.resolveActivity(probe, 0);
            if (ri != null && ri.activityInfo != null) {
                ActivityInfo ai = ri.activityInfo;
                Drawable d = ai.loadBanner(pm);
                if (d == null) d = ai.applicationInfo.loadBanner(pm);
                if (d != null) return d;
            }
            return pm.getApplicationBanner(pkg);
        } catch (Exception e) {
            return null;
        }
    }
}
