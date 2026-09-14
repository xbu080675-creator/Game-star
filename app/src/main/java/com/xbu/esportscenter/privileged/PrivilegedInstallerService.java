package com.xbu.esportscenter.privileged;

import android.content.Context;
import android.os.Process;
import android.util.Log;

import androidx.annotation.Keep;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Shizuku UserService running as shell/root identity.
 *
 * Security boundary:
 * - self-update only;
 * - fixed package id only;
 * - no arbitrary command API;
 * - APK bytes are streamed over Binder and re-hashed inside the privileged process;
 * - staging is restricted to /data/local/tmp with a fixed filename prefix;
 * - post-update relaunch is restricted to the fixed Game Star Box activity.
 */
public final class PrivilegedInstallerService extends IPrivilegedInstaller.Stub {

    private static final String TAG = "[GSB-PRIV]";
    private static final String INSTALL_TAG = "[GSB-INSTALL]";
    private static final String OFFICIAL_PACKAGE = "com.xbu.esportscenter";
    private static final String OFFICIAL_ACTIVITY = "com.xbu.esportscenter/.MainActivity";
    private static final String STAGING_PREFIX = "gsb-self-update-";
    private static final long MAX_APK_BYTES = 512L * 1024L * 1024L;
    private static final int MAX_CHUNK_BYTES = 64 * 1024;
    private static final long INSTALL_TIMEOUT_SECONDS = 120L;
    private static final long RESTART_TIMEOUT_SECONDS = 12L;

    private File partFile;
    private File apkFile;
    private FileOutputStream output;
    private MessageDigest digest;
    private long expectedSize;
    private long receivedSize;
    private String expectedSha256;
    private boolean active;

    public PrivilegedInstallerService() {
        cleanupStaleFiles();
        Log.i(TAG, "UserService created uid=" + Process.myUid());
    }

    @Keep
    public PrivilegedInstallerService(Context context) {
        this();
    }

    @Override
    public synchronized int beginInstall(long expectedSize, String expectedSha256, String packageName) {
        cancelLocked();

        if (!OFFICIAL_PACKAGE.equals(packageName)) {
            Log.w(INSTALL_TAG, "GSB-PRIV-INSTALL-BEGIN-PACKAGE-DENIED");
            return 11;
        }
        String normalizedHash = normalizeHex(expectedSha256);
        if (expectedSize <= 0L || expectedSize > MAX_APK_BYTES) {
            Log.w(INSTALL_TAG, "GSB-PRIV-INSTALL-BEGIN-SIZE-INVALID size=" + expectedSize);
            return 12;
        }
        if (normalizedHash.length() != 64) {
            Log.w(INSTALL_TAG, "GSB-PRIV-INSTALL-BEGIN-HASH-INVALID");
            return 13;
        }

        try {
            cleanupStaleFiles();
            File dir = new File("/data/local/tmp");
            if (!dir.isDirectory()) return 14;

            String suffix = Process.myPid() + "-" + System.nanoTime();
            partFile = new File(dir, STAGING_PREFIX + suffix + ".apk.part");
            apkFile = new File(dir, STAGING_PREFIX + suffix + ".apk");
            output = new FileOutputStream(partFile, false);
            digest = MessageDigest.getInstance("SHA-256");
            this.expectedSize = expectedSize;
            this.receivedSize = 0L;
            this.expectedSha256 = normalizedHash;
            this.active = true;
            Log.i(INSTALL_TAG, "staging begin bytes=" + expectedSize + " hash=" + normalizedHash.substring(0, 12));
            return 0;
        } catch (Throwable t) {
            Log.e(INSTALL_TAG, "GSB-PRIV-INSTALL-BEGIN-FAILED", t);
            cancelLocked();
            return 15;
        }
    }

    @Override
    public synchronized int writeChunk(byte[] data, int length) {
        if (!active || output == null || digest == null) return 21;
        if (data == null || length <= 0 || length > data.length || length > MAX_CHUNK_BYTES) return 22;
        if (receivedSize + length > expectedSize) return 23;

        try {
            output.write(data, 0, length);
            digest.update(data, 0, length);
            receivedSize += length;
            return 0;
        } catch (Throwable t) {
            Log.e(INSTALL_TAG, "GSB-PRIV-INSTALL-WRITE-FAILED", t);
            cancelLocked();
            return 24;
        }
    }

