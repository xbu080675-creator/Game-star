package com.xbu.esportscenter.privileged;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

/**
 * Process-local bootstrap for the shoulder calibration privileged adapter.
 *
 * The provider exposes no data surface. It only observes Activity lifecycle so raw input reading
 * exists while BootMainActivity is resumed and immediately stops when it leaves the foreground.
 */
public final class RedMagicShoulderBootstrapProvider extends ContentProvider {
    private static final String TAG = "[GSB-SHOULDER]";
    private static final String BOOT_ACTIVITY = "com.xbu.esportscenter.BootMainActivity";
    private static final String PREFS_BOOT = "gsb.boot.profile";
    private static final String PREF_SHOULDER_CALIBRATED = "shoulder_calibrated_v1";
    private static final String PREF_SAR_MIGRATION_DONE = "shoulder_sar_migration_0427_v1";
    private static final String PREF_AWAKENING_MIGRATION_DONE = "hardware_awakening_migration_0427_v2";
    private static final String PREF_MOTOR_MIGRATION_DONE = "hardware_motor_migration_0427_v3";
    private static final String PREF_REALISTIC_VISUAL_MIGRATION_DONE = "hardware_realistic_visual_migration_0427_v4";
    private static final String PREF_PLAYGROUND_MIGRATION_DONE = "hardware_playground_migration_0427_v5";
    private static final String PREF_CINEMATIC_PLAYGROUND_MIGRATION_DONE =
            "hardware_playground_cinematic_migration_0427_v6";
    private static final String PREF_ASSET_REBUILD_MIGRATION_DONE =
            "hardware_playground_asset_rebuild_migration_0427_v7";
    private static final String PREF_MODEL_PIPELINE_MIGRATION_DONE =
            "hardware_playground_model_pipeline_migration_0427_v8";
    private static final String PREF_CORE_GATE_MIGRATION_DONE =
            "hardware_playground_core_gate_migration_0427_v9";

    private RedMagicShoulderPrivilegedAdapter adapter;
    private Application.ActivityLifecycleCallbacks callbacks;

    @Override
    public boolean onCreate() {
        if (getContext() == null) return false;
        Application application = (Application) getContext().getApplicationContext();
        forceOneSarCalibrationAfterPrototype(application);
        forceOneHardwareAwakeningAfterPrototype(application);
        forceOneMotorBackedAwakeningAfterPrototype(application);
        forceOneRealisticAwakeningAfterPrototype(application);
        forceOnePlayableHardwarePlayground(application);
        forceOneCinematicHardwarePlayground(application);
        forceOneAssetRebuiltHardwarePlayground(application);
        forceOneModelPipelineHardwarePlayground(application);
        forceOneCoreGatedHardwarePlayground(application);
        adapter = new RedMagicShoulderPrivilegedAdapter(application);
        callbacks = new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle state) {
            }

            @Override
            public void onActivityStarted(Activity activity) {
            }

            @Override
            public void onActivityResumed(Activity activity) {
                if (isBootActivity(activity) && adapter != null) adapter.activate();
            }

            @Override
            public void onActivityPaused(Activity activity) {
                if (isBootActivity(activity) && adapter != null) adapter.deactivate();
            }

