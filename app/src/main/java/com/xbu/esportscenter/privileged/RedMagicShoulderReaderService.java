package com.xbu.esportscenter.privileged;

import android.content.Context;
import android.os.Process;
import android.util.Log;

import androidx.annotation.Keep;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Narrow Shizuku UserService for REDMAGIC/Nubia shoulder calibration.
 *
 * Security boundary:
 * - fixed, typed REDMAGIC calibration scene activation only;
 * - fixed /system/bin/settings and /system/bin/getevent executables only;
 * - only three whitelisted Global Settings keys can be changed, with original values restored;
 * - detects its own input device list; callers cannot supply paths, commands, keys or values;
 * - accepts only /dev/input/eventN nodes whose names match Nubia shoulder SAR evidence;
 * - emits only KEY_F7/KEY_F8 DOWN/UP semantics;
 * - no input injection, uinput, sendevent or generic shell surface.
 */
public final class RedMagicShoulderReaderService extends IRedMagicShoulderReader.Stub {
    private static final String TAG = "[GSB-SHOULDER]";
    private static final String GETEVENT = "/system/bin/getevent";
    private static final String SETTINGS = "/system/bin/settings";
    private static final String NUBIA_GAME_SCENE = "nubia_game_scene";
    private static final String NUBIA_GAME_MODE = "nubia_game_mode";
    private static final String CC_GAME_MIS_OPERATE = "cc_game_mis_operate";
    private static final Pattern EVENT_PATH = Pattern.compile("/dev/input/event\\d+");
    private static final Pattern HEX_KEY = Pattern.compile("EV_KEY\\s+([0-9a-fA-F]{4})\\s+([0-9a-fA-F]+)");
    private static final Pattern SAFE_SETTING_VALUE = Pattern.compile("-?\\d{1,12}");
    private static final long DETECT_TIMEOUT_SECONDS = 3L;
    private static final long SETTINGS_TIMEOUT_SECONDS = 2L;

    private final Object lock = new Object();
    private final Object sceneLock = new Object();
    private final List<java.lang.Process> readerProcesses = new ArrayList<>();
    private final List<Thread> readerThreads = new ArrayList<>();
    private final Map<String, String> originalSceneValues = new HashMap<>();
    private volatile boolean reading;
    private volatile boolean sceneApplied;
    private volatile IRedMagicShoulderCallback callback;

    public RedMagicShoulderReaderService() {
        Log.i(TAG, "reader UserService created uid=" + Process.myUid());
    }

    @Keep
    public RedMagicShoulderReaderService(Context context) {
        this();
    }

