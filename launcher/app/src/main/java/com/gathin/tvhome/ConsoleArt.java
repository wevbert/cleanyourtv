package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

/**
 * The console tile's face: PlayStation blue, "PS5" in SF Pro and a small status pill ("On" in green,
 * "Standby" in grey). Drawn once per state into a 400x240 bitmap (a tvOS icon's 5:3), so the tile costs
 * what any app banner costs. No Sony artwork is copied.
 */
final class ConsoleArt {

    private static final int W = 400, H = 240;
    private static Drawable on, standby;

    private ConsoleArt() {
    }

    static synchronized Drawable get(Context ctx, String shortName, boolean isOn) {
        if (isOn && on != null) return on;
        if (!isOn && standby != null) return standby;
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setShader(new LinearGradient(0, 0, W, H, 0xFF0046C8, 0xFF00256E, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, bg);
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setColor(0xFFFFFFFF);
        t.setTypeface(Fonts.sf(ctx, "'wght' 700, 'opsz' 28"));
        t.setTextSize(92);
        t.setLetterSpacing(0.02f);
        t.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = t.getFontMetrics();
        c.drawText(shortName, W / 2f, H / 2f - (fm.ascent + fm.descent) / 2f - 10, t);
        // Status pill, bottom centre.
        String label = ctx.getString(isOn ? R.string.console_on : R.string.console_standby);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(Fonts.sf(ctx, "'wght' 600, 'opsz' 20"));
        p.setTextSize(24);
        float tw = p.measureText(label), dot = 10, padX = 16, h = 36;
        float w = padX + dot + 8 + tw + padX;
        RectF r = new RectF(W / 2f - w / 2, H - 26 - h, W / 2f + w / 2, H - 26);
        Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
        pill.setColor(0x40000000);
        c.drawRoundRect(r, h / 2, h / 2, pill);
        Paint d = new Paint(Paint.ANTI_ALIAS_FLAG);
        d.setColor(isOn ? 0xFF30D158 : 0xFF8E8E93);
        c.drawCircle(r.left + padX + dot / 2, r.centerY(), dot / 2, d);
        p.setColor(0xF2FFFFFF);
        Paint.FontMetrics pm = p.getFontMetrics();
        c.drawText(label, r.left + padX + dot + 8, r.centerY() - (pm.ascent + pm.descent) / 2, p);
        BitmapDrawable drawable = new BitmapDrawable(ctx.getResources(), b);
        if (isOn) on = drawable;
        else standby = drawable;
        return drawable;
    }
}