            @Override
            public void onActivityStopped(Activity activity) {
                if (isBootActivity(activity) && adapter != null) adapter.deactivate();
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
                if (isBootActivity(activity) && adapter != null) adapter.deactivate();
            }
        };
        application.registerActivityLifecycleCallbacks(callbacks);
        Log.i(TAG, "shoulder calibration lifecycle bootstrap ready");
        return true;
    }

    private static void forceOneSarCalibrationAfterPrototype(Application application) {
        SharedPreferences prefs = application.getSharedPreferences(PREFS_BOOT, Application.MODE_PRIVATE);
        if (prefs.getBoolean(PREF_SAR_MIGRATION_DONE, false)) return;
        prefs.edit()
                .putBoolean(PREF_SHOULDER_CALIBRATED, false)
                .putBoolean(PREF_SAR_MIGRATION_DONE, true)
                .apply();
        Log.i(TAG, "one-time SAR calibration migration applied");
    }

    private static void forceOneHardwareAwakeningAfterPrototype(Application application) {
        SharedPreferences prefs = application.getSharedPreferences(PREFS_BOOT, Application.MODE_PRIVATE);
        if (prefs.getBoolean(PREF_AWAKENING_MIGRATION_DONE, false)) return;
        prefs.edit()
                .putBoolean(PREF_SHOULDER_CALIBRATED, false)
                .putBoolean(PREF_AWAKENING_MIGRATION_DONE, true)
                .apply();
        Log.i(TAG, "one-time hardware awakening migration applied");
    }

    private static void forceOneMotorBackedAwakeningAfterPrototype(Application application) {
        SharedPreferences prefs = application.getSharedPreferences(PREFS_BOOT, Application.MODE_PRIVATE);
        if (prefs.getBoolean(PREF_MOTOR_MIGRATION_DONE, false)) return;
        prefs.edit()
                .putBoolean(PREF_SHOULDER_CALIBRATED, false)
                .putBoolean(PREF_MOTOR_MIGRATION_DONE, true)
                .apply();
        Log.i(TAG, "one-time physical motor awakening migration applied");
    }

    private static void forceOneRealisticAwakeningAfterPrototype(Application application) {
        SharedPreferences prefs = application.getSharedPreferences(PREFS_BOOT, Application.MODE_PRIVATE);
        if (prefs.getBoolean(PREF_REALISTIC_VISUAL_MIGRATION_DONE, false)) return;
        prefs.edit()
                .putBoolean(PREF_SHOULDER_CALIBRATED, false)
                .putBoolean(PREF_REALISTIC_VISUAL_MIGRATION_DONE, true)
                .apply();
        Log.i(TAG, "one-time realistic hardware awakening migration applied");
    }

    private static void forceOnePlayableHardwarePlayground(Application application) {
        SharedPreferences prefs = application.getSharedPreferences(PREFS_BOOT, Application.MODE_PRIVATE);
        if (prefs.getBoolean(PREF_PLAYGROUND_MIGRATION_DONE, false)) return;
        prefs.edit()
                .putBoolean(PREF_SHOULDER_CALIBRATED, false)
                .putBoolean(PREF_PLAYGROUND_MIGRATION_DONE, true)
                .apply();
        Log.i(TAG, "one-time hardware playground migration applied");
    }

    private static void forceOneCinematicHardwarePlayground(Application application) {
        SharedPreferences prefs = application.getSharedPreferences(PREFS_BOOT, Application.MODE_PRIVATE);
        if (prefs.getBoolean(PREF_CINEMATIC_PLAYGROUND_MIGRATION_DONE, false)) return;
        prefs.edit()
                .putBoolean(PREF_SHOULDER_CALIBRATED, false)
                .putBoolean(PREF_CINEMATIC_PLAYGROUND_MIGRATION_DONE, true)
                .apply();
        Log.i(TAG, "one-time cinematic hardware playground migration applied");
    }

    private static void forceOneAssetRebuiltHardwarePlayground(Application application) {
        SharedPreferences prefs = application.getSharedPreferences(PREFS_BOOT, Application.MODE_PRIVATE);
        if (prefs.getBoolean(PREF_ASSET_REBUILD_MIGRATION_DONE, false)) return;
        prefs.edit()
                .putBoolean(PREF_SHOULDER_CALIBRATED, false)
                .putBoolean(PREF_ASSET_REBUILD_MIGRATION_DONE, true)
                .apply();
        Log.i(TAG, "one-time hardware playground asset rebuild migration applied");
    }

    private static void forceOneModelPipelineHardwarePlayground(Application application) {
        SharedPreferences prefs = application.getSharedPreferences(PREFS_BOOT, Application.MODE_PRIVATE);
        if (prefs.getBoolean(PREF_MODEL_PIPELINE_MIGRATION_DONE, false)) return;
        prefs.edit()
                .putBoolean(PREF_SHOULDER_CALIBRATED, false)
                .putBoolean(PREF_MODEL_PIPELINE_MIGRATION_DONE, true)
                .apply();
        Log.i(TAG, "one-time hardware playground model pipeline migration applied");
    }

    /**
     * v9 moves playground progression and final-grip authorization into Core. Reset once so an
     * install-over test cannot silently take the previously calibrated FAST path and skip the gate.
     */
    private static void forceOneCoreGatedHardwarePlayground(Application application) {
        SharedPreferences prefs = application.getSharedPreferences(PREFS_BOOT, Application.MODE_PRIVATE);
        if (prefs.getBoolean(PREF_CORE_GATE_MIGRATION_DONE, false)) return;
        prefs.edit()
                .putBoolean(PREF_SHOULDER_CALIBRATED, false)
                .putBoolean(PREF_CORE_GATE_MIGRATION_DONE, true)
                .apply();
        Log.i(TAG, "one-time hardware playground core-gate migration applied");
    }

    private static boolean isBootActivity(Activity activity) {
        return activity != null && BOOT_ACTIVITY.equals(activity.getClass().getName());
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
