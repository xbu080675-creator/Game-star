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

    private RedMagicShoulderPrivilegedAdapter adapter;
    private Application.ActivityLifecycleCallbacks callbacks;

    @Override
    public boolean onCreate() {
        if (getContext() == null) return false;
        Application application = (Application) getContext().getApplicationContext();
        forceOneSarCalibrationAfterPrototype(application);
        forceOneHardwareAwakeningAfterPrototype(application);
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

    /**
     * The first 0.4.27 prototype could only finish through touch fallback on REDMAGIC 9 Pro+.
     * Reset that calibration exactly once so an in-place update actually exercises the new SAR
     * reader. The migration marker prevents future launches from repeatedly erasing calibration.
     */
    private static void forceOneSarCalibrationAfterPrototype(Application application) {
        SharedPreferences prefs = application.getSharedPreferences(PREFS_BOOT, Application.MODE_PRIVATE);
        if (prefs.getBoolean(PREF_SAR_MIGRATION_DONE, false)) return;
        prefs.edit()
                .putBoolean(PREF_SHOULDER_CALIBRATED, false)
                .putBoolean(PREF_SAR_MIGRATION_DONE, true)
                .apply();
        Log.i(TAG, "one-time SAR calibration migration applied");
    }

    /**
     * Users who already validated the SAR prototype must still see the redesigned Hardware
     * Awakening exactly once. This is a presentation/provisioning migration, not a permission or
     * REDMAGIC setting migration, and it never repeats after the first upgraded launch.
     */
    private static void forceOneHardwareAwakeningAfterPrototype(Application application) {
        SharedPreferences prefs = application.getSharedPreferences(PREFS_BOOT, Application.MODE_PRIVATE);
        if (prefs.getBoolean(PREF_AWAKENING_MIGRATION_DONE, false)) return;
        prefs.edit()
                .putBoolean(PREF_SHOULDER_CALIBRATED, false)
                .putBoolean(PREF_AWAKENING_MIGRATION_DONE, true)
                .apply();
        Log.i(TAG, "one-time hardware awakening migration applied");
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
