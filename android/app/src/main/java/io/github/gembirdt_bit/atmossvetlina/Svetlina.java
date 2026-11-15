package io.github.gembirdt_bit.atmossvetlina;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;

/** Podešavanja i računanje svetline prema položaju sunca. */
public final class Svetlina {
    private Svetlina() {}

    public static final String PREFS = "svetlina";
    public static final String K_ON = "ukljuceno";
    public static final String K_DAY = "dan";          // %
    public static final String K_NIGHT = "noc";        // %
    public static final String K_LAT = "lat";
    public static final String K_LON = "lon";
    public static final String K_HOLD = "drzi";        // vrati svetlinu ako je jedinica promeni
    public static final String K_PAUSE = "pauza_do";   // ms
    public static final String K_DAY_ANGLE = "ugao_dan";
    public static final String K_NIGHT_ANGLE = "ugao_noc";

    /** Dok korisnik pomera klizač, servis ne dira svetlinu. */
    public static volatile long previewUntil = 0;

    public static boolean previewing() { return System.currentTimeMillis() < previewUntil; }

    /** Podrazumevana lokacija: Beograd. */
    public static final float DEF_LAT = 44.8125f;
    public static final float DEF_LON = 20.4612f;

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean on(Context c) { return prefs(c).getBoolean(K_ON, true); }
    public static int day(Context c) { return prefs(c).getInt(K_DAY, 100); }
    public static int night(Context c) { return prefs(c).getInt(K_NIGHT, 25); }
    public static boolean hold(Context c) { return prefs(c).getBoolean(K_HOLD, true); }
    public static double lat(Context c) { return prefs(c).getFloat(K_LAT, DEF_LAT); }
    public static double lon(Context c) { return prefs(c).getFloat(K_LON, DEF_LON); }
    /** Sunce iznad ovog ugla = puna dnevna svetlina. */
    public static double dayAngle(Context c) { return prefs(c).getFloat(K_DAY_ANGLE, 4f); }
    /** Sunce ispod ovog ugla = puna noćna svetlina. */
    public static double nightAngle(Context c) { return prefs(c).getFloat(K_NIGHT_ANGLE, -5f); }

    public static boolean paused(Context c) {
        return System.currentTimeMillis() < prefs(c).getLong(K_PAUSE, 0);
    }

    /** Udeo dana 0..1 (0 = noć, 1 = dan), sa blagim prelazom. */
    public static double dayFraction(Context c, long now) {
        double e = Sun.elevation(now, lat(c), lon(c));
        double lo = nightAngle(c), hi = dayAngle(c);
        double f = (e - lo) / (hi - lo);
        f = Math.max(0, Math.min(1, f));
        return f * f * (3 - 2 * f);
    }

    /** Ciljna svetlina u procentima za trenutak now. */
    public static int targetPercent(Context c, long now) {
        double f = dayFraction(c, now);
        return (int) Math.round(night(c) + (day(c) - night(c)) * f);
    }

    /** Procenat (1-100) u vrednost sistema (1-255). */
    public static int toRaw(int percent) {
        int p = Math.max(1, Math.min(100, percent));
        return Math.max(1, Math.round(p * 255f / 100f));
    }

    public static boolean canWrite(Context c) {
        if (Build.VERSION.SDK_INT < 23) return true;
        return Settings.System.canWrite(c);
    }

    public static int currentRaw(Context c) {
        try {
            return Settings.System.getInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
        } catch (Exception e) {
            return -1;
        }
    }

    /** Upisuje svetlinu (procenat). Vraća upisanu sistemsku vrednost ili -1. */
    public static int applyPercent(Context c, int percent) {
        if (!canWrite(c)) return -1;
        int raw = toRaw(percent);
        try {
            ContentResolver cr = c.getContentResolver();
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            if (currentRaw(c) != raw) {
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, raw);
            }
            return raw;
        } catch (Exception e) {
            return -1;
        }
    }
}
