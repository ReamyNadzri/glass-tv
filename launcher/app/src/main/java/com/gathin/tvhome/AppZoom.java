package com.gathin.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityOptions;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LightingColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.view.View;
import android.view.ViewOverlay;
import android.view.animation.PathInterpolator;

/**
 * Opening and closing an app the way Apple TV does, in the style chosen in Glass TV's settings
 * ({@link HomePrefs#zoom}):
 * <ul>
 * <li>card: the tile's own colour grows to fill the screen while its picture stays sharp in the middle,
 * only a little bigger, and Home behind pushes in and darkens;</li>
 * <li>glass: the same, with a pane of Home's frosted glass tinted in the app's colour instead;</li>
 * <li>lift: the tile lifts a little while Home rushes past it into black;</li>
 * <li>stretch: the first version, the tile's picture stretched to the full screen;</li>
 * <li>off: apps simply appear.</li>
 * </ul>
 * Coming back plays it in reverse. Android does not let a launcher move another app's window, so the
 * zoom is drawn over Home, in the window's overlay, never in Home's views; Home itself only scales and
 * darkens, in one GPU layer. Off with reduced_motion.
 */
final class AppZoom {

    static final long OPEN_MS = 420, CLOSE_MS = 460;
    /** The app starts this far into the zoom, so its first frame arrives while the tile is still growing. */
    static final long LAUNCH_AT_MS = 180;
    /** The tile's picture grows to this size in the middle of a card or glass zoom. */
    static final float LOGO_GROW = 1.5f;
    /** How far the tile lifts in the lift zoom. */
    static final float LIFT = 1.14f;
    /** Still in front this long after OK with no app shown: the open failed quietly, so Home comes back. */
    private static final long GIVE_UP_MS = 2500;
    /** Apple's easing: quick to start, long gentle landing. Not static: the unit tests load this class. */
    private final PathInterpolator ease = new PathInterpolator(0.2f, 0f, 0f, 1f);

    private final Activity activity;
    /** Home's own views: scaled, never the overlay. */
    private final View home;
    private final Handler handler;
    private final ViewOverlay overlay;
    private final HomePrefs prefs;
    private final float density;
    /** Paints Home's layer during the zoom; its colour filter darkens Home. */
    private final Paint layerPaint = new Paint();

    /** The tile an app was opened from, in window coordinates, and a picture of it; kept until Home is back. */
    private final RectF from = new RectF();
    private float fromRadius;
    private ZoomDrawable ghost;
    /** The style and speed the app was opened with, so it closes the same way. */
    private String style = "card";
    private float time = 1f;
    /** An app was opened with the zoom and Home has not come back since. */
    private boolean awaitingReturn;
    private boolean opening;
    private boolean paused;
    private ValueAnimator running;

    AppZoom(Activity activity, View home, Handler handler) {
        this.activity = activity;
        this.home = home;
        this.handler = handler;
        this.overlay = activity.getWindow().getDecorView().getOverlay();
        this.prefs = new HomePrefs(activity);
        this.density = activity.getResources().getDisplayMetrics().density;
    }

    /**
     * OK on a tile: zooms it open and starts the app part-way through. Returns false when the zoom is
     * off or cannot run (the caller opens the app plainly); a failed start calls {@code onFail} and puts
     * Home back. {@code tint} colours the glass style.
     */
    boolean open(View card, float radius, int tint, Runnable start, Runnable onFail) {
        if (opening) return true;   // a second OK while the first app is on its way
        String chosen = prefs.zoom();
        if ("off".equals(chosen)) return false;
        int w = card.getWidth(), h = card.getHeight();
        View decor = activity.getWindow().getDecorView();
        if (w <= 0 || h <= 0 || decor.getWidth() <= 0) return false;
        Bitmap bmp;
        try {
            bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError e) {
            return false;
        }
        card.draw(new Canvas(bmp));
        clear();
        releaseGhost();
        style = chosen;
        time = prefs.zoomTime();
        tileRect(card, from);
        fromRadius = radius * card.getScaleX();
        ghost = new ZoomDrawable(style, bmp, edgeColor(bmp), tint, decor, density, fromRadius);
        ghost.setBounds(0, 0, decor.getWidth(), decor.getHeight());
        opening = true;
        awaitingReturn = true;

        final RectF screen = new RectF(0, 0, decor.getWidth(), decor.getHeight());
        holdHome();
        overlay.add(ghost);
        frame(screen, 0f, true);
        running = animate(Math.round(OPEN_MS * time), t -> frame(screen, t, true), null);
        handler.postDelayed(() -> {
            if (!opening) return;
            try {
                start.run();
            } catch (RuntimeException e) {
                reset();
                onFail.run();
                return;
            }
            handler.postDelayed(giveUp, GIVE_UP_MS);
        }, Math.round(LAUNCH_AT_MS * time));
        return true;
    }

