package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import java.util.Calendar;

/**
 * The built-in aerials (launcher/WALLPAPERS.md). Today's is decoded once per process and shared by Home and
 * the screensaver, so the screensaver costs no second copy of it.
 */
final class Wallpapers {

    static final int[] PHOTOS = {R.drawable.wall_napali, R.drawable.wall_seapoint};
    static final int[] PLACES = {R.string.place_napali, R.string.place_seapoint};

    private static Bitmap shared;
    private static int sharedIndex = -1;

    private Wallpapers() {
    }

    /** A different aerial each day. */
    static int todayIndex() {
        return Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % PHOTOS.length;
    }

    /** Today's aerial with the shelf gradient painted in, decoded on first use and then shared. */
    static synchronized Bitmap today(Context ctx) {
        int i = todayIndex();
        if (shared == null || sharedIndex != i) {
            shared = decode(ctx, i);
            sharedIndex = i;
        }
        return shared;
    }

    static synchronized boolean isShared(Bitmap b) {
        return b != null && b == shared;
    }

    /** Aerial i: the shared copy when it is today's, else a fresh decode the caller owns and recycles. */
    static Bitmap get(Context ctx, int i) {
        return i == todayIndex() ? today(ctx) : decode(ctx, i);
    }

    private static Bitmap decode(Context ctx, int i) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            // Full colour: a 16-bit decode bands visibly in skies and sea. 1920x1080 is 8 MB.
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            opts.inScaled = false;
            opts.inMutable = true;
            Bitmap bmp = BitmapFactory.decodeResource(ctx.getResources(), PHOTOS[i], opts);
            if (bmp != null) bake(ctx, bmp);
            return bmp;
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    /** Paints the shelf gradient into the pixels once, so a frame draws one opaque layer, not two. */
    static void bake(Context ctx, Bitmap bmp) {
        if (!bmp.isMutable()) return;
        Drawable shade = ctx.getDrawable(R.drawable.shelf_scrim);
        if (shade == null) return;
        shade.setBounds(0, 0, bmp.getWidth(), bmp.getHeight());
        shade.draw(new Canvas(bmp));
    }
}
