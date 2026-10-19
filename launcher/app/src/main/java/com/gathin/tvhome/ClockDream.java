package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Screensaver, tvOS aerial style: an aerial drifting in a slow pan and zoom, crossfading to the next
 * every few minutes, with the time, the place, the greeting and the weather over it. Today's photo is
 * the one Home already holds; the next is decoded off the main thread only while the screensaver runs
 * and freed when it ends. The drift moves the view, not its pixels, so the GPU only composites it.
 */
public class ClockDream extends DreamService {

    private static final long PHOTO_MS = 3 * 60_000L;
    private static final long MINUTE_MS = 60_000L;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ImageView front, back;
    private Bitmap frontBmp, backBmp;
    private int photo;
    private boolean drifting;
    private float density;
    private int driftSign = 1;
    private TextView greeting, place, wxText;
    private View weatherRow, textBlock;
    private ImageView wxIcon;

    private final Runnable nextPhoto = new Runnable() {
        @Override
        public void run() {
            int next = (photo + 1) % Wallpapers.PHOTOS.length;
            IO.execute(() -> {
                Bitmap b = Wallpapers.get(getApplicationContext(), next);
                handler.post(() -> {
                    if (!drifting || b == null) return;
                    crossfadeTo(next, b);
                });
            });
            handler.postDelayed(this, PHOTO_MS);
        }
    };

    private final Runnable minute = new Runnable() {
        @Override
        public void run() {
            updateText();
            handler.postDelayed(this, MINUTE_MS);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any key wakes the TV back to where it was
        setFullscreen(true);
        setScreenBright(true);
        setContentView(R.layout.dream_clock);
        density = getResources().getDisplayMetrics().density;
        front = findViewById(R.id.photoA);
        back = findViewById(R.id.photoB);
        greeting = findViewById(R.id.dreamGreeting);
        place = findViewById(R.id.dreamPlace);
        weatherRow = findViewById(R.id.dreamWeather);
        wxIcon = findViewById(R.id.dreamWxIcon);
        wxText = findViewById(R.id.dreamWxText);
        textBlock = findViewById(R.id.dreamText);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        drifting = true;
        photo = Wallpapers.todayIndex();
        frontBmp = Wallpapers.today(this);
        front.setImageBitmap(frontBmp);
        place.setText(Wallpapers.PLACES[photo]);
        updateText();
        drift(front);
        View root = (View) front.getParent();
        root.setAlpha(0f);
        root.animate().alpha(1f).setDuration(1500).start();
        handler.postDelayed(nextPhoto, PHOTO_MS);
        handler.postDelayed(minute, MINUTE_MS);
    }

    @Override
    public void onDreamingStopped() {
        stop();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    private void stop() {
        drifting = false;
        handler.removeCallbacks(nextPhoto);
        handler.removeCallbacks(minute);
        front.animate().cancel();
        back.animate().cancel();
        front.setImageDrawable(null);
        back.setImageDrawable(null);
        // Free whatever the screensaver decoded itself; the shared photo stays for Home.
        release(frontBmp);
        release(backBmp);
        frontBmp = backBmp = null;
    }

    private static void release(Bitmap b) {
        if (b != null && !Wallpapers.isShared(b) && !b.isRecycled()) b.recycle();
    }

    /** A slow Ken Burns move: zoom from 100% to 108% while panning, alternating direction each photo. */
    private void drift(ImageView v) {
        v.animate().cancel();
        v.setScaleX(1f);
        v.setScaleY(1f);
        v.setTranslationX(0f);
        if (!ValueAnimator.areAnimatorsEnabled()) return;
        driftSign = -driftSign;
        v.animate().scaleX(1.08f).scaleY(1.08f).translationX(driftSign * 28 * density)
                .setDuration(PHOTO_MS + 4000).setInterpolator(new LinearInterpolator()).start();
    }

    private void crossfadeTo(int index, Bitmap b) {
        Bitmap old = frontBmp;
        back.setImageBitmap(b);
        backBmp = b;
        drift(back);
        back.bringToFront();
        place.bringToFront();
        textBlock.bringToFront();
        findViewById(R.id.dreamClock).bringToFront();
        back.setAlpha(0f);
        place.animate().alpha(0f).setDuration(700).withEndAction(() -> {
            place.setText(Wallpapers.PLACES[index]);
            place.animate().alpha(1f).setDuration(900).start();
        }).start();
        back.animate().alpha(1f).setDuration(2500).withEndAction(() -> {
            // The faded-out photo is no longer drawn: let it go.
            front.animate().cancel();
            front.setImageDrawable(null);
            release(old);
            ImageView t = front;
            front = back;
            back = t;
            frontBmp = backBmp;
            backBmp = null;
        }).start();
        photo = index;
    }

    private void updateText() {
        String name = getString(R.string.owner_name).trim();
        greeting.setVisibility(name.isEmpty() ? View.GONE : View.VISIBLE);
        if (!name.isEmpty()) greeting.setText(Greeting.now(this, name).text);
        Weather.Now now = getString(R.string.weather_place).trim().isEmpty() ? null
                : new Weather(this, getString(R.string.weather_latitude).trim(),
                getString(R.string.weather_longitude).trim(), getString(R.string.weather_unit).trim()).cached();
        if (now == null) {
            weatherRow.setVisibility(View.GONE);
            return;
        }
        wxIcon.setImageResource(Weather.icon(now.code, now.day));
        String note = MainActivity.weatherNote(this, now);
        wxText.setText(now.temp + "°  " + getString(Weather.label(now.code)) + (note.isEmpty() ? "" : "  ·  " + note));
        weatherRow.setVisibility(View.VISIBLE);
    }
}