    @Override
    public synchronized String finishInstall() {
        if (!active || output == null || digest == null || partFile == null || apkFile == null) {
            return "ERR:GSB-PRIV-INSTALL-FINISH-NO-SESSION";
        }

        try {
            output.getFD().sync();
            output.close();
            output = null;

            if (receivedSize != expectedSize || partFile.length() != expectedSize) {
                return failLocked("GSB-PRIV-INSTALL-VERIFY-SIZE-MISMATCH");
            }

            String actualHash = hex(digest.digest());
            digest = null;
            if (!actualHash.equals(expectedSha256)) {
                return failLocked("GSB-PRIV-INSTALL-VERIFY-HASH-MISMATCH");
            }

            if (apkFile.exists() && !apkFile.delete()) {
                return failLocked("GSB-PRIV-INSTALL-STAGE-CLEANUP-FAILED");
            }
            if (!partFile.renameTo(apkFile)) {
                copyFile(partFile, apkFile);
                if (!partFile.delete()) {
                    Log.w(INSTALL_TAG, "staging part cleanup deferred");
                }
            }

            active = false;
            Log.i(INSTALL_TAG, "verified privileged staging hash=" + actualHash.substring(0, 12));

            ProcessBuilder builder = new ProcessBuilder(
                    "/system/bin/pm",
                    "install",
                    "-r",
                    apkFile.getAbsolutePath()
            );
            builder.redirectErrorStream(true);
            java.lang.Process process = builder.start();
            boolean finished = process.waitFor(INSTALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return failAfterInstall("GSB-PRIV-INSTALL-COMMIT-TIMEOUT");
            }

            String commandOutput = readProcessOutput(process.getInputStream());
            int exit = process.exitValue();
            if (exit != 0 || !commandOutput.toLowerCase(Locale.ROOT).contains("success")) {
                Log.w(INSTALL_TAG, "pm install failed exit=" + exit + " out=" + sanitize(commandOutput));
                return failAfterInstall(classifyInstallFailure(commandOutput));
            }

            deleteQuietly(apkFile);
            apkFile = null;
            partFile = null;
            expectedSha256 = null;
            expectedSize = 0L;
            receivedSize = 0L;
            Log.i(INSTALL_TAG, "self-update committed successfully");

            boolean restarted = restartGameStarBox();
            if (restarted) {
                Log.i(INSTALL_TAG, "post-update app restart requested successfully");
                return "OK:RESTARTED";
            }

            Log.w(INSTALL_TAG, "GSB-PRIV-INSTALL-RESTART-FAILED");
            return "OK:RESTART-FAILED";
        } catch (Throwable t) {
            Log.e(INSTALL_TAG, "GSB-PRIV-INSTALL-FINISH-FAILED", t);
            return failAfterInstall("GSB-PRIV-INSTALL-FINISH-FAILED");
        }
    }

    @Override
    public synchronized void cancelInstall() {
        cancelLocked();
    }

    @Override
    public synchronized void destroy() {
        Log.i(TAG, "UserService destroy");
        cancelLocked();
        System.exit(0);
    }

    private String failLocked(String code) {
        Log.w(INSTALL_TAG, code);
        cancelLocked();
        return "ERR:" + code;
    }

    private String failAfterInstall(String code) {
        Log.w(INSTALL_TAG, code);
        active = false;
        closeQuietly(output);
        output = null;
        digest = null;
        deleteQuietly(partFile);
        deleteQuietly(apkFile);
        partFile = null;
        apkFile = null;
        expectedSha256 = null;
        expectedSize = 0L;
        receivedSize = 0L;
        return "ERR:" + code;
    }

    private void cancelLocked() {
        active = false;
        closeQuietly(output);
        output = null;
        digest = null;
        deleteQuietly(partFile);
        deleteQuietly(apkFile);
        partFile = null;
        apkFile = null;
        expectedSha256 = null;
        expectedSize = 0L;
        receivedSize = 0L;
    }

    private static String classifyInstallFailure(String commandOutput) {
        String value = commandOutput == null ? "" : commandOutput.toLowerCase(Locale.ROOT);
        if (value.contains("install_failed_user_restricted")
                || value.contains("user restricted")
                || value.contains("install is disabled")
                || value.contains("adb install") && value.contains("disabled")) {
            return "GSB-PRIV-INSTALL-PACKAGE-DENIED-ADB-POLICY";
        }
        return "GSB-PRIV-INSTALL-COMMIT-FAILED";
    }

    private static boolean restartGameStarBox() {
        java.lang.Process process = null;
        try {
            Thread.sleep(420L);
            ProcessBuilder builder = new ProcessBuilder(
                    "/system/bin/am",
                    "start",
                    "-S",
                    "-W",
                    "-n",
                    OFFICIAL_ACTIVITY
            );
            builder.redirectErrorStream(true);
            process = builder.start();
            boolean finished = process.waitFor(RESTART_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            String output = readProcessOutput(process.getInputStream());
            int exit = process.exitValue();
            if (exit != 0) {
                Log.w(INSTALL_TAG, "app restart failed exit=" + exit + " out=" + sanitize(output));
                return false;
            }
            return true;
        } catch (Throwable t) {
            Log.w(INSTALL_TAG, "app restart failed", t);
            if (process != null) {
                try {
                    process.destroyForcibly();
                } catch (Throwable ignored) {
                }
            }
            return false;
        }
    }

    private static void cleanupStaleFiles() {
        try {
            File dir = new File("/data/local/tmp");
            File[] files = dir.listFiles((d, name) -> name != null && name.startsWith(STAGING_PREFIX));
            if (files == null) return;
            for (File file : files) {
                deleteQuietly(file);
            }
        } catch (Throwable t) {
            Log.w(TAG, "stale staging cleanup skipped", t);
        }
    }

    private static void copyFile(File from, File to) throws Exception {
        try (FileInputStream input = new FileInputStream(from);
             FileOutputStream out = new FileOutputStream(to, false)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
            out.getFD().sync();
        }
    }

    private static String readProcessOutput(InputStream stream) throws Exception {
        try (BufferedInputStream input = new BufferedInputStream(stream);
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                bytes.write(buffer, 0, read);
                if (bytes.size() > 32 * 1024) break;
            }
            return bytes.toString(StandardCharsets.UTF_8.name()).trim();
        }
    }

    private static void closeQuietly(FileOutputStream stream) {
        if (stream == null) return;
        try {
            stream.close();
        } catch (Throwable ignored) {
        }
    }

    private static void deleteQuietly(File file) {
        if (file == null) return;
        try {
            if (file.exists() && !file.delete()) {
                file.deleteOnExit();
            }
        } catch (Throwable ignored) {
        }
    }

    private static String normalizeHex(String value) {
        return value == null
                ? ""
                : value.replace(":", "").replace(" ", "").trim().toLowerCase(Locale.ROOT);
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        }
        return sb.toString();
    }

    private static String sanitize(String value) {
        if (value == null) return "";
        String clean = value.replace('\n', ' ').replace('\r', ' ').trim();
        return clean.length() > 240 ? clean.substring(0, 240) : clean;
    }
}
