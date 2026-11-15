package io.github.gembirdt_bit.atmossvetlina;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Pokreće servis pri paljenju jedinice i posle ažuriranja aplikacije. */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (Svetlina.on(context)) {
            try {
                SvetlinaService.start(context);
            } catch (Exception ignored) {
            }
        }
    }
}
