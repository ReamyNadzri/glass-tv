package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;

/**
 * The waktu solat widget, in the style chosen in Glass TV's settings ({@link HomePrefs#prayerStyle}):
 * <ul>
 * <li>card: today's six times in a glass card at the top right, the next one lit with its countdown
 * and a gold bar filling towards it;</li>
 * <li>pill: the next prayer and its countdown in the top bar, beside Inputs;</li>
 * <li>strip: today's times as glass chips just above the dock, the next one gold;</li>
 * <li>off.</li>
 * </ul>
 * The card and the strip show with the dock's shelf and go with it when focus moves down into the grid,
 * so they never sit over the apps. Nothing here is focusable: the remote's path through Home is unchanged.
 */
final class PrayerWidget {
    private static final int GOLD = 0xFFF6D27A;

    private final Activity activity;
    private final FrameLayout root;
    private final LinearLayout topRow;
    private final float density;
    private final Typeface font;
    private String style = "off";

    private LinearLayout pill;
    private TextView pillName, pillIn;
    private LinearLayout card;
    private TextView cardTitle, cardHijri, cardNote;
    private final LinearLayout[] rows = new LinearLayout[PrayerTimes.NAMES.length];
    private final TextView[] rowTime = new TextView[PrayerTimes.NAMES.length], rowIn = new TextView[PrayerTimes.NAMES.length];
    private View barFill;
    private LinearLayout strip;
    private final LinearLayout[] chips = new LinearLayout[PrayerTimes.NAMES.length];
    private final TextView[] chipTime = new TextView[PrayerTimes.NAMES.length], chipName = new TextView[PrayerTimes.NAMES.length];
    private TextView stripNote;
    private Drawable[] chipGold = new Drawable[PrayerTimes.NAMES.length];

    private boolean shown = true;
    private float right = -1, below = -1, shelfBottom = -1;
    private String reminded;

    PrayerWidget(Activity activity, FrameLayout root, LinearLayout topRow, float density) {
        this.activity = activity;
        this.root = root;
        this.topRow = topRow;
        this.density = density;
        Typeface f;
        try {
            f = activity.getResources().getFont(R.font.outfit);
        } catch (RuntimeException e) {
            f = Typeface.DEFAULT;
        }
        font = f;
    }

    String style() {
        return style;
    }

    /** Builds the widget for {@code s}, removing the one shown before. */
    void setStyle(String s) {
        if (s.equals(style)) return;
        if (pill != null) topRow.removeView(pill);
        if (card != null) root.removeView(card);
        if (strip != null) root.removeView(strip);
        pill = null;
        card = null;
        strip = null;
        style = s;
        if ("pill".equals(s)) buildPill();
        else if ("card".equals(s)) buildCard();
        else if ("strip".equals(s)) buildStrip();
        position();
    }

    // ---- building ---------------------------------------------------------------------------------

    private void buildPill() {
        pill = new LinearLayout(activity);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(13.5f);
        pill.setPadding(pad, 0, pad, 0);
        glass(pill, 21);
        ImageView moon = new ImageView(activity);
        moon.setImageResource(R.drawable.ic_prayer);
        pill.addView(moon, new LinearLayout.LayoutParams(dp(18), dp(18)));
        pillName = text(15, 0xFFFFFFFF, 500);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(7);
        pill.addView(pillName, lp);
        pillIn = text(13, 0xBFFFFFFF, 400);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp2.leftMargin = dp(8);
        pill.addView(pillIn, lp2);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        plp.setMarginStart(dp(10));
        topRow.addView(pill, 0, plp);
    }