    /** Window animations for the app's start: it fades in over the zoom while Home holds still. */
    static android.os.Bundle startOptions(Activity activity) {
        return ActivityOptions.makeCustomAnimation(activity, R.anim.app_open_enter, R.anim.app_open_exit).toBundle();
    }

    /**
     * One frame of the zoom: {@code t} runs from 0 to 1, opening from the tile to the full screen, or
     * closing from the full screen back into the tile.
     */
    private void frame(RectF screen, float t, boolean opening) {
        float out = opening ? t : 1f - t;   // how far out of the tile: 0 at the tile, 1 at the full screen
        ZoomDrawable g = ghost;
        switch (style) {
            case "lift": {
                pushHome(out, 1.3f, 0f);
                float s = lerp(1f, LIFT, out);
                // Opening, the tile fades in Home's dark once it has lifted; closing, it is there early.
                int alpha = opening ? Math.round(255 * Math.min(1f, (1f - t) / 0.45f))
                        : Math.round(255 * Math.min(1f, t / 0.4f));
                if (g != null) g.set(scaled(from, s), fromRadius * s, 1f, alpha);
                break;
            }
            case "glass":
                pushHome(out, 1.05f, 0.8f);
                if (g != null) g.set(lerp(from, screen, out), lerp(fromRadius, 0f, out), lerp(1f, LOGO_GROW, out), 255);
                break;
            case "stretch":
                pushHome(out, 1.05f, 0.42f);
                if (g != null) g.set(lerp(from, screen, out), lerp(fromRadius, 0f, out), 1f,
                        opening ? 255 : Math.round(255 * Math.min(1f, t * 4f)));
                break;
            default:   // card
                pushHome(out, 1.05f, 0.42f);
                if (g != null) g.set(lerp(from, screen, out), lerp(fromRadius, 0f, out), lerp(1f, LOGO_GROW, out), 255);
        }
    }

    private final Runnable giveUp = () -> {
        if (opening && !paused) {
            awaitingReturn = false;
            reset();
        }
    };

    /**
     * Home is in front again. After an app opened with the zoom, it plays back into the tile;
     * {@code tilesMoved} (apps were installed or removed meanwhile) keeps only Home settling back, since
     * the tile may no longer be where it was.
     */
    void onResume(boolean tilesMoved) {
        paused = false;
        // Back without Home ever being hidden (the app closed at once): the zoom still covers Home.
        if (opening) {
            opening = false;
            handler.removeCallbacks(giveUp);
        }
        if (!awaitingReturn) return;
        awaitingReturn = false;
        View decor = activity.getWindow().getDecorView();
        if (decor.getWidth() <= 0) {
            releaseGhost();
            return;
        }
        clear();
        final RectF screen = new RectF(0, 0, decor.getWidth(), decor.getHeight());
        if (tilesMoved) releaseGhost();
        holdHome();
        if (ghost != null) overlay.add(ghost);
        frame(screen, 0f, false);
        running = animate(Math.round(CLOSE_MS * time), t -> frame(screen, t, false), () -> {
            clear();
            releaseGhost();
        });
    }

    void onPause() {
        paused = true;
    }

