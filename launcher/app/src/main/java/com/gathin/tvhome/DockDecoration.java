package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * The tvOS top row's glass band: a dark translucent slab with a light rim that brightens toward its top
 * edge, drawn behind the top row's icons. Painted, not blurred, so it costs one rounded rect per frame.
 */
public class DockDecoration extends RecyclerView.ItemDecoration {

    private final int dockSize;
    private final float density;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float rimTop = Float.NaN, rimBottom;

    DockDecoration(int dockSize, float density) {
        this.dockSize = dockSize;
        this.density = density;
        fill.setColor(0x52232328);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1.2f * density);
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        if (pos >= 0 && pos < dockSize) {
            outRect.top = (int) (6 * density);
            outRect.bottom = (int) (8 * density);
        }
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        boolean any = false;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(child);
            if (pos < 0 || pos >= dockSize) continue;
            View card = child.findViewById(R.id.card);
            if (card == null) continue;
            float t = child.getTop() + card.getTop() + child.getTranslationY();
            float b = t + card.getHeight();
            if (!any) {
                rect.set(child.getLeft() + card.getLeft(), t, child.getLeft() + card.getRight(), b);
                any = true;
            } else {
                rect.union(child.getLeft() + card.getLeft(), t, child.getLeft() + card.getRight(), b);
            }
        }
        if (!any) return;
        // The band spans the content width, as on tvOS, even when the row holds fewer apps.
        rect.left = parent.getPaddingLeft() - 18 * density;
        rect.right = parent.getWidth() - parent.getPaddingRight() + 18 * density;
        rect.inset(0, -20 * density);
        if (rect.top != rimTop || rect.bottom != rimBottom) {
            rimTop = rect.top;
            rimBottom = rect.bottom;
            rim.setShader(new LinearGradient(0, rect.top, 0, rect.bottom, 0x47FFFFFF, 0x0FFFFFFF, Shader.TileMode.CLAMP));
        }
        float radius = 34 * density;
        c.drawRoundRect(rect, radius, radius, fill);
        c.drawRoundRect(rect, radius, radius, rim);
    }
}