    private void buildCard() {
        card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(11), dp(14), dp(12));
        glass(card, 18);
        // "Waktu Solat", and under it where and which day: the place, then the Hijri date.
        cardTitle = text(15, 0xFFFFFFFF, 500);
        cardTitle.setText(R.string.prayer_title);
        card.addView(cardTitle);
        cardHijri = text(10.5f, 0xB3FFFFFF, 400);
        cardHijri.setLetterSpacing(0.06f);
        cardHijri.setSingleLine(true);
        cardHijri.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hl.topMargin = dp(4);
        card.addView(cardHijri, hl);
        cardNote = text(13, 0xCCFFFFFF, 400);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nl.topMargin = dp(8);
        card.addView(cardNote, nl);
        for (int i = 0; i < rows.length; i++) {
            LinearLayout r = new LinearLayout(activity);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(dp(9), 0, dp(9), 0);
            boolean minor = i == PrayerTimes.SYURUK;
            TextView name = text(minor ? 12.5f : 14, 0xFFFFFFFF, 400);
            name.setText(PrayerTimes.NAMES[i]);
            r.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            rowIn[i] = text(11.5f, 0xD9FFFFFF, 400);
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            il.rightMargin = dp(10);
            r.addView(rowIn[i], il);
            rowTime[i] = text(minor ? 12.5f : 14, 0xFFFFFFFF, 400);
            rowTime[i].setFontFeatureSettings("tnum");
            r.addView(rowTime[i]);
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(minor ? 20 : 23));
            rl.topMargin = dp(i == 0 ? 7 : 1);
            rows[i] = r;
            card.addView(r, rl);
        }
        FrameLayout bar = new FrameLayout(activity);
        bar.setBackground(round(0x2EFFFFFF, 2));
        barFill = new View(activity);
        barFill.setBackground(round(GOLD, 2));
        bar.addView(barFill, new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
        bl.topMargin = dp(9);
        card.addView(bar, bl);
        root.addView(card, new FrameLayout.LayoutParams(dp(250), ViewGroup.LayoutParams.WRAP_CONTENT));
        card.setAlpha(shown ? 1f : 0f);
    }

    private void buildStrip() {
        strip = new LinearLayout(activity);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < chips.length; i++) {
            LinearLayout c = new LinearLayout(activity);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setGravity(Gravity.CENTER);
            Drawable glass = new GlassDrawable(c, 14 * density, density, null);
            chipGold[i] = round(0x47F6D27A, 14);
            chipGold[i].setAlpha(0);
            c.setBackground(new LayerDrawable(new Drawable[]{glass, chipGold[i]}));
            Backdrop.watch(c);
            chipName[i] = text(9.5f, 0xCCFFFFFF, 500);
            chipName[i].setLetterSpacing(0.1f);
            chipName[i].setText(PrayerTimes.NAMES[i].toUpperCase(java.util.Locale.ROOT));
            c.addView(chipName[i]);
            chipTime[i] = text(14, 0xFFFFFFFF, 300);
            chipTime[i].setFontFeatureSettings("tnum");
            c.addView(chipTime[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(68), dp(42));
            if (i > 0) lp.leftMargin = dp(6);
            chips[i] = c;
            strip.addView(c, lp);
        }
        stripNote = text(13, 0xE6FFFFFF, 400);
        stripNote.setGravity(Gravity.CENTER);
        stripNote.setPadding(dp(14), 0, dp(14), 0);
        glass(stripNote, 14);
        stripNote.setVisibility(View.GONE);
        strip.addView(stripNote, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)));
        root.addView(strip, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        strip.setAlpha(shown ? 1f : 0f);
    }

    // ---- placing ----------------------------------------------------------------------------------

    /** The top bar's right edge and bottom, in Home's coordinates: the card hangs under it, on the right. */
    void placeTop(float rightEdge, float topBarBottom) {
        if (rightEdge == right && topBarBottom == below) return;
        right = rightEdge;
        below = topBarBottom;
        position();
    }

    /** The bottom of the dock's shelf (just above the dock): the strip sits on it. */
    void placeShelf(float bottom) {
        if (bottom == shelfBottom) return;
        shelfBottom = bottom;
        position();
    }

    private void position() {
        if (card != null && right > 0) {
            card.setX(right - dp(250));
            card.setY(below + dp(14));
        }
        if (strip != null && right > 0 && shelfBottom > 0) {
            strip.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            strip.setX(right - strip.getMeasuredWidth());
            strip.setY(shelfBottom - dp(42));
        }
    }

    /** With the dock's shelf: shown while focus is on the dock, gone in the grid below. */
    void setShown(boolean show) {
        if (show == shown) return;
        shown = show;
        for (View v : new View[]{card, strip}) {
            if (v == null) continue;
            v.animate().cancel();
            v.animate().alpha(show ? 1f : 0f).setDuration(show ? 240 : 140).start();
        }
    }

    // ---- content ----------------------------------------------------------------------------------

    /** Shows the times from {@code src} as of now. */
    void update(PrayerSource src) {
        if ("off".equals(style)) return;
        PrayerTimes t = src.times();
        Calendar now = Calendar.getInstance(PrayerSource.MALAYSIA);
        PrayerTimes.Day today = t == null ? null : t.day(now.get(Calendar.DAY_OF_MONTH));
        PrayerSource.Zone zone = src.zoneInfo(src.zone());
        String place = zone != null ? zone.place() : "";
        if (today == null) {
            String note = activity.getString(src.zoneUnknown() ? R.string.prayer_pick_zone : R.string.prayer_loading);
            showNote(note, place);
            return;
        }
        PrayerTimes.Day tomorrow = t.day(now.get(Calendar.DAY_OF_MONTH) + 1);
        float minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE) + now.get(Calendar.SECOND) / 60f;
        PrayerTimes.Next next = PrayerTimes.next(today, tomorrow, minute);
        boolean h24 = DateFormat.is24HourFormat(activity);
        String in = PrayerTimes.until(next.at - minute);

        if (pill != null) {
            pillName.setText(PrayerTimes.NAMES[next.index] + " " + PrayerTimes.clock(next.at, h24));
            pillIn.setText(in);
        }
        if (card != null) {
            cardNote.setVisibility(View.GONE);
            String day = PrayerTimes.hijri(today.hijri);
            cardHijri.setText((place.isEmpty() ? day : day.isEmpty() ? place : place + " · " + day)
                    .toUpperCase(java.util.Locale.ROOT));
            for (int i = 0; i < rows.length; i++) {
                rows[i].setVisibility(View.VISIBLE);
                boolean isNext = i == next.index, past = today.minutes[i] <= minute && !isNext;
                rowTime[i].setText(PrayerTimes.clock(isNext ? next.at : today.minutes[i], h24));
                rowIn[i].setText(isNext ? in : "");
                rows[i].setBackground(isNext ? round(0x33FFFFFF, 10) : null);
                rows[i].setAlpha(past ? 0.5f : i == PrayerTimes.SYURUK ? 0.75f : 1f);
            }
            ((View) barFill.getParent()).setVisibility(View.VISIBLE);
            View bar = (View) barFill.getParent();
            ViewGroup.LayoutParams lp = barFill.getLayoutParams();
            int w = bar.getWidth() > 0 ? bar.getWidth() : dp(250 - 28);
            lp.width = Math.round(w * next.progress(minute));
            barFill.setLayoutParams(lp);
        }
        if (strip != null) {
            stripNote.setVisibility(View.GONE);
            for (int i = 0; i < chips.length; i++) {
                chips[i].setVisibility(View.VISIBLE);
                boolean isNext = i == next.index, past = today.minutes[i] <= minute && !isNext;
                chipTime[i].setText(PrayerTimes.clock(isNext ? next.at : today.minutes[i], h24).replace(" AM", "").replace(" PM", ""));
                chipName[i].setText(isNext ? PrayerTimes.NAMES[i].toUpperCase(java.util.Locale.ROOT) + " · " + in.replace("in ", "")
                        : PrayerTimes.NAMES[i].toUpperCase(java.util.Locale.ROOT));
                chipGold[i].setAlpha(isNext ? 255 : 0);
                chips[i].setAlpha(past ? 0.45f : 1f);
                ViewGroup.LayoutParams lp = chips[i].getLayoutParams();
                int w = dp(isNext ? 112 : 68);
                if (lp.width != w) {
                    lp.width = w;
                    chips[i].setLayoutParams(lp);
                }
            }
            position();
        }
    }

    private void showNote(String note, String place) {
        if (pill != null) {
            pillName.setText(note);
            pillIn.setText("");
        }
        if (card != null) {
            cardHijri.setText(place.toUpperCase(java.util.Locale.ROOT));
            cardNote.setText(note);
            cardNote.setVisibility(View.VISIBLE);
            for (LinearLayout r : rows) r.setVisibility(View.GONE);
            ((View) barFill.getParent()).setVisibility(View.GONE);
        }
        if (strip != null) {
            for (LinearLayout c : chips) c.setVisibility(View.GONE);
            stripNote.setText(note);
            stripNote.setVisibility(View.VISIBLE);
            position();
        }
    }

    // ---- reminder ---------------------------------------------------------------------------------

    /**
     * {@code minutesBefore} the next prayer, Home shows a glass reminder at the top for ten seconds,
     * once per prayer. Only on Home: nothing of Glass TV runs over another app.
     */
    void remind(PrayerSource src, int minutesBefore) {
        if (minutesBefore <= 0 || src.times() == null) return;
        Calendar now = Calendar.getInstance(PrayerSource.MALAYSIA);
        PrayerTimes.Day today = src.times().day(now.get(Calendar.DAY_OF_MONTH));
        if (today == null) return;
        float minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE) + now.get(Calendar.SECOND) / 60f;
        PrayerTimes.Next next = PrayerTimes.next(today, src.times().day(now.get(Calendar.DAY_OF_MONTH) + 1), minute);
        float left = next.at - minute;
        String key = now.get(Calendar.DAY_OF_YEAR) + ":" + next.index + ":" + next.at;
        if (left <= 0 || left > minutesBefore || key.equals(reminded)) return;
        reminded = key;
        String text = activity.getString(R.string.prayer_reminder, PrayerTimes.NAMES[next.index],
                (int) Math.ceil(left), PrayerTimes.clock(next.at, DateFormat.is24HourFormat(activity)));
        showBanner(text);
    }

    void showBanner(String text) {
        LinearLayout banner = new LinearLayout(activity);
        banner.setOrientation(LinearLayout.HORIZONTAL);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setPadding(dp(18), 0, dp(20), 0);
        glass(banner, 24);
        ImageView moon = new ImageView(activity);
        moon.setImageResource(R.drawable.ic_prayer);
        banner.addView(moon, new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView t = text(16, 0xFFFFFFFF, 400);
        t.setText(text);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.leftMargin = dp(10);
        banner.addView(t, tl);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = dp(18);
        root.addView(banner, lp);
        banner.setTranslationY(-dp(80));
        banner.animate().translationY(0f).setDuration(320).withEndAction(() ->
                banner.animate().translationY(-dp(80)).setStartDelay(10_000).setDuration(260)
                        .withEndAction(() -> root.removeView(banner)).start()).start();
    }

    // ---- helpers ----------------------------------------------------------------------------------

    private void glass(View v, float radiusDp) {
        v.setBackground(new GlassDrawable(v, radiusDp * density, density, null));
        Backdrop.watch(v);
    }

    private TextView text(float sp, int color, int weight) {
        TextView t = new TextView(activity);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        // Outfit is a variable font: the weight is an axis (Typeface.create with a weight needs Android 9).
        t.setTypeface(font);
        t.setFontVariationSettings("'wght' " + weight);
        t.setIncludeFontPadding(false);
        return t;
    }

    private GradientDrawable round(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusDp * density);
        return d;
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
