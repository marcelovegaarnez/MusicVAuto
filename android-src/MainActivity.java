package com.marcelovega.automusic;

import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.WebView;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    /** true mientras la app está a la vista (lo usa BootReceiver para decidir si reintenta abrirla). */
    public static volatile boolean visible = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(MusicFolderPlugin.class);
        super.onCreate(savedInstanceState);
        getSharedPreferences("automusica", MODE_PRIVATE).edit().putLong("lastCreate", System.currentTimeMillis()).apply();
    }

    @Override
    public void onResume() { super.onResume(); visible = true; }

    @Override
    public void onPause() { visible = false; super.onPause(); }

    /** Teclas que llegan a la app (volante, botones del equipo): se avisan a la página para usarlas o mostrarlas en la prueba. */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getRepeatCount() == 0 && getBridge() != null) {
            final WebView wv = getBridge().getWebView();
            if (wv != null) {
                final String js = "window.__onKey&&window.__onKey(" + keyCode + ")";
                wv.post(() -> wv.evaluateJavascript(js, null));
            }
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_STOP:
            case KeyEvent.KEYCODE_HEADSETHOOK:
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }
}
