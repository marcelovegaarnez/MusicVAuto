package com.marcelovega.automusic;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

/** Abre la app al encender la radio (si el usuario no lo desactivó en Configuración).
 *  La radio puede mostrar su propio launcher justo después y tapar la app, por eso se reintenta
 *  dos veces (a los 7 s y a los 16 s) solo si la app no está visible. */
public class BootReceiver extends BroadcastReceiver {
    private static boolean enabled(Context c) {
        return c.getSharedPreferences("automusica", Context.MODE_PRIVATE).getBoolean("autostart", true);
    }

    private static void launch(Context c) {
        try {
            Intent a = new Intent(c, MainActivity.class);
            a.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            c.startActivity(a);
        } catch (Exception ignored) { }
    }

    @Override
    public void onReceive(Context c, Intent i) {
        try {
            final Context app = c.getApplicationContext();
            if (!enabled(app)) return;
            app.getSharedPreferences("automusica", Context.MODE_PRIVATE).edit().putLong("lastBoot", System.currentTimeMillis()).apply();
            launch(app);
            Handler h = new Handler(Looper.getMainLooper());
            final long[] delays = { 7000, 16000 };
            for (long d : delays) {
                h.postDelayed(new Runnable() {
                    @Override public void run() { if (enabled(app) && !MainActivity.visible) launch(app); }
                }, d);
            }
        } catch (Exception ignored) { }
    }
}
