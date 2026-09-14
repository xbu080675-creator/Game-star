package com.xbu.esportscenter.platform.redmagic;

import android.content.Context;
import android.content.pm.PackageManager;

import java.io.File;

/**
 * Read-only REDMAGIC capability evidence probe.
 *
 * This class does not write Settings, invoke Shizuku, execute shell commands or mutate sysfs.
 * Presence of evidence is not treated as proof that Game Star Box may safely control it.
 */
public final class RedMagicCapabilityProbe {
    public static final String PACKAGE_GAME_SPACE = "cn.nubia.gamelauncher";
    public static final String PACKAGE_GAME_ASSIST = "cn.nubia.gameassist";

    private static final String FAN_ENABLE = "/sys/kernel/fan/fan_enable";
    private static final String FAN_SPEED = "/sys/kernel/fan/fan_speed_level";

    private final Context appContext;

    public RedMagicCapabilityProbe(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public RedMagicCapabilityProbeSnapshot probe(RedMagicSnapshot state) {
        boolean likely = state != null && state.likelyRedMagic;
        boolean settings = state != null && (
                state.gameSpaceSwitch != null || state.gameScene != null || state.gameMode != null
        );
        return new RedMagicCapabilityProbeSnapshot(
                likely,
                hasPackage(PACKAGE_GAME_SPACE),
                hasPackage(PACKAGE_GAME_ASSIST),
                settings,
                fileExists(FAN_ENABLE),
                fileExists(FAN_SPEED)
        );
    }

    private boolean hasPackage(String packageName) {
        try {
            appContext.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
            return false;
        }
    }

    private boolean fileExists(String path) {
        try {
            return new File(path).exists();
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
