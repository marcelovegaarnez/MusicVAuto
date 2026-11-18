package com.marcelovega.automusic;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.DocumentsContract;
import android.view.View;
import android.view.WindowManager;

import androidx.activity.result.ActivityResult;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Plugin nativo: elegir carpeta (con permiso persistente), listar audios y reproducir con MediaPlayer.
 * Reproducir de forma nativa evita depender del WebView para leer content:// y es más ligero.
 */
@CapacitorPlugin(name = "MusicFolder")
public class MusicFolderPlugin extends Plugin {

    private static final String[] EXT = {".mp3", ".flac", ".m4a", ".aac", ".ogg", ".opus", ".wav", ".wma"};
    private static final int MAX_TRACKS = 20000;
    private static final int MAX_DEPTH = 12;

    private final Handler main = new Handler(Looper.getMainLooper());
    private MediaPlayer mp;
    private AudioManager am;
    private boolean pausedByFocus = false;

    @Override
    public void load() {
        final Activity a = getActivity();
        am = (AudioManager) a.getSystemService(Context.AUDIO_SERVICE);
        a.runOnUiThread(() -> {
            // Pantalla siempre encendida y modo inmersivo (útil con el teléfono en el soporte del auto)
            a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            a.getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN);
            // Sin las banderas LAYOUT_*: si la pantalla (p. ej. la de un auto) no puede ocultar sus barras,
            // la app se dibuja debajo de ellas en vez de quedar tapada.
        });
    }

    /* ---------------- Carpeta ---------------- */

    @PluginMethod
    public void pickFolder(PluginCall call) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(call, i, "pickResult");
    }

    @ActivityCallback
    private void pickResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) {
            call.reject("cancelado");
            return;
        }
        Uri tree = result.getData().getData();
        try {
            getContext().getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) { }
        String id = DocumentsContract.getTreeDocumentId(tree);
        String name = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        if (name.isEmpty()) name = "Almacenamiento interno";
        JSObject o = new JSObject();
        o.put("uri", tree.toString());
        o.put("name", name);
        call.resolve(o);
    }

    @PluginMethod
    public void listTracks(final PluginCall call) {
        final String s = call.getString("uri");
        if (s == null) { call.reject("falta uri"); return; }
        new Thread(() -> {
            try {
                Uri tree = Uri.parse(s);
                JSArray out = new JSArray();
                walk(tree, DocumentsContract.getTreeDocumentId(tree), "", out, 0);
                JSObject r = new JSObject();
                r.put("tracks", out);
                call.resolve(r);
            } catch (Exception e) {
                call.reject("No se pudo leer la carpeta: " + e.getMessage());
            }
        }).start();
    }

    private void walk(Uri tree, String docId, String path, JSArray out, int depth) {
        if (depth > MAX_DEPTH || out.length() >= MAX_TRACKS) return;
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId);
        Cursor c = null;
        try {
            c = getContext().getContentResolver().query(children, new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null);
            if (c == null) return;
            while (c.moveToNext() && out.length() < MAX_TRACKS) {
                String id = c.getString(0), name = c.getString(1), mime = c.getString(2);
                if (name == null) continue;
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    walk(tree, id, path + name + "/", out, depth + 1);
                } else if (isAudio(name, mime)) {
                    JSObject t = new JSObject();
                    t.put("uri", DocumentsContract.buildDocumentUriUsingTree(tree, id).toString());
                    t.put("name", name);
                    t.put("path", path + name);
                    out.put(t);
                }
            }
        } finally {
            if (c != null) c.close();
        }
    }

    private boolean isAudio(String name, String mime) {
        if (mime != null && mime.startsWith("audio/")) return true;
        String n = name.toLowerCase(Locale.ROOT);
        for (String e : EXT) if (n.endsWith(e)) return true;
        return false;
    }

    /* ---------------- Reproducción ---------------- */

    private final AudioManager.OnAudioFocusChangeListener focusListener = change -> {
        if (mp == null) return;
        try {
            if (change == AudioManager.AUDIOFOCUS_LOSS) {
                if (mp.isPlaying()) mp.pause();
                pausedByFocus = false;
                emitState(false);
            } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                if (mp.isPlaying()) { mp.pause(); pausedByFocus = true; }
                emitState(false);
            } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                mp.setVolume(0.3f, 0.3f);
            } else if (change == AudioManager.AUDIOFOCUS_GAIN) {
                mp.setVolume(1f, 1f);
                if (pausedByFocus) { mp.start(); pausedByFocus = false; emitState(true); }
            }
        } catch (IllegalStateException ignored) { }
    };

    @PluginMethod
    public void play(final PluginCall call) {
        final String s = call.getString("uri");
        if (s == null) { call.reject("falta uri"); return; }
        main.post(() -> {
            try {
                releasePlayer();
                mp = new MediaPlayer();
                mp.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
                mp.setWakeMode(getContext(), PowerManager.PARTIAL_WAKE_LOCK);
                mp.setDataSource(getContext(), Uri.parse(s));
                mp.setOnCompletionListener(m -> notifyListeners("ended", new JSObject()));
                mp.setOnErrorListener((m, what, extra) -> {
                    notifyListeners("error", new JSObject());
                    return true;
                });
                mp.setOnPreparedListener(m -> {
                    requestFocus();
                    m.start();
                    emitState(true);
                    call.resolve();
                });
                mp.prepareAsync();
            } catch (Exception e) {
                call.reject("No se pudo reproducir: " + e.getMessage());
            }
        });
    }

    private void emitState(boolean playing) {
        JSObject s = new JSObject();
        s.put("playing", playing);
        notifyListeners("state", s);
    }

    @PluginMethod
    public void pause(PluginCall call) {
        main.post(() -> {
            try {
                if (mp != null && mp.isPlaying()) mp.pause();
                pausedByFocus = false;
            } catch (IllegalStateException ignored) { }
            emitState(false);
            call.resolve();
        });
    }

    @PluginMethod
    public void resume(PluginCall call) {
        main.post(() -> {
            try {
                if (mp != null) { requestFocus(); mp.start(); emitState(true); }
            } catch (IllegalStateException ignored) { }
            call.resolve();
        });
    }

    @PluginMethod
    public void position(PluginCall call) {
        main.post(() -> {
            int pos = 0, dur = 0;
            try {
                if (mp != null) { pos = mp.getCurrentPosition(); dur = mp.getDuration(); }
            } catch (IllegalStateException ignored) { }
            JSObject r = new JSObject();
            r.put("pos", pos);
            r.put("dur", dur);
            call.resolve(r);
        });
    }


    /* ---------------- Letras (.lrc / .txt) ---------------- */

    @PluginMethod
    public void pickLyricsFolder(PluginCall call) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(call, i, "lyricsPickResult");
    }

    @ActivityCallback
    private void lyricsPickResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) {
            call.reject("cancelado");
            return;
        }
        Uri tree = result.getData().getData();
        boolean writable = true;
        try {
            getContext().getContentResolver().takePersistableUriPermission(tree,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception e) {
            writable = false;
            try { getContext().getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) { }
        }
        String id = DocumentsContract.getTreeDocumentId(tree);
        String name = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        if (name.isEmpty()) name = "Almacenamiento interno";
        JSObject o = new JSObject();
        o.put("uri", tree.toString());
        o.put("name", name);
        o.put("writable", writable);
        call.resolve(o);
    }

    @PluginMethod
    public void listLyrics(final PluginCall call) {
        final String s = call.getString("uri");
        if (s == null) { call.reject("falta uri"); return; }
        new Thread(() -> {
            try {
                Uri tree = Uri.parse(s);
                JSArray out = new JSArray();
                walkLyrics(tree, DocumentsContract.getTreeDocumentId(tree), out, 0);
                JSObject r = new JSObject();
                r.put("files", out);
                call.resolve(r);
            } catch (Exception e) {
                call.reject("No se pudo leer la carpeta de letras: " + e.getMessage());
            }
        }).start();
    }

    private void walkLyrics(Uri tree, String docId, JSArray out, int depth) {
        if (depth > MAX_DEPTH || out.length() >= MAX_TRACKS) return;
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId);
        Cursor c = null;
        try {
            c = getContext().getContentResolver().query(children, new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null);
            if (c == null) return;
            while (c.moveToNext() && out.length() < MAX_TRACKS) {
                String id = c.getString(0), name = c.getString(1), mime = c.getString(2);
                if (name == null) continue;
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    walkLyrics(tree, id, out, depth + 1);
                } else {
                    String low = name.toLowerCase(Locale.ROOT);
                    if (low.endsWith(".lrc") || low.endsWith(".txt")) {
                        JSObject t = new JSObject();
                        t.put("uri", DocumentsContract.buildDocumentUriUsingTree(tree, id).toString());
                        t.put("name", name);
                        out.put(t);
                    }
                }
            }
        } finally {
            if (c != null) c.close();
        }
    }

    @PluginMethod
    public void readText(final PluginCall call) {
        final String s = call.getString("uri");
        if (s == null) { call.reject("falta uri"); return; }
        new Thread(() -> {
            try (InputStream in = getContext().getContentResolver().openInputStream(Uri.parse(s))) {
                if (in == null) { call.reject("no se pudo abrir"); return; }
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n, total = 0;
                while ((n = in.read(buf)) > 0 && total < 1_000_000) { bo.write(buf, 0, n); total += n; }
                String txt = new String(bo.toByteArray(), StandardCharsets.UTF_8);
                if (txt.startsWith("\uFEFF")) txt = txt.substring(1);
                JSObject r = new JSObject();
                r.put("text", txt);
                call.resolve(r);
            } catch (Exception e) {
                call.reject("No se pudo leer: " + e.getMessage());
            }
        }).start();
    }

    /** Guarda texto. Si viene "uri" sobrescribe ese archivo; si no, crea "name" dentro de la carpeta "tree". */
    @PluginMethod
    public void writeText(final PluginCall call) {
        final String fileUri = call.getString("uri");
        final String tree = call.getString("tree");
        final String name = call.getString("name");
        final String text = call.getString("text", "");
        new Thread(() -> {
            try {
                Uri target;
                if (fileUri != null && !fileUri.isEmpty()) {
                    target = Uri.parse(fileUri);
                } else {
                    if (tree == null || name == null) { call.reject("faltan datos"); return; }
                    Uri t = Uri.parse(tree);
                    Uri parent = DocumentsContract.buildDocumentUriUsingTree(t, DocumentsContract.getTreeDocumentId(t));
                    target = DocumentsContract.createDocument(getContext().getContentResolver(), parent, "application/octet-stream", name);
                    if (target == null) { call.reject("no se pudo crear el archivo"); return; }
                }
                try (OutputStream os = getContext().getContentResolver().openOutputStream(target, "wt")) {
                    if (os == null) { call.reject("no se pudo escribir"); return; }
                    os.write(text.getBytes(StandardCharsets.UTF_8));
                }
                JSObject r = new JSObject();
                r.put("uri", target.toString());
                call.resolve(r);
            } catch (Exception e) {
                call.reject("No se pudo guardar: " + e.getMessage());
            }
        }).start();
    }

    @PluginMethod
    public void seek(PluginCall call) {
        final int ms = call.getInt("ms", 0);
        main.post(() -> {
            try { if (mp != null) mp.seekTo(Math.max(0, ms)); } catch (IllegalStateException ignored) { }
            call.resolve();
        });
    }

    @PluginMethod
    public void stop(PluginCall call) {
        main.post(() -> { releasePlayer(); call.resolve(); });
    }

    @SuppressWarnings("deprecation")
    private void requestFocus() {
        if (am != null) am.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
    }

    private void releasePlayer() {
        pausedByFocus = false;
        if (mp != null) {
            try { mp.release(); } catch (Exception ignored) { }
            mp = null;
        }
    }

    @Override
    protected void handleOnDestroy() {
        releasePlayer();
        super.handleOnDestroy();
    }
}
