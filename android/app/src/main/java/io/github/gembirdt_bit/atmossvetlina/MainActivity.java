package io.github.gembirdt_bit.atmossvetlina;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(16, 20, 28);
    private static final int CARD = Color.rgb(28, 34, 46);
    private static final int ACCENT = Color.rgb(255, 176, 32);
    private static final int TEXT = Color.rgb(235, 238, 245);
    private static final int MUTED = Color.rgb(150, 158, 175);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status, permText, locText, dayLabel, nightLabel;
    private View permCard;
    private EditText latEdit, lonEdit;
    private Button pauseBtn, batteryBtn;

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            updateStatus();
            handler.postDelayed(this, 5000);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(BG);
            getWindow().setNavigationBarColor(BG);
        }
        setContentView(build());
    }

    @Override protected void onResume() {
        super.onResume();
        if (Svetlina.on(this) && Svetlina.canWrite(this)) SvetlinaService.start(this);
        handler.post(refresh);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    // ---------- izgled ----------

    private View build() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(24));
        sv.addView(root, new ViewGroup.LayoutParams(-1, -2));

        TextView title = text("☀  Atmos Svetlina", 26, TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);
        TextView sub = text("Svetlina ekrana prema izlasku i zalasku sunca", 15, MUTED);
        sub.setPadding(0, dp(2), 0, dp(12));
        root.addView(sub);

        // dozvola
        LinearLayout pc = card(root);
        permCard = pc;
        permText = text("Aplikaciji treba dozvola da menja svetlinu ekrana.", 16, TEXT);
        pc.addView(permText);
        Button permBtn = button("Dozvoli menjanje svetline", true);
        permBtn.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 23) {
                Intent i = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:" + getPackageName()));
                try { startActivity(i); } catch (Exception e) { toast("Otvori ručno: Podešavanja → Aplikacije → Atmos Svetlina → Menjanje sistemskih podešavanja"); }
            }
        });
        pc.addView(permBtn);

        // stanje
        LinearLayout sc = card(root);
        status = text("", 17, TEXT);
        status.setLineSpacing(0, 1.25f);
        sc.addView(status);

        // glavna podešavanja
        LinearLayout mc = card(root);
        Switch onSw = sw("Automatska svetlina", Svetlina.on(this));
        onSw.setOnCheckedChangeListener((CompoundButton bt, boolean c) -> {
            Svetlina.prefs(this).edit().putBoolean(Svetlina.K_ON, c).apply();
            if (c) SvetlinaService.start(this);
            else SvetlinaService.send(this, SvetlinaService.ACTION_STOP);
            updateStatus();
        });
        mc.addView(onSw);

        dayLabel = text("", 17, TEXT);
        dayLabel.setPadding(0, dp(14), 0, 0);
        mc.addView(dayLabel);
        mc.addView(slider(Svetlina.K_DAY, Svetlina.day(this), dayLabel, "☀  Dan"));

        nightLabel = text("", 17, TEXT);
        nightLabel.setPadding(0, dp(14), 0, 0);
        mc.addView(nightLabel);
        mc.addView(slider(Svetlina.K_NIGHT, Svetlina.night(this), nightLabel, "☾  Noć"));

        TextView hint = text("Dok pomeraš klizač, ekran odmah pokazuje tu svetlinu.", 14, MUTED);
        hint.setPadding(0, dp(4), 0, dp(8));
        mc.addView(hint);

        Switch holdSw = sw("Vrati moju svetlinu ako je jedinica promeni (npr. kad upališ svetla)",
                Svetlina.hold(this));
        holdSw.setOnCheckedChangeListener((CompoundButton bt, boolean c) ->
                Svetlina.prefs(this).edit().putBoolean(Svetlina.K_HOLD, c).apply());
        mc.addView(holdSw);

        pauseBtn = button("", false);
        pauseBtn.setOnClickListener(v -> {
            SvetlinaService.send(this, Svetlina.paused(this)
                    ? SvetlinaService.ACTION_RESUME : SvetlinaService.ACTION_PAUSE);
            handler.postDelayed(this::updateStatus, 300);
        });
        mc.addView(pauseBtn);

        // lokacija
        LinearLayout lc = card(root);
        TextView lt = text("Lokacija", 18, TEXT);
        lt.setTypeface(Typeface.DEFAULT_BOLD);
        lc.addView(lt);
        locText = text("", 15, MUTED);
        locText.setPadding(0, dp(4), 0, dp(4));
        lc.addView(locText);
        Button gps = button("Uzmi lokaciju sa GPS-a", true);
        gps.setOnClickListener(v -> askLocation());
        lc.addView(gps);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, 0);
        latEdit = edit("Geo. širina");
        lonEdit = edit("Geo. dužina");
        row.addView(latEdit, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(lonEdit, new LinearLayout.LayoutParams(0, -2, 1));
        Button save = button("Sačuvaj", false);
        save.setOnClickListener(v -> saveManualLocation());
        row.addView(save, new LinearLayout.LayoutParams(-2, -2));
        lc.addView(row);
        fillLocEdits();

        // ostalo
        LinearLayout oc = card(root);
        TextView batTxt = text("Da radi pouzdano u pozadini, isključi uštedu baterije za ovu aplikaciju.", 15, MUTED);
        oc.addView(batTxt);
        batteryBtn = button("Isključi uštedu baterije", false);
        batteryBtn.setOnClickListener(v -> askBattery());
        oc.addView(batteryBtn);

        String ver = "";
        try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) {}
        TextView foot = text("Verzija " + ver, 13, MUTED);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, dp(16), 0, 0);
        root.addView(foot);

        updateStatus();
        return sv;
    }

    private SeekBar slider(String key, int value, TextView label, String name) {
        SeekBar s = new SeekBar(this);
        s.setMax(99);
        s.setProgress(value - 1);
        s.setPadding(dp(8), dp(14), dp(8), dp(14));
        label.setText(name + ":  " + value + "%");
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                int pct = p + 1;
                label.setText(name + ":  " + pct + "%");
                if (fromUser) {
                    Svetlina.previewUntil = System.currentTimeMillis() + 4000;
                    Svetlina.applyPercent(MainActivity.this, pct);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {
                Svetlina.previewUntil = System.currentTimeMillis() + 4000;
                Svetlina.applyPercent(MainActivity.this, sb.getProgress() + 1);
            }
            @Override public void onStopTrackingTouch(SeekBar sb) {
                Svetlina.prefs(MainActivity.this).edit().putInt(key, sb.getProgress() + 1).apply();
                // kratko zadrži pregled, pa vrati na pravu svetlinu
                handler.postDelayed(() -> {
                    Svetlina.previewUntil = 0;
                    if (Svetlina.on(MainActivity.this)) {
                        SvetlinaService.send(MainActivity.this, SvetlinaService.ACTION_APPLY);
                    }
                    updateStatus();
                }, 1500);
                Svetlina.previewUntil = System.currentTimeMillis() + 2000;
            }
        });
        return s;
    }

    // ---------- stanje ----------

    private void updateStatus() {
        if (status == null) return;
        boolean can = Svetlina.canWrite(this);
        permCard.setVisibility(can ? View.GONE : View.VISIBLE);

        long now = System.currentTimeMillis();
        double lat = Svetlina.lat(this), lon = Svetlina.lon(this);
        double el = Sun.elevation(now, lat, lon);
        long rise = Sun.crossing(now, lat, lon, -0.833, true);
        long set = Sun.crossing(now, lat, lon, -0.833, false);
        long dimStart = Sun.crossing(now, lat, lon, Svetlina.dayAngle(this), false);
        long dimEnd = Sun.crossing(now, lat, lon, Svetlina.nightAngle(this), false);
        long brStart = Sun.crossing(now, lat, lon, Svetlina.nightAngle(this), true);
        long brEnd = Sun.crossing(now, lat, lon, Svetlina.dayAngle(this), true);
        int target = Svetlina.targetPercent(this, now);
        int raw = Svetlina.currentRaw(this);
        int curPct = raw < 0 ? -1 : Math.round(raw * 100f / 255f);

        StringBuilder sb = new StringBuilder();
        if (!Svetlina.on(this)) sb.append("Automatska svetlina je ISKLJUČENA\n");
        else if (Svetlina.paused(this)) sb.append("Pauzirano do ")
                .append(SvetlinaService.hm(Svetlina.prefs(this).getLong(Svetlina.K_PAUSE, 0))).append('\n');
        sb.append("Sada:  ").append(target).append("%   (ekran: ")
                .append(curPct < 0 ? "?" : curPct + "%").append(")\n");
        sb.append("Sunce:  ").append(String.format(Locale.US, "%.1f°", el))
                .append(el > 0 ? " iznad horizonta" : " ispod horizonta").append('\n');
        sb.append("Izlazak ").append(SvetlinaService.hm(rise))
                .append("   ·   Zalazak ").append(SvetlinaService.hm(set)).append('\n');
        sb.append("Jutro: pojačava ").append(SvetlinaService.hm(brStart)).append("–").append(SvetlinaService.hm(brEnd))
                .append("\nVeče: smanjuje ").append(SvetlinaService.hm(dimStart)).append("–").append(SvetlinaService.hm(dimEnd));
        status.setText(sb.toString());

        locText.setText(String.format(Locale.US, "%.4f, %.4f", lat, lon)
                + (Svetlina.prefs(this).contains(Svetlina.K_LAT) ? "" : "  (podrazumevano: Beograd)"));

        boolean paused = Svetlina.paused(this);
        pauseBtn.setText(paused ? "Nastavi automatsku svetlinu" : "Pauziraj 1 sat");
        pauseBtn.setVisibility(Svetlina.on(this) ? View.VISIBLE : View.GONE);

        boolean ignoring = true;
        if (Build.VERSION.SDK_INT >= 23) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            ignoring = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        }
        ((View) batteryBtn.getParent()).setVisibility(ignoring ? View.GONE : View.VISIBLE);
    }

    // ---------- lokacija ----------

    private void askLocation() {
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, 7);
            return;
        }
        fetchLocation();
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == 7) {
            for (int r : res) if (r == PackageManager.PERMISSION_GRANTED) { fetchLocation(); return; }
            toast("Bez dozvole — upiši lokaciju ručno");
        }
    }

    @SuppressWarnings({"deprecation", "MissingPermission"})
    private void fetchLocation() {
        if (SvetlinaService.updateLocation(this)) {
            toast("Lokacija sačuvana");
            fillLocEdits();
            updateStatus();
            return;
        }
        try {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            String provider = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
            toast("Čekam GPS signal…");
            lm.requestSingleUpdate(provider, new LocationListener() {
                @Override public void onLocationChanged(Location l) {
                    Svetlina.prefs(MainActivity.this).edit()
                            .putFloat(Svetlina.K_LAT, (float) l.getLatitude())
                            .putFloat(Svetlina.K_LON, (float) l.getLongitude()).apply();
                    toast("Lokacija sačuvana");
                    fillLocEdits();
                    updateStatus();
                }
                @Override public void onStatusChanged(String p, int s, Bundle e) {}
                @Override public void onProviderEnabled(String p) {}
                @Override public void onProviderDisabled(String p) {}
            }, Looper.getMainLooper());
        } catch (Exception e) {
            toast("GPS nije dostupan — upiši lokaciju ručno");
        }
    }

    private void saveManualLocation() {
        try {
            float la = Float.parseFloat(latEdit.getText().toString().trim().replace(',', '.'));
            float lo = Float.parseFloat(lonEdit.getText().toString().trim().replace(',', '.'));
            if (la < -90 || la > 90 || lo < -180 || lo > 180) throw new NumberFormatException();
            Svetlina.prefs(this).edit().putFloat(Svetlina.K_LAT, la).putFloat(Svetlina.K_LON, lo).apply();
            toast("Lokacija sačuvana");
            updateStatus();
        } catch (NumberFormatException e) {
            toast("Neispravne koordinate (npr. 44.81 i 20.46)");
        }
    }

    private void fillLocEdits() {
        latEdit.setText(String.format(Locale.US, "%.4f", Svetlina.lat(this)));
        lonEdit.setText(String.format(Locale.US, "%.4f", Svetlina.lon(this)));
    }

    private void askBattery() {
        if (Build.VERSION.SDK_INT < 23) return;
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
            catch (Exception ignored) { toast("Nije dostupno na ovom uređaju"); }
        }
    }

    // ---------- pomoćno ----------

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(14));
        c.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10);
        parent.addView(c, lp);
        return c;
    }

    private Button button(String s, boolean primary) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTextColor(primary ? Color.BLACK : TEXT);
        GradientDrawable g = new GradientDrawable();
        g.setColor(primary ? ACCENT : Color.rgb(48, 56, 72));
        g.setCornerRadius(dp(10));
        b.setBackground(g);
        b.setMinHeight(dp(52));
        b.setPadding(dp(16), 0, dp(16), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    private Switch sw(String s, boolean checked) {
        Switch w = new Switch(this);
        w.setText(s);
        w.setTextSize(17);
        w.setTextColor(TEXT);
        w.setChecked(checked);
        w.setMinHeight(dp(52));
        return w;
    }

    private EditText edit(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextColor(TEXT);
        e.setHintTextColor(MUTED);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        return e;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
