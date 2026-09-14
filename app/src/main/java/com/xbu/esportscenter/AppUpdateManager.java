package com.xbu.esportscenter;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class AppUpdateManager {
    public interface Listener {
        void onUpdateState(State state);
    }

    public static final class State {
        public final boolean checking;
        public final boolean downloading;
        public final boolean available;
        public final String latestVersionName;
        public final int latestVersionCode;
        public final String changelog;
        public final String sourceLabel;
        public final int progressPercent;
        public final long downloadedBytes;
        public final long totalBytes;
        public final String status;
        public final String error;
        public final boolean requiresInstallPermission;

        State(boolean checking,
              boolean downloading,
              boolean available,
              String latestVersionName,
              int latestVersionCode,
              String changelog,
              String sourceLabel,
              int progressPercent,
              long downloadedBytes,
              long totalBytes,
              String status,
              String error,
              boolean requiresInstallPermission) {
            this.checking = checking;
            this.downloading = downloading;
            this.available = available;
            this.latestVersionName = latestVersionName == null ? "" : latestVersionName;
            this.latestVersionCode = latestVersionCode;
            this.changelog = changelog == null ? "" : changelog;
            this.sourceLabel = sourceLabel == null ? "" : sourceLabel;
            this.progressPercent = progressPercent;
            this.downloadedBytes = downloadedBytes;
            this.totalBytes = totalBytes;
            this.status = status == null ? "" : status;
            this.error = error;
            this.requiresInstallPermission = requiresInstallPermission;
        }

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("checking", checking);
                o.put("downloading", downloading);
                o.put("available", available);
                o.put("latestVersionName", latestVersionName);
                o.put("latestVersionCode", latestVersionCode);
                o.put("changelog", changelog);
                o.put("sourceLabel", sourceLabel);
                o.put("progressPercent", progressPercent);
                o.put("downloadedBytes", downloadedBytes);
                o.put("totalBytes", totalBytes);
                o.put("status", status);
                o.put("error", error == null ? JSONObject.NULL : error);
                o.put("requiresInstallPermission", requiresInstallPermission);
                o.put("currentVersionName", BuildConfig.VERSION_NAME);
                o.put("currentVersionCode", BuildConfig.VERSION_CODE);
            } catch (Throwable ignored) {
            }
            return o;
        }
    }

    private static final class Transport {
        final String label;
        final String baseUrl;
        final boolean accelerated;

        Transport(String label, String baseUrl, boolean accelerated) {
            this.label = label;
            this.baseUrl = baseUrl;
            this.accelerated = accelerated;
        }
    }

    private static final class Candidate {
        final String versionName;
        final int versionCode;
        final String changelog;
        final String apkUrl;
        final String sha256;
        final long size;
        final String sourceLabel;

        Candidate(String versionName, int versionCode, String changelog, String apkUrl,
                  String sha256, long size, String sourceLabel) {
            this.versionName = versionName;
            this.versionCode = versionCode;
            this.changelog = changelog;
            this.apkUrl = apkUrl;
            this.sha256 = sha256;
            this.size = size;
            this.sourceLabel = sourceLabel;
        }
    }

    private static final class Probe {
        final Transport transport;
        final long bytesPerSecond;

        Probe(Transport transport, long bytesPerSecond) {
            this.transport = transport;
            this.bytesPerSecond = bytesPerSecond;
        }
    }

    private static final String OFFICIAL_PACKAGE = "com.xbu.esportscenter";
    private static final String MANIFEST_URL =
            "https://github.com/xbu080675-creator/Game-star/releases/download/preview/latest.json";
    private static final String RELEASE_PATH_PREFIX =
            "/xbu080675-creator/Game-star/releases/download/preview/";
    private static final String DEV_SIGNER_SHA256 =
            "1d1c44e7bfabeb5c4bab863408f07aef1fa806c4337827830f4b72fb2920bf35";
    private static final int PROBE_BYTES = 256 * 1024;
    private static final int PROBE_MIN_BYTES = 48 * 1024;

    private static final Transport DIRECT = new Transport("GitHub 直连", "", false);
    private static final List<Transport> MIRRORS = Arrays.asList(
            new Transport("GH LLKK", "https://gh.llkk.cc/", true),
            new Transport("iSteed", "https://cors.isteed.cc/", true),
            new Transport("XMLY", "https://gh.xmly.dev/", true),
            new Transport("DDLC", "https://gh.ddlc.top/", true),
            new Transport("GHFast", "https://ghfast.top/", true),
            new Transport("GHProxy.net", "https://ghproxy.net/", true)
    );

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newFixedThreadPool(10);
    private volatile Listener listener;
    private volatile Candidate candidate;
    private volatile State state = new State(
            false, false, false, "", 0, "", "", 0, 0, 0,
            "尚未检查更新", null, false
    );

    public AppUpdateManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public State getState() {
        return state;
    }

    public String getStateJson() {
        return state.toJson().toString();
    }

    public void checkForUpdates(boolean userInitiated) {
        if (state.checking) return;
        postState(new State(
                true, false, state.available, state.latestVersionName, state.latestVersionCode,
                state.changelog, state.sourceLabel, state.progressPercent, state.downloadedBytes,
                state.totalBytes, "正在检查 GitHub 更新…", null, false
        ));
        executor.execute(() -> {
            try {
                Candidate found = fetchPreferredRelease();
                candidate = found;
                boolean available = found.versionCode > BuildConfig.VERSION_CODE;
                String status = available
                        ? "发现新版本 " + found.versionName + " · " + found.sourceLabel
                        : "当前已是最新版本 · " + BuildConfig.VERSION_NAME;
                postState(new State(
                        false, false, available, found.versionName, found.versionCode,
                        found.changelog, found.sourceLabel, 0, 0, found.size,
                        status, null, false
                ));
            } catch (Throwable t) {
                postState(new State(
                        false, false, false, "", 0, "", "", 0, 0, 0,
                        userInitiated ? "检查更新失败" : "后台更新检查暂不可用",
                        userInitiated ? shortMessage(t) : null, false
                ));
            }
        });
    }

    public void downloadAndInstall() {
        Candidate c = candidate;
        if (c == null || !state.available || state.downloading) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.getPackageManager().canRequestPackageInstalls()) {
            postState(new State(
                    false, false, true, c.versionName, c.versionCode, c.changelog,
                    state.sourceLabel, state.progressPercent, state.downloadedBytes, c.size,
                    "需要允许竞界安装更新包", null, true
            ));
            Intent settingsIntent = new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + context.getPackageName())
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(settingsIntent);
            return;
        }

        postState(new State(
                false, true, true, c.versionName, c.versionCode, c.changelog,
                state.sourceLabel, 0, 0, c.size,
                "正在测速 GitHub 与镜像通道…", null, false
        ));

        executor.execute(() -> {
            try {
                File apk = downloadWithFallback(c);
                verifyDownloadedApk(apk, c);
                postState(new State(
                        false, false, true, c.versionName, c.versionCode, c.changelog,
                        state.sourceLabel, 100, apk.length(), c.size,
                        "下载与安全校验完成 · 正在打开系统安装器", null, false
                ));
                launchInstaller(apk);
            } catch (Throwable t) {
                postState(new State(
                        false, false, true, c.versionName, c.versionCode, c.changelog,
                        state.sourceLabel, state.progressPercent, state.downloadedBytes, c.size,
                        "更新失败", shortMessage(t), false
                ));
            }
        });
    }

    private Candidate fetchPreferredRelease() throws Exception {
        validateOfficialManifestUrl(MANIFEST_URL);
        try {
            return fetchManifest(DIRECT);
        } catch (Throwable directError) {
            Throwable last = directError;
            for (Transport t : MIRRORS) {
                try {
                    postStatus("GitHub 直连不可用 · 尝试 " + t.label + "…");
                    return fetchManifest(t);
                } catch (Throwable mirrorError) {
                    last = mirrorError;
                }
            }
            if (last instanceof Exception) throw (Exception) last;
            throw new Exception(last);
        }
    }

    private Candidate fetchManifest(Transport transport) throws Exception {
        JSONObject root = getJson(MANIFEST_URL, transport);
        int schema = root.optInt("schemaVersion", 1);
        if (schema < 1) throw new IllegalStateException("更新清单格式无效");
        String channel = root.optString("channel", "preview").trim();
        if (!channel.isEmpty() && !"preview".equalsIgnoreCase(channel)) {
            throw new IllegalStateException("更新通道不匹配");
        }
        String versionName = root.optString("versionName", "").trim();
        int versionCode = root.optInt("versionCode", 0);
        String changelog = root.optString("changelog", "").trim();
        String apkRef = root.optString("apk", "").trim();
        if (apkRef.isEmpty()) apkRef = root.optString("apkUrl", "").trim();
        String sha256 = normalizeHex(root.optString("sha256", ""));
        long size = Math.max(0L, root.optLong("size", 0L));
        if (versionName.isEmpty() || versionCode <= 0 || apkRef.isEmpty() || sha256.length() != 64) {
            throw new IllegalStateException("更新清单元数据不完整");
        }
        URL resolved = new URL(new URL(MANIFEST_URL), apkRef);
        String apkUrl = resolved.toString();
        validateOfficialApkUrl(apkUrl);
        return new Candidate(versionName, versionCode, changelog, apkUrl, sha256, size, transport.label);
    }

    private JSONObject getJson(String originalUrl, Transport transport) throws Exception {
        HttpURLConnection connection = null;
        try {
            String requestUrl = transportUrl(originalUrl, transport);
            connection = (HttpURLConnection) new URL(requestUrl).openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(transport.accelerated ? 6000 : 7000);
            connection.setReadTimeout(transport.accelerated ? 10000 : 12000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "Game-Star-Box-Updater/" + BuildConfig.VERSION_NAME);
            if (transport.accelerated) connection.setRequestProperty("Connection", "close");
            int code = connection.getResponseCode();
            if (code < 200 || code > 299) throw new IllegalStateException(transport.label + " HTTP " + code);
            if (!"https".equalsIgnoreCase(connection.getURL().getProtocol())) {
                throw new IllegalStateException("更新通道重定向到了非 HTTPS 地址");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (BufferedInputStream input = new BufferedInputStream(connection.getInputStream())) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    out.write(buffer, 0, read);
                    if (out.size() > 512 * 1024) throw new IllegalStateException("更新清单异常过大");
                }
            }
            return new JSONObject(out.toString(StandardCharsets.UTF_8.name()));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private List<Probe> rankTransports(Candidate c) {
        List<Transport> transports = new ArrayList<>();
        transports.add(DIRECT);
        transports.addAll(MIRRORS);
        List<Callable<Probe>> tasks = new ArrayList<>();
        for (Transport t : transports) {
            tasks.add(() -> probe(c, t));
        }
        List<Probe> probes = new ArrayList<>();
        try {
            List<Future<Probe>> futures = executor.invokeAll(tasks, 8, TimeUnit.SECONDS);
            for (Future<Probe> f : futures) {
                try {
                    if (!f.isCancelled()) {
                        Probe p = f.get(100, TimeUnit.MILLISECONDS);
                        if (p != null) probes.add(p);
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        probes.sort(Comparator.comparingLong((Probe p) -> p.bytesPerSecond).reversed());
        return probes;
    }

    private Probe probe(Candidate c, Transport t) throws Exception {
        HttpURLConnection connection = null;
        long started = System.nanoTime();
        try {
            connection = (HttpURLConnection) new URL(transportUrl(c.apkUrl, t)).openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(t.accelerated ? 4000 : 5000);
            connection.setReadTimeout(5000);
            connection.setUseCaches(true);
            connection.setRequestProperty("Accept", "application/octet-stream");
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("Range", "bytes=0-" + (PROBE_BYTES - 1));
            connection.setRequestProperty("User-Agent", "Game-Star-Box-Updater/" + BuildConfig.VERSION_NAME);
            if (t.accelerated) connection.setRequestProperty("Connection", "close");
            int code = connection.getResponseCode();
            if (code < 200 || code > 299) throw new IllegalStateException(t.label + " 测速 HTTP " + code);
            int received = 0;
            try (BufferedInputStream input = new BufferedInputStream(connection.getInputStream())) {
                byte[] buffer = new byte[64 * 1024];
                while (received < PROBE_BYTES) {
                    int read = input.read(buffer, 0, Math.min(buffer.length, PROBE_BYTES - received));
                    if (read < 0) break;
                    received += read;
                }
            }
            if (received < PROBE_MIN_BYTES) throw new IllegalStateException(t.label + " 测速数据不足");
            long elapsed = Math.max(1L, System.nanoTime() - started);
            long bps = Math.max(1L, received * 1_000_000_000L / elapsed);
            return new Probe(t, bps);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private File downloadWithFallback(Candidate c) throws Exception {
        List<Probe> ranked = rankTransports(c);
        List<Transport> order = new ArrayList<>();
        for (Probe p : ranked) order.add(p.transport);
        for (Transport t : allTransports()) if (!order.contains(t)) order.add(t);

        if (!ranked.isEmpty()) {
            Probe best = ranked.get(0);
            postState(new State(
                    false, true, true, c.versionName, c.versionCode, c.changelog,
                    best.transport.label, 0, 0, c.size,
                    "测速完成 · " + best.transport.label + " · " + formatRate(best.bytesPerSecond), null, false
            ));
        }

        Throwable last = null;
        for (Transport t : order) {
            try {
                State current = state;
                postState(new State(
                        false, true, true, c.versionName, c.versionCode, c.changelog,
                        t.label, current.progressPercent, current.downloadedBytes, c.size,
                        "正在通过 " + t.label + " 下载 " + c.versionName + "…", null, false
                ));
                return downloadFromTransport(c, t);
            } catch (Throwable failure) {
                last = failure;
                postStatus(t.label + " 中断 · 保留断点并切换下一通道…");
            }
        }
        if (last instanceof Exception) throw (Exception) last;
        throw new Exception(last == null ? "没有可用更新通道" : last);
    }

    private File downloadFromTransport(Candidate c, Transport t) throws Exception {
        File dir = new File(context.getCacheDir(), "updates");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("无法创建更新缓存目录");
        File part = new File(dir, "Game-Star-Box-" + c.versionCode + ".apk.part");
        File out = new File(dir, "Game-Star-Box-update.apk");
        if (c.size > 0 && part.exists() && part.length() > c.size) part.delete();

        long existing = part.exists() ? part.length() : 0L;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(transportUrl(c.apkUrl, t)).openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(t.accelerated ? 7000 : 12000);
            connection.setReadTimeout(t.accelerated ? 20000 : 30000);
            connection.setUseCaches(true);
            connection.setRequestProperty("Accept", "application/octet-stream");
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("User-Agent", "Game-Star-Box-Updater/" + BuildConfig.VERSION_NAME);
            if (existing > 0) connection.setRequestProperty("Range", "bytes=" + existing + "-");
            if (t.accelerated) connection.setRequestProperty("Connection", "close");
            int code = connection.getResponseCode();
            if (code < 200 || code > 299) throw new IllegalStateException(t.label + " 下载 HTTP " + code);

            boolean append = existing > 0 && code == HttpURLConnection.HTTP_PARTIAL;
            if (existing > 0 && !append) {
                existing = 0;
                if (part.exists()) part.delete();
            }

            long downloaded = existing;
            long lastUi = 0L;
            try (BufferedInputStream input = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream output = new FileOutputStream(part, append)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    output.write(buffer, 0, read);
                    downloaded += read;
                    long now = System.currentTimeMillis();
                    if (now - lastUi >= 220) {
                        lastUi = now;
                        int percent = c.size > 0 ? (int) Math.min(99, downloaded * 100L / c.size) : 0;
                        postState(new State(
                                false, true, true, c.versionName, c.versionCode, c.changelog,
                                t.label, percent, downloaded, c.size,
                                "正在下载 · " + t.label, null, false
                        ));
                    }
                }
                output.getFD().sync();
            }

            if (c.size > 0 && part.length() != c.size) {
                throw new IllegalStateException("更新包大小不完整");
            }
            if (out.exists() && !out.delete()) throw new IllegalStateException("无法替换旧更新包");
            if (!part.renameTo(out)) {
                copyFile(part, out);
                if (!part.delete()) part.deleteOnExit();
            }
            return out;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void verifyDownloadedApk(File apk, Candidate c) throws Exception {
        String actualSha = sha256(apk);
        if (!actualSha.equals(c.sha256)) {
            if (apk.exists()) apk.delete();
            throw new SecurityException("SHA-256 校验失败");
        }

        PackageManager pm = context.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? PackageManager.GET_SIGNING_CERTIFICATES
                : PackageManager.GET_SIGNATURES;
        PackageInfo info = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        if (info == null) throw new SecurityException("无法读取更新包信息");
        if (!OFFICIAL_PACKAGE.equals(info.packageName)) throw new SecurityException("更新包包名不匹配");
        long archiveVersion = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? info.getLongVersionCode() : info.versionCode;
        if (archiveVersion != c.versionCode) throw new SecurityException("更新包 versionCode 不匹配");

        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && info.signingInfo != null) {
            signatures = info.signingInfo.getApkContentsSigners();
        } else {
            signatures = info.signatures;
        }
        if (signatures == null || signatures.length == 0) throw new SecurityException("更新包没有签名");
        boolean trusted = false;
        for (Signature signature : signatures) {
            String digest = sha256(signature.toByteArray());
            if (DEV_SIGNER_SHA256.equals(digest)) {
                trusted = true;
                break;
            }
        }
        if (!trusted) throw new SecurityException("更新包签名证书不匹配");
    }

    private void launchInstaller(File apk) {
        mainHandler.post(() -> {
            try {
                Uri uri = FileProvider.getUriForFile(
                        context,
                        context.getPackageName() + ".updateprovider",
                        apk
                );
                Intent intent = new Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                context.startActivity(intent);
            } catch (Throwable t) {
                Candidate c = candidate;
                postState(new State(
                        false, false, c != null, c == null ? "" : c.versionName,
                        c == null ? 0 : c.versionCode, c == null ? "" : c.changelog,
                        state.sourceLabel, 100, apk.length(), c == null ? 0 : c.size,
                        "无法打开系统安装器", shortMessage(t), false
                ));
            }
        });
    }

    private String transportUrl(String originalUrl, Transport t) throws Exception {
        if (!t.accelerated) return originalUrl;
        validateOfficialSource(originalUrl);
        String base = t.baseUrl.trim();
        if (!base.endsWith("/")) base += "/";
        if (!base.toLowerCase(Locale.ROOT).startsWith("https://")) {
            throw new SecurityException("镜像通道必须使用 HTTPS");
        }
        return base + originalUrl;
    }

    private static void validateOfficialManifestUrl(String url) throws Exception {
        URL u = new URL(url);
        if (!"https".equalsIgnoreCase(u.getProtocol()) || !"github.com".equalsIgnoreCase(u.getHost())) {
            throw new SecurityException("更新清单不是官方 GitHub HTTPS 地址");
        }
        if (!u.getPath().equals(RELEASE_PATH_PREFIX + "latest.json")) {
            throw new SecurityException("更新清单路径不在官方 preview Release");
        }
    }

    private static void validateOfficialApkUrl(String url) throws Exception {
        validateOfficialSource(url);
        URL u = new URL(url);
        String path = u.getPath();
        if (!path.startsWith(RELEASE_PATH_PREFIX) || !path.toLowerCase(Locale.ROOT).endsWith(".apk")) {
            throw new SecurityException("APK 地址不在官方 preview Release");
        }
    }

    private static void validateOfficialSource(String url) throws Exception {
        URL u = new URL(url);
        if (!"https".equalsIgnoreCase(u.getProtocol()) || !"github.com".equalsIgnoreCase(u.getHost())) {
            throw new SecurityException("只允许访问 Game Star Box 官方 GitHub Release");
        }
        if (!u.getPath().startsWith(RELEASE_PATH_PREFIX)) {
            throw new SecurityException("资源不在官方 preview Release");
        }
    }

    private static List<Transport> allTransports() {
        List<Transport> result = new ArrayList<>();
        result.add(DIRECT);
        result.addAll(MIRRORS);
        return result;
    }

    private void postStatus(String status) {
        State s = state;
        postState(new State(
                s.checking, s.downloading, s.available, s.latestVersionName, s.latestVersionCode,
                s.changelog, s.sourceLabel, s.progressPercent, s.downloadedBytes,
                s.totalBytes, status, s.error, s.requiresInstallPermission
        ));
    }

    private void postState(State newState) {
        state = newState;
        Listener l = listener;
        if (l != null) mainHandler.post(() -> l.onUpdateState(newState));
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
        }
        return hex(digest.digest());
    }

    private static String sha256(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return hex(digest.digest(bytes));
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return sb.toString();
    }

    private static String normalizeHex(String value) {
        return value == null ? "" : value.replace(":", "").replace(" ", "").trim().toLowerCase(Locale.ROOT);
    }

    private static void copyFile(File from, File to) throws Exception {
        try (FileInputStream input = new FileInputStream(from);
             FileOutputStream output = new FileOutputStream(to)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            output.getFD().sync();
        }
    }

    private static String shortMessage(Throwable t) {
        String m = t.getMessage();
        if (m == null || m.trim().isEmpty()) m = t.getClass().getSimpleName();
        return m.length() > 180 ? m.substring(0, 180) : m;
    }

    private static String formatRate(long bytesPerSecond) {
        if (bytesPerSecond >= 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f MB/s", bytesPerSecond / 1024d / 1024d);
        }
        return String.format(Locale.ROOT, "%.0f KB/s", bytesPerSecond / 1024d);
    }
}
