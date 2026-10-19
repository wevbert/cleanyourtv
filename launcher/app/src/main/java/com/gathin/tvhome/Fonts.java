package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.fonts.Font;
import android.graphics.fonts.FontFamily;
import android.os.Build;

import java.util.HashMap;
import java.util.Map;

/**
 * SF Pro instances, one per variation, made once per process. Setting fontVariationSettings in a layout
 * builds a fresh native typeface every time the layout is inflated; the screensaver is inflated on every
 * run, so it takes its faces from here instead and stops costing a few MB of native memory each time.
 */
final class Fonts {

    private static final Map<String, Typeface> CACHE = new HashMap<>();

    private Fonts() {
    }

    /** SF Pro with these axes, e.g. "'wght' 600, 'opsz' 28"; the plain family when they can't be applied. */
    static synchronized Typeface sf(Context ctx, String variation) {
        Typeface t = CACHE.get(variation);
        if (t != null) return t;
        t = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                // Built from the app's own font resource (stored uncompressed, so it is mapped, not copied).
                Font font = new Font.Builder(ctx.getResources(), R.font.sf_pro).setFontVariationSettings(variation).build();
                t = new Typeface.CustomFallbackBuilder(new FontFamily.Builder(font).build()).build();
            } catch (Exception e) {
                t = null;
            }
        }
        if (t == null) t = ctx.getResources().getFont(R.font.sf_pro);
        CACHE.put(variation, t);
        return t;
    }
}
