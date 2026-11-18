package com.marcelovega.automusic;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Abre la app al encender la radio (si el usuario no lo desactivó en Configuración). */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        try {
            if (!c.getSharedPreferences("automusica", Context.MODE_PRIVATE).getBoolean("autostart", true)) return;
            Intent a = new Intent(c, MainActivity.class);
            a.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(a);
        } catch (Exception ignored) { }
    }
}