    /**
     * Activate only the minimal REDMAGIC game-scene bits needed to power the shoulder SAR sensors.
     * The caller cannot choose keys or values. Existing values are captured first for restoration.
     */
    @Override
    public int enableCalibrationScene() {
        synchronized (sceneLock) {
            if (sceneApplied) return 0;
            originalSceneValues.clear();
            try {
                captureOriginal(NUBIA_GAME_SCENE);
                captureOriginal(NUBIA_GAME_MODE);
                captureOriginal(CC_GAME_MIS_OPERATE);

                writeGlobalSetting(NUBIA_GAME_SCENE, "1");
                writeGlobalSetting(NUBIA_GAME_MODE, "1");
                writeGlobalSetting(CC_GAME_MIS_OPERATE, "0");
                sceneApplied = true;

                // Give Nubia's framework a short settle window before enumerating SAR nodes.
                try {
                    Thread.sleep(140L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                Log.i(TAG, "temporary REDMAGIC shoulder calibration scene enabled");
                return 0;
            } catch (Throwable t) {
                Log.w(TAG, "GSB-SHOULDER-SCENE-ACTIVATE-FAILED", t);
                restoreSceneLocked();
                return 1;
            }
        }
    }

    @Override
    public int restoreCalibrationScene() {
        synchronized (sceneLock) {
            return restoreSceneLocked() ? 0 : 1;
        }
    }

    @Override
    public String detectDevices() {
        List<String> devices = detectTrustedDevices();
        return String.join(",", devices);
    }

    @Override
    public void startReading(IRedMagicShoulderCallback callback) {
        if (callback == null) return;
        stopReading();

        List<String> devices = detectTrustedDevices();
        if (devices.isEmpty()) {
            sendStatus(callback, "GSB-SHOULDER-SAR-NOT-FOUND");
            Log.w(TAG, "no trusted REDMAGIC shoulder SAR device found");
            return;
        }

        synchronized (lock) {
            this.callback = callback;
            reading = true;
            for (String device : devices) {
                if (!isTrustedEventPath(device)) continue;
                startReaderLocked(device);
            }
        }

        sendStatus(callback, "GSB-SHOULDER-READING");
        Log.i(TAG, "started trusted SAR readers count=" + devices.size());
    }

    @Override
    public void stopReading() {
        List<java.lang.Process> processes;
        List<Thread> threads;
        synchronized (lock) {
            reading = false;
            callback = null;
            processes = new ArrayList<>(readerProcesses);
            threads = new ArrayList<>(readerThreads);
            readerProcesses.clear();
            readerThreads.clear();
        }

        for (java.lang.Process process : processes) {
            try {
                process.destroy();
                if (!process.waitFor(250L, TimeUnit.MILLISECONDS)) process.destroyForcibly();
            } catch (Throwable ignored) {
            }
        }
        for (Thread thread : threads) {
            try {
                thread.interrupt();
            } catch (Throwable ignored) {
            }
        }
        if (!processes.isEmpty()) Log.i(TAG, "shoulder readers stopped");
    }

    public void destroy() {
        stopReading();
        restoreCalibrationScene();
    }

    private void captureOriginal(String key) throws Exception {
        String value = readGlobalSetting(key);
        if (value != null && !SAFE_SETTING_VALUE.matcher(value).matches()) {
            throw new IllegalStateException("unexpected original value for " + key);
        }
        originalSceneValues.put(key, value);
    }

    private boolean restoreSceneLocked() {
        if (originalSceneValues.isEmpty() && !sceneApplied) return true;
        boolean ok = true;
        for (String key : new String[]{NUBIA_GAME_SCENE, NUBIA_GAME_MODE, CC_GAME_MIS_OPERATE}) {
            if (!originalSceneValues.containsKey(key)) continue;
            String original = originalSceneValues.get(key);
            try {
                if (original == null) deleteGlobalSetting(key);
                else writeGlobalSetting(key, original);
            } catch (Throwable t) {
                ok = false;
                Log.w(TAG, "GSB-SHOULDER-SCENE-RESTORE-FAILED key=" + key, t);
            }
        }
        if (ok) {
            originalSceneValues.clear();
            sceneApplied = false;
            Log.i(TAG, "REDMAGIC shoulder calibration scene restored");
        }
        return ok;
    }

    private static String readGlobalSetting(String key) throws Exception {
        requireAllowedSettingKey(key);
        java.lang.Process process = new ProcessBuilder(SETTINGS, "get", "global", key)
                .redirectErrorStream(true)
                .start();
        String line;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            line = reader.readLine();
        }
        if (!process.waitFor(SETTINGS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("settings get timeout");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("settings get exit=" + process.exitValue());
        }
        if (line == null) return null;
        String value = line.trim();
        return value.isEmpty() || "null".equalsIgnoreCase(value) ? null : value;
    }

    private static void writeGlobalSetting(String key, String value) throws Exception {
        requireAllowedSettingKey(key);
        if (value == null || !SAFE_SETTING_VALUE.matcher(value).matches()) {
            throw new IllegalArgumentException("unsafe settings value");
        }
        runSettingsMutation("put", key, value);
    }

    private static void deleteGlobalSetting(String key) throws Exception {
        requireAllowedSettingKey(key);
        runSettingsMutation("delete", key, null);
    }

    private static void runSettingsMutation(String operation, String key, String value) throws Exception {
        requireAllowedSettingKey(key);
        if (!"put".equals(operation) && !"delete".equals(operation)) {
            throw new IllegalArgumentException("unsupported settings operation");
        }
        ProcessBuilder builder = "put".equals(operation)
                ? new ProcessBuilder(SETTINGS, "put", "global", key, value)
                : new ProcessBuilder(SETTINGS, "delete", "global", key);
        java.lang.Process process = builder.redirectErrorStream(true).start();
        if (!process.waitFor(SETTINGS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("settings mutation timeout");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("settings mutation exit=" + process.exitValue());
        }
    }

    private static void requireAllowedSettingKey(String key) {
        if (!NUBIA_GAME_SCENE.equals(key)
                && !NUBIA_GAME_MODE.equals(key)
                && !CC_GAME_MIS_OPERATE.equals(key)) {
            throw new SecurityException("setting key rejected");
        }
    }

    private List<String> detectTrustedDevices() {
        java.lang.Process process = null;
        try {
            process = new ProcessBuilder(GETEVENT, "-pl")
                    .redirectErrorStream(true)
                    .start();

            if (!process.waitFor(DETECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                Log.w(TAG, "device discovery timeout");
                return Collections.emptyList();
            }

            List<String> devices = new ArrayList<>();
            String currentPath = "";
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher pathMatcher = EVENT_PATH.matcher(line);
                    if (pathMatcher.find()) currentPath = pathMatcher.group();

                    String trimmed = line.trim();
                    if (!trimmed.startsWith("name:")) continue;
                    String name = trimmed.substring("name:".length()).trim().replace("\"", "");
                    if (isTrustedSarName(name) && isTrustedEventPath(currentPath)
                            && !devices.contains(currentPath)) {
                        devices.add(currentPath);
                        Log.i(TAG, "trusted SAR detected name=" + sanitizeName(name)
                                + " node=" + currentPath);
                    }
                }
            }
            return devices;
        } catch (Throwable t) {
            Log.w(TAG, "GSB-SHOULDER-READER-DETECT-FAILED", t);
            return Collections.emptyList();
        } finally {
            if (process != null && process.isAlive()) {
                try {
                    process.destroyForcibly();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private void startReaderLocked(String device) {
        try {
            java.lang.Process process = new ProcessBuilder(GETEVENT, "-ql", device)
                    .redirectErrorStream(true)
                    .start();
            readerProcesses.add(process);

            Thread thread = new Thread(() -> readLoop(device, process),
                    "GSB-Shoulder-" + device.substring(device.lastIndexOf('/') + 1));
            thread.setDaemon(true);
            readerThreads.add(thread);
            thread.start();
        } catch (Throwable t) {
            Log.w(TAG, "GSB-SHOULDER-READER-START-FAILED node=" + device, t);
            sendStatus(callback, "GSB-SHOULDER-READER-START-FAILED");
        }
    }

    private void readLoop(String device, java.lang.Process process) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while (reading && !Thread.currentThread().isInterrupted()
                    && (line = reader.readLine()) != null) {
                parseTrustedEvent(line);
            }
        } catch (Throwable t) {
            if (reading) {
                Log.w(TAG, "reader ended unexpectedly node=" + device, t);
                sendStatus(callback, "GSB-SHOULDER-READER-ENDED");
            }
        }
    }

    private void parseTrustedEvent(String line) {
        if (line == null || !line.contains("EV_KEY")) return;

        String upper = line.toUpperCase(Locale.ROOT);
        int side;
        if (upper.contains("KEY_F7")) {
            side = 0;
        } else if (upper.contains("KEY_F8")) {
            side = 1;
        } else {
            Matcher hex = HEX_KEY.matcher(line);
            if (!hex.find()) return;
            int code;
            try {
                code = Integer.parseInt(hex.group(1), 16);
            } catch (NumberFormatException ignored) {
                return;
            }
            if (code == 0x41) side = 0;
            else if (code == 0x42) side = 1;
            else return;
        }

        Boolean down = parseDownState(upper);
        if (down == null) return;

        IRedMagicShoulderCallback target = callback;
        if (!reading || target == null) return;
        try {
            target.onTriggerEvent(side, down);
            Log.d(TAG, (side == 0 ? "LEFT" : "RIGHT") + (down ? " DOWN" : " UP"));
        } catch (Throwable t) {
            Log.w(TAG, "callback failed; stopping reader", t);
            stopReading();
        }
    }

    private static Boolean parseDownState(String upperLine) {
        if (upperLine.contains(" DOWN") || upperLine.contains("VALUE 1")
                || upperLine.endsWith(" 00000001")) return Boolean.TRUE;
        if (upperLine.contains(" UP") || upperLine.contains("VALUE 0")
                || upperLine.endsWith(" 00000000")) return Boolean.FALSE;
        return null;
    }

    private static boolean isTrustedEventPath(String path) {
        return path != null && EVENT_PATH.matcher(path).matches();
    }

    private static boolean isTrustedSarName(String name) {
        if (name == null) return false;
        return name.toLowerCase(Locale.ROOT).contains("nubia_tgk_aw_sar");
    }

    private static String sanitizeName(String name) {
        if (name == null) return "unknown";
        String safe = name.replaceAll("[^a-zA-Z0-9._ -]", "");
        return safe.length() > 80 ? safe.substring(0, 80) : safe;
    }

    private static void sendStatus(IRedMagicShoulderCallback callback, String code) {
        if (callback == null) return;
        try {
            callback.onStatus(code);
        } catch (Throwable ignored) {
        }
    }
}
