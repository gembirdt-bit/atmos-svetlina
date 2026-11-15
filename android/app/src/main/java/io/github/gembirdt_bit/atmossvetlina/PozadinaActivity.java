package io.github.gembirdt_bit.atmossvetlina;

import android.app.Activity;
import android.os.Bundle;

/**
 * Nevidljivo pokretanje: samo pokrene servis i odmah se zatvori, bez ekrana.
 * Namenjeno za "Autostart" listu jedinice, da se ne otvara početni ekran.
 */
public class PozadinaActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            if (Svetlina.on(this)) SvetlinaService.start(this);
        } catch (Exception ignored) {
        }
        finish();
        overridePendingTransition(0, 0);
    }
}