    /** Home is hidden behind the app now: nothing of the zoom may still be there when it comes back. */
    void onStop() {
        handler.removeCallbacks(giveUp);
        if (opening) {
            opening = false;
            clear();
        }
    }

    /** Memory is short while an app runs: the closing zoom then only settles Home back. */
    void trim() {
        if (!opening && running == null) releaseGhost();
    }

    private void reset() {
        handler.removeCallbacks(giveUp);
        opening = false;
        clear();
        releaseGhost();
    }

    /** Takes everything off the screen and puts Home back at its own size. */
    private void clear() {
        if (running != null) {
            ValueAnimator r = running;
            running = null;
            r.removeAllListeners();
            r.cancel();
        }
        home.setScaleX(1f);
        home.setScaleY(1f);
        home.setLayerType(View.LAYER_TYPE_NONE, null);
        if (ghost != null) overlay.remove(ghost);
    }

    private void releaseGhost() {
        if (ghost != null) {
            overlay.remove(ghost);
            ghost.bitmap.recycle();
            ghost = null;
        }
    }

    /**
     * Home goes into one GPU layer for the zoom, pivoting on the tile. Scaling and darkening it then
     * redraws that one picture instead of Home's views, plus no second full-screen pass for a dark
     * veil: this TV's graphics chip, not the app, is what holds each frame back.
     */
    private void holdHome() {
        home.setPivotX(from.centerX());
        home.setPivotY(from.centerY());
        layerPaint.setColorFilter(null);
        home.setLayerType(View.LAYER_TYPE_HARDWARE, layerPaint);
    }

    /** Home pushed in by {@code t} (0 at rest, 1 fully behind the zoom) to {@code scale} and {@code dim}. */
    private void pushHome(float t, float scale, float dim) {
        float s = lerp(1f, scale, t);
        home.setScaleX(s);
        home.setScaleY(s);
        int c = Math.round(255 * lerp(1f, dim, t));
        layerPaint.setColorFilter(new LightingColorFilter(Color.rgb(c, c, c), 0));
        home.setLayerPaint(layerPaint);
    }

    private interface Frame {
        void at(float t);
    }

