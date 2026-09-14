package com.xbu.esportscenter.platform.redmagic;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import java.util.Locale;

/**
 * Read-only adapter for REDMAGIC/Nubia game-entry state.
 *
 * Security boundary: this class never writes Settings, never invokes Shizuku,
 * never executes shell commands and never launches arbitrary packages.
 */
public final class RedMagicEntryMonitor implements AutoCloseable {
    public interface Listener {
        void onSnapshot(RedMagicSnapshot snapshot);
    }

    public static final String CAPABILITY_ID = "platform.redmagic.entry.read";
    public static final String KEY_GAME_SPACE_SWITCH = "gcs_need_kill_game_launcher";
    public static final String KEY_GAME_SCENE = "nubia_game_scene";
    public static final String KEY_GAME_MODE = "nubia_game_mode";

    private static final String TAG = "[GSB-RM]";

    private final Context appContext;
    private final ContentResolver resolver;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean registered;

    private final ContentObserver observer = new ContentObserver(mainHandler) {
        @Override
        public void onChange(boolean selfChange) {
            emitSnapshot();
        }
    };

    public RedMagicEntryMonitor(Context context, Listener listener) {
        this.appContext = context.getApplicationContext();
        this.resolver = appContext.getContentResolver();
        this.listener = listener;
    }

    public synchronized void start() {
        if (registered) return;
        resolver.registerContentObserver(Settings.Global.getUriFor(KEY_GAME_SPACE_SWITCH), false, observer);
        resolver.registerContentObserver(Settings.Global.getUriFor(KEY_GAME_SCENE), false, observer);
        resolver.registerContentObserver(Settings.Global.getUriFor(KEY_GAME_MODE), false, observer);
        registered = true;
        emitSnapshot();
    }

    public RedMagicSnapshot readSnapshot() {
        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER;
        String brand = Build.BRAND == null ? "" : Build.BRAND;
        String model = Build.MODEL == null ? "" : Build.MODEL;
        String identity = (manufacturer + " " + brand + " " + model).toLowerCase(Locale.ROOT);
        boolean likelyRedMagic = identity.contains("nubia") || identity.contains("redmagic") || identity.contains("red magic");

        return new RedMagicSnapshot(
                likelyRedMagic,
                manufacturer,
                model,
                readGlobalInt(KEY_GAME_SPACE_SWITCH),
                readGlobalInt(KEY_GAME_SCENE),
                readGlobalInt(KEY_GAME_MODE)
        );
    }

    private Integer readGlobalInt(String key) {
        try {
            String raw = Settings.Global.getString(resolver, key);
            if (raw == null) return null;
            return Integer.parseInt(raw.trim());
        } catch (SecurityException e) {
            Log.w(TAG, "GSB-RM-ENTRY-READ-DENIED key=" + key);
            return null;
        } catch (RuntimeException e) {
            Log.w(TAG, "GSB-RM-ENTRY-READ-INVALID key=" + key);
            return null;
        }
    }

    private void emitSnapshot() {
        RedMagicSnapshot snapshot = readSnapshot();
        Log.i(TAG, "entry snapshot redmagic=" + snapshot.likelyRedMagic
                + " switch=" + snapshot.gameSpaceSwitch
                + " scene=" + snapshot.gameScene
                + " mode=" + snapshot.gameMode);
        if (listener != null) listener.onSnapshot(snapshot);
    }

    @Override
    public synchronized void close() {
        if (!registered) return;
        try {
            resolver.unregisterContentObserver(observer);
        } finally {
            registered = false;
        }
    }
}
