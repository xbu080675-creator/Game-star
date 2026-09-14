package com.xbu.esportscenter.platform.redmagic;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Read-only audit of exported components exposed by the REDMAGIC/Nubia game stack.
 *
 * This class never starts/binds/calls any discovered component. It only inspects package metadata
 * so later control adapters can be designed against evidence instead of guesses.
 */
public final class RedMagicNativeEntryAudit {
    private static final int MAX_CANDIDATES = 96;
    private static final String[] RELEVANT_TERMS = {
            "game", "assist", "perf", "boost", "power", "thermal", "mode",
            "clean", "kill", "memory", "mindsync", "process",
            "notify", "notification", "disturb", "dnd", "shield",
            "network", "wifi", "qos", "latency", "acceler",
            "fan", "charge", "shoulder", "trigger", "record", "capture", "fps", "overlay", "hud"
    };

    private final PackageManager packageManager;

    public RedMagicNativeEntryAudit(Context context) {
        this.packageManager = context.getApplicationContext().getPackageManager();
    }

    public RedMagicNativeEntryAuditSnapshot audit() {
        List<RedMagicNativeEntryAuditSnapshot.EntryPoint> candidates = new ArrayList<>();
        ScanResult gameSpace = scanPackage(RedMagicCapabilityProbe.PACKAGE_GAME_SPACE, candidates);
        ScanResult gameAssist = scanPackage(RedMagicCapabilityProbe.PACKAGE_GAME_ASSIST, candidates);
        return new RedMagicNativeEntryAuditSnapshot(
                gameSpace.visible,
                gameAssist.visible,
                gameSpace.exportedCount + gameAssist.exportedCount,
                candidates
        );
    }

    private ScanResult scanPackage(
            String packageName,
            List<RedMagicNativeEntryAuditSnapshot.EntryPoint> candidates
    ) {
        try {
            PackageInfo info = getPackageInfo(packageName);
            int exported = 0;
            exported += collectActivities(packageName, info.activities, candidates);
            exported += collectActivities(packageName, info.receivers, candidates, "receiver");
            exported += collectServices(packageName, info.services, candidates);
            exported += collectProviders(packageName, info.providers, candidates);
            return new ScanResult(true, exported);
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
            return new ScanResult(false, 0);
        }
    }

    private PackageInfo getPackageInfo(String packageName) throws PackageManager.NameNotFoundException {
        long flags = PackageManager.GET_ACTIVITIES
                | PackageManager.GET_RECEIVERS
                | PackageManager.GET_SERVICES
                | PackageManager.GET_PROVIDERS
                | PackageManager.GET_META_DATA;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(flags)
            );
        }
        //noinspection deprecation
        return packageManager.getPackageInfo(packageName, (int) flags);
    }

    private int collectActivities(
            String packageName,
            ActivityInfo[] items,
            List<RedMagicNativeEntryAuditSnapshot.EntryPoint> candidates
    ) {
        return collectActivities(packageName, items, candidates, "activity");
    }

    private int collectActivities(
            String packageName,
            ActivityInfo[] items,
            List<RedMagicNativeEntryAuditSnapshot.EntryPoint> candidates,
            String kind
    ) {
        if (items == null) return 0;
        int count = 0;
        for (ActivityInfo item : items) {
            if (item == null || !item.exported) continue;
            count++;
            maybeAdd(packageName, kind, item.name, item.permission, candidates);
        }
        return count;
    }

    private int collectServices(
            String packageName,
            ServiceInfo[] items,
            List<RedMagicNativeEntryAuditSnapshot.EntryPoint> candidates
    ) {
        if (items == null) return 0;
        int count = 0;
        for (ServiceInfo item : items) {
            if (item == null || !item.exported) continue;
            count++;
            maybeAdd(packageName, "service", item.name, item.permission, candidates);
        }
        return count;
    }

    private int collectProviders(
            String packageName,
            ProviderInfo[] items,
            List<RedMagicNativeEntryAuditSnapshot.EntryPoint> candidates
    ) {
        if (items == null) return 0;
        int count = 0;
        for (ProviderInfo item : items) {
            if (item == null || !item.exported) continue;
            count++;
            String permission = item.readPermission;
            if (permission == null || permission.isEmpty()) permission = item.writePermission;
            maybeAdd(packageName, "provider", item.name, permission, candidates);
        }
        return count;
    }

    private void maybeAdd(
            String packageName,
            String kind,
            String className,
            String permission,
            List<RedMagicNativeEntryAuditSnapshot.EntryPoint> candidates
    ) {
        if (candidates.size() >= MAX_CANDIDATES) return;
        String searchable = ((className == null ? "" : className) + " "
                + (permission == null ? "" : permission)).toLowerCase(Locale.ROOT);
        boolean relevant = false;
        for (String term : RELEVANT_TERMS) {
            if (searchable.contains(term)) {
                relevant = true;
                break;
            }
        }
        if (!relevant) return;
        candidates.add(new RedMagicNativeEntryAuditSnapshot.EntryPoint(
                packageName,
                kind,
                className,
                permission
        ));
    }

    private static final class ScanResult {
        final boolean visible;
        final int exportedCount;

        ScanResult(boolean visible, int exportedCount) {
            this.visible = visible;
            this.exportedCount = exportedCount;
        }
    }
}
