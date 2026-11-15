package io.github.gembirdt_bit.atmossvetlina;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Radi u pozadini: svakog minuta podešava svetlinu prema suncu. */
public class SvetlinaService extends Service {
    public static final String ACTION_APPLY = "primeni";
    public static final String ACTION_PAUSE = "pauza";
    public static final String ACTION_RESUME = "nastavi";
    public static final String ACTION_STOP = "stop";

    private static final String CHANNEL = "svetlina";
    private static final int NOTIF_ID = 1;
    private static final long TICK_MS = 60_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int lastRaw = -1;
    private String lastText = "";
    private long lastLocCheck = 0;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            apply();
            handler.postDelayed(this, TICK_MS);
        }
    };

    private final Runnable applyNow = this::apply;

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            // posle buđenja jedinica često vrati svoju svetlinu — primeni ponovo
            handler.removeCallbacks(applyNow);
            handler.postDelayed(applyNow, 1500);
            handler.postDelayed(applyNow, 6000);
        }
    };

    private ContentObserver observer;

    public static void start(Context c) {
        Intent i = new Intent(c, SvetlinaService.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
        else c.startService(i);
    }

    public static void send(Context c, String action) {
        Intent i = new Intent(c, SvetlinaService.class).setAction(action);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
        else c.startService(i);
    }

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Atmos Svetlina",
                    NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
        startForeground(NOTIF_ID, buildNotification("Pokretanje…"));

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screenReceiver, f);

        observer = new ContentObserver(handler) {
            @Override public void onChange(boolean selfChange) {
                if (!Svetlina.on(SvetlinaService.this) || !Svetlina.hold(SvetlinaService.this)
                        || Svetlina.paused(SvetlinaService.this) || Svetlina.previewing()) return;
                int cur = Svetlina.currentRaw(SvetlinaService.this);
                if (lastRaw > 0 && cur != lastRaw) {
                    // jedinica (npr. pri paljenju svetala) je promenila svetlinu — vrati našu
                    handler.removeCallbacks(applyNow);
                    handler.postDelayed(applyNow, 1200);
                }
            }
        };
        getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), false, observer);

        handler.post(tick);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(a) || !Svetlina.on(this)) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_PAUSE.equals(a)) {
            Svetlina.prefs(this).edit()
                    .putLong(Svetlina.K_PAUSE, System.currentTimeMillis() + 60 * 60_000L).apply();
        } else if (ACTION_RESUME.equals(a)) {
            Svetlina.prefs(this).edit().putLong(Svetlina.K_PAUSE, 0).apply();
        }
        lastText = "";
        handler.removeCallbacks(applyNow);
        handler.post(applyNow);
        return START_STICKY;
    }

    private void apply() {
        long now = System.currentTimeMillis();
        if (now - lastLocCheck > 30 * 60_000L) {
            lastLocCheck = now;
            updateLocation(this);
        }
        if (Svetlina.previewing()) return;
        int pct = Svetlina.targetPercent(this, now);
        String text;
        if (!Svetlina.canWrite(this)) {
            text = "Nema dozvole za menjanje svetline — otvori aplikaciju";
        } else if (Svetlina.paused(this)) {
            long until = Svetlina.prefs(this).getLong(Svetlina.K_PAUSE, 0);
            text = "Pauzirano do " + hm(until);
            lastRaw = -1;
        } else {
            lastRaw = Svetlina.applyPercent(this, pct);
            long rise = Sun.crossing(now, Svetlina.lat(this), Svetlina.lon(this), -0.833, true);
            long set = Sun.crossing(now, Svetlina.lat(this), Svetlina.lon(this), -0.833, false);
            text = "Svetlina " + pct + "%  ·  izlazak " + hm(rise) + "  ·  zalazak " + hm(set);
        }
        if (!text.equals(lastText)) {
            lastText = text;
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.notify(NOTIF_ID, buildNotification(text));
        }
    }

    static String hm(long t) {
        if (t < 0) return "—";
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(t));
    }

    /** Uzima poslednju poznatu lokaciju (GPS/mreža), ako postoji dozvola. */
    static boolean updateLocation(Context c) {
        if (Build.VERSION.SDK_INT >= 23
                && c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        try {
            LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
            Location best = null;
            for (String p : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
            if (best == null) return false;
            Svetlina.prefs(c).edit()
                    .putFloat(Svetlina.K_LAT, (float) best.getLatitude())
                    .putFloat(Svetlina.K_LON, (float) best.getLongitude())
                    .apply();
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    private Notification buildNotification(String text) {
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), piFlags);
        boolean paused = Svetlina.paused(this);
        Intent act = new Intent(this, SvetlinaService.class)
                .setAction(paused ? ACTION_RESUME : ACTION_PAUSE);
        PendingIntent pause = Build.VERSION.SDK_INT >= 26
                ? PendingIntent.getForegroundService(this, 1, act, piFlags)
                : PendingIntent.getService(this, 1, act, piFlags);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Atmos Svetlina")
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .setShowWhen(false)
                .addAction(new Notification.Action.Builder(0,
                        paused ? "Nastavi" : "Pauza 1 h", pause).build());
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_MIN);
        return b.build();
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        try { unregisterReceiver(screenReceiver); } catch (Exception ignored) {}
        if (observer != null) getContentResolver().unregisterContentObserver(observer);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
