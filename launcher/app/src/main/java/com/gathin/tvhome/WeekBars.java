package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The data panel's last 7 days: one bar a day, the week's top app shaded inside it, the day's initial
 * under it and a one-line key. Drawn directly in onDraw (no child views), and only when the panel opens.
 */
public class WeekBars extends View {

    private static final int REST = 0x4DFFFFFF, TOP = 0xFF0A84FF, LABEL = 0x99FFFFFF;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float density;
    private DataUsage.Week week;
    private final String[] days = new String[7];

    public WeekBars(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        density = getResources().getDisplayMetrics().density;
        text.setTextSize(10.5f * getResources().getDisplayMetrics().scaledDensity);
        text.setColor(LABEL);
    }

    void setTypeface(Typeface t) {
        text.setTypeface(t);
    }

    void setWeek(DataUsage.Week w) {
        week = w;
        SimpleDateFormat f = new SimpleDateFormat("EEEEE", Locale.getDefault());
        if (w != null) for (int i = 0; i < 7; i++) days[i] = f.format(new Date(w.start[i]));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (week == null) return;
        float w = getWidth(), h = getHeight();
        float keyH = 16 * density, dayH = 16 * density;
        float barArea = h - keyH - dayH - 4 * density;
        long max = 1;
        for (long t : week.total) max = Math.max(max, t);
        float slot = w / 7f, bw = Math.min(18 * density, slot * 0.5f), rad = 3 * density;
        text.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < 7; i++) {
            float cx = slot * i + slot / 2f, bottom = keyH + barArea;
            float full = Math.max(week.total[i] > 0 ? 2 * density : 0, barArea * week.total[i] / max);
            float topH = week.total[i] > 0 ? full * week.top[i] / week.total[i] : 0;
            r.set(cx - bw / 2, bottom - full, cx + bw / 2, bottom);
            fill.setColor(REST);
            c.drawRoundRect(r, rad, rad, fill);
            if (topH > 0) {
                r.set(cx - bw / 2, bottom - topH, cx + bw / 2, bottom);
                fill.setColor(TOP);
                c.drawRoundRect(r, rad, rad, fill);
            }
            if (days[i] != null) c.drawText(days[i], cx, h - 3 * density, text);
        }
        // Key: top app in blue, everything else in grey.
        text.setTextAlign(Paint.Align.LEFT);
        float y = 11 * density, x = 2 * density, sq = 7 * density;
        if (week.topLabel != null) {
            fill.setColor(TOP);
            r.set(x, y - sq, x + sq, y);
            c.drawRoundRect(r, 2 * density, 2 * density, fill);
            x += sq + 5 * density;
            c.drawText(week.topLabel, x, y, text);
            x += text.measureText(week.topLabel) + 12 * density;
        }
        fill.setColor(REST);
        r.set(x, y - sq, x + sq, y);
        c.drawRoundRect(r, 2 * density, 2 * density, fill);
        c.drawText(getResources().getString(R.string.data_everything_else), x + sq + 5 * density, y, text);
    }
}
