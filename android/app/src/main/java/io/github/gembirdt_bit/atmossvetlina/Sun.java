package io.github.gembirdt_bit.atmossvetlina;

import java.util.Calendar;

/** Položaj sunca (visina iznad horizonta) i vremena izlaska/zalaska, bez interneta. */
public final class Sun {
    private Sun() {}

    /** Visina sunca u stepenima za dati trenutak (ms) i lokaciju. */
    public static double elevation(long millis, double lat, double lon) {
        double n = millis / 86400000.0 + 2440587.5 - 2451545.0;
        double L = norm(280.460 + 0.9856474 * n);
        double g = Math.toRadians(norm(357.528 + 0.9856003 * n));
        double lambda = Math.toRadians(L + 1.915 * Math.sin(g) + 0.020 * Math.sin(2 * g));
        double eps = Math.toRadians(23.439 - 0.0000004 * n);
        double ra = Math.toDegrees(Math.atan2(Math.cos(eps) * Math.sin(lambda), Math.cos(lambda)));
        double dec = Math.asin(Math.sin(eps) * Math.sin(lambda));
        double gmst = (18.697374558 + 24.06570982441908 * n) % 24.0;
        if (gmst < 0) gmst += 24.0;
        double h = Math.toRadians(gmst * 15.0 + lon - ra);
        double la = Math.toRadians(lat);
        double s = Math.sin(la) * Math.sin(dec) + Math.cos(la) * Math.cos(dec) * Math.cos(h);
        return Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, s))));
    }

    /**
     * Trenutak (ms) kada sunce danas prelazi zadatu visinu.
     * rising=true za jutarnji prelaz (izlazak), false za večernji (zalazak). -1 ako ne prelazi.
     */
    public static long crossing(long now, double lat, double lon, double angle, boolean rising) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long start = c.getTimeInMillis();
        double prev = elevation(start, lat, lon) - angle;
        for (int m = 1; m <= 1440; m++) {
            long t = start + m * 60000L;
            double cur = elevation(t, lat, lon) - angle;
            if (rising && prev < 0 && cur >= 0) return t;
            if (!rising && prev >= 0 && cur < 0) return t;
            prev = cur;
        }
        return -1;
    }

    private static double norm(double d) {
        d %= 360.0;
        return d < 0 ? d + 360.0 : d;
    }
}