    private ValueAnimator animate(long ms, Frame frame, Runnable end) {
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(ms);
        a.setInterpolator(ease);
        a.addUpdateListener(v -> frame.at((float) v.getAnimatedValue()));
        a.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (running == animation) running = null;
                if (end != null) end.run();
            }
        });
        a.start();
        return a;
    }

    /** The tile as it shows now, focus zoom included, in window coordinates. */
    private void tileRect(View card, RectF out) {
        int[] at = new int[2];
        Rect r = new Rect(0, 0, card.getWidth(), card.getHeight());
        // Layout position (offsetDescendantRectToMyCoords ignores the zoom), then the zoom around the centre.
        ((android.view.ViewGroup) home).offsetDescendantRectToMyCoords(card, r);
        home.getLocationInWindow(at);
        float[] b = scaledAround(r.left + at[0], r.top + at[1], r.right + at[0], r.bottom + at[1], card.getScaleX());
        out.set(b[0], b[1], b[2], b[3]);
    }

    /** The colour round the edge of the tile's picture: the card the picture sits on as it grows. */
    private static int edgeColor(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight(), inset = Math.max(1, Math.min(w, h) / 20);
        int[] px = new int[2 * (w + h)];
        int n = 0;
        for (int x = 0; x < w; x++) {
            px[n++] = b.getPixel(x, inset);
            px[n++] = b.getPixel(x, h - 1 - inset);
        }
        for (int y = 0; y < h; y++) {
            px[n++] = b.getPixel(inset, y);
            px[n++] = b.getPixel(w - 1 - inset, y);
        }
        return averageColor(px);
    }

    /** The average of these colours, opaque; transparent pixels count as the black behind Home. */
    static int averageColor(int[] argb) {
        if (argb.length == 0) return 0xFF000000;
        long r = 0, g = 0, b = 0;
        for (int c : argb) {
            int a = c >>> 24;
            r += ((c >> 16) & 0xFF) * a / 255;
            g += ((c >> 8) & 0xFF) * a / 255;
            b += (c & 0xFF) * a / 255;
        }
        int n = argb.length;
        return 0xFF000000 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
    }

    /** A rectangle scaled by {@code s} around its own centre: {left, top, right, bottom}. */
    static float[] scaledAround(float l, float t, float r, float b, float s) {
        float cx = (l + r) / 2f, cy = (t + b) / 2f, hw = (r - l) * s / 2f, hh = (b - t) * s / 2f;
        return new float[]{cx - hw, cy - hh, cx + hw, cy + hh};
    }

    static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private final RectF work = new RectF();

    private RectF lerp(RectF a, RectF b, float t) {
        work.set(lerp(a.left, b.left, t), lerp(a.top, b.top, t), lerp(a.right, b.right, t), lerp(a.bottom, b.bottom, t));
        return work;
    }

    private RectF scaled(RectF a, float s) {
        float[] r = scaledAround(a.left, a.top, a.right, a.bottom, s);
        work.set(r[0], r[1], r[2], r[3]);
        return work;
    }

    /** The zoom itself: the card or glass pane growing, with the tile's picture on it. */
    private static final class ZoomDrawable extends Drawable {
        final Bitmap bitmap;
        private final String style;
        private final int cardColor, tint;
        private final View owner;
        private final Paint picture = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint card = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final BitmapShader shader;
        private final Matrix matrix = new Matrix();
        private final RectF src, rect = new RectF(), pic = new RectF();
        private final Backdrop.Fill frost = new Backdrop.Fill();
        /** The focused tile's corner radius, which the picture keeps as it grows. */
        private final float tileRadius;
        private float radius, grow = 1f;

        ZoomDrawable(String style, Bitmap bitmap, int cardColor, int tint, View owner, float density, float tileRadius) {
            this.style = style;
            this.bitmap = bitmap;
            this.tileRadius = tileRadius;
            this.cardColor = cardColor;
            this.tint = tint;
            this.owner = owner;
            shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            picture.setShader(shader);
            src = new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight());
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(1.5f * density);
            rim.setColor(0x99FFFFFF);
        }

        /** Where the zoom is: its rectangle and corners, the picture's growth, and how visible it is. */
        void set(RectF r, float radius, float grow, int alpha) {
            rect.set(r);
            this.radius = radius;
            this.grow = grow;
            picture.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void draw(Canvas canvas) {
            if ("stretch".equals(style) || "lift".equals(style)) {
                drawPicture(canvas, rect, radius);
                return;
            }
            if ("glass".equals(style)) {
                frost.draw(canvas, owner, rect, radius, 1f);
                card.setColor((tint & 0x00FFFFFF) | 0x59000000);
                canvas.drawRoundRect(rect, radius, radius, card);
                canvas.drawRoundRect(rect, radius, radius, rim);
            } else {
                card.setColor(cardColor);
                canvas.drawRoundRect(rect, radius, radius, card);
            }
            // The picture keeps its own shape, centred, only a little bigger: nothing is stretched.
            float w = src.width() / src.height() * pictureHeight() * grow, h = pictureHeight() * grow;
            pic.set(rect.centerX() - w / 2f, rect.centerY() - h / 2f, rect.centerX() + w / 2f, rect.centerY() + h / 2f);
            drawPicture(canvas, pic, tileRadius * grow);
        }

        /** The picture's height at the tile: the bitmap is the tile at rest, the zoom starts from its focused size. */
        private float pictureHeight() {
            return src.height() * AppAdapter.FOCUS_SCALE;
        }

        private void drawPicture(Canvas canvas, RectF where, float r) {
            matrix.setRectToRect(src, where, Matrix.ScaleToFit.FILL);
            shader.setLocalMatrix(matrix);
            canvas.drawRoundRect(where, r, r, picture);
        }

        @Override
        public void setAlpha(int alpha) {
            picture.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            picture.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
