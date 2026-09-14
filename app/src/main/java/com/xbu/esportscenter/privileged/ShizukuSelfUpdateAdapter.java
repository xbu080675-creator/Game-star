package com.xbu.esportscenter.privileged;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.xbu.esportscenter.BuildConfig;

import java.io.File;
import java.io.FileInputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import rikka.shizuku.Shizuku;

/**
 * Narrow Shizuku adapter for verified Game Star Box self-updates.
 *
 * There is intentionally no generic shell/exec surface in this class.
 */
public final class ShizukuSelfUpdateAdapter {

    public interface Callback {
        void onStatus(String status);
        void onCompleted();
        void onFallback(String errorCode, String message);
    }

    private static final String TAG = "[GSB-PRIV]";
    private static final String INSTALL_TAG = "[GSB-INSTALL]";
    private static final String OFFICIAL_PACKAGE = "com.xbu.esportscenter";
    private static final int REQUEST_CODE = 0x4753;
    private static final int CHUNK_BYTES = 48 * 1024;
    private static final long BINDER_WAIT_MS = 5000L;

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Shizuku.UserServiceArgs serviceArgs;
    private final AtomicBoolean serviceBound = new AtomicBoolean(false);

    private volatile Pending pending;
    private volatile IPrivilegedInstaller remote;
    private volatile boolean destroyed;
    private volatile boolean permissionRequestInFlight;
    private volatile boolean installInFlight;

    private static final class Pending {
        final File apk;
        final long size;
        final String sha256;
        final String packageName;
        final Callback callback;

        Pending(File apk, long size, String sha256, String packageName, Callback callback) {
            this.apk = apk;
            this.size = size;
            this.sha256 = sha256;
            this.packageName = packageName;
            this.callback = callback;
        }
    }

    private final Shizuku.OnBinderReceivedListener binderReceivedListener = () -> {
        Log.i(TAG, "binder received");
        continuePending();
    };

    private final Shizuku.OnBinderDeadListener binderDeadListener = () -> {
        remote = null;
        serviceBound.set(false);
        Log.w(TAG, "GSB-PRIV-SHIZUKU-BINDER-DEAD");
        Pending p = pending;
        if (p != null && !destroyed) {
            fallback(p, "GSB-PRIV-SHIZUKU-BINDER-DEAD", "Shizuku 服务已断开");
        }
    };

    private final Shizuku.OnRequestPermissionResultListener permissionResultListener = (requestCode, grantResult) -> {
        if (requestCode != REQUEST_CODE) return;
        permissionRequestInFlight = false;
        Pending p = pending;
        if (p == null || destroyed) return;
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Shizuku permission granted");
            continuePending();
        } else {
            Log.w(TAG, "GSB-PRIV-SHIZUKU-PERMISSION-DENIED");
            fallback(p, "GSB-PRIV-SHIZUKU-PERMISSION-DENIED", "未授予 Shizuku 权限");
        }
    };

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            if (binder == null || !binder.pingBinder()) {
                Pending p = pending;
                if (p != null && !destroyed) {
                    fallback(p, "GSB-PRIV-SHIZUKU-SERVICE-MISSING", "Shizuku UserService 返回了无效 Binder");
                }
                return;
            }

            serviceBound.set(true);
            remote = IPrivilegedInstaller.Stub.asInterface(binder);
            Pending p = pending;
            if (p == null || destroyed) {
                unbindService(true);
                return;
            }
            if (installInFlight) return;
            installInFlight = true;
            status(p, "Shizuku 已连接 · 正在应用已验证更新…");
            executor.execute(() -> transferAndInstall(p));
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            remote = null;
            serviceBound.set(false);
            Pending p = pending;
            if (p != null && !destroyed && installInFlight) {
                fallback(p, "GSB-PRIV-SHIZUKU-SERVICE-DISCONNECTED", "特权安装服务已断开");
            }
        }
    };

    public ShizukuSelfUpdateAdapter(Context context) {
        this.context = context.getApplicationContext();
        this.serviceArgs = new Shizuku.UserServiceArgs(
                new ComponentName(this.context, PrivilegedInstallerService.class)
        )
                .daemon(false)
                .processNameSuffix("gsb_privileged")
                .debuggable(BuildConfig.DEBUG)
                .version(BuildConfig.VERSION_CODE);

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
        Shizuku.addRequestPermissionResultListener(permissionResultListener);
    }

    /**
     * Install an APK only after the caller has completed all normal OTA verification.
     * Inputs are intentionally narrow and revalidated here before touching Shizuku.
     */
    public void installVerifiedSelfUpdate(
            File apk,
            long size,
            String sha256,
            String packageName,
            Callback callback
    ) {
        if (destroyed) {
            callback.onFallback("GSB-PRIV-SHIZUKU-ADAPTER-CLOSED", "特权安装适配器已关闭");
            return;
        }
        if (pending != null || installInFlight) {
            callback.onFallback("GSB-PRIV-INSTALL-BUSY", "已有更新安装事务正在进行");
            return;
        }
        if (!OFFICIAL_PACKAGE.equals(packageName)) {
            callback.onFallback("GSB-PRIV-INSTALL-PACKAGE-DENIED", "特权通道只允许竞界自更新");
            return;
        }
        String normalizedHash = normalizeHex(sha256);
        if (apk == null || !apk.isFile() || size <= 0L || apk.length() != size || normalizedHash.length() != 64) {
            callback.onFallback("GSB-PRIV-INSTALL-CANDIDATE-INVALID", "更新候选文件状态无效");
            return;
        }

        Pending p = new Pending(apk, size, normalizedHash, packageName, callback);
        pending = p;
        status(p, "正在连接 Shizuku…");

        if (!safePingBinder()) {
            status(p, "正在等待 Shizuku Binder…");
            main.postDelayed(() -> {
                Pending current = pending;
                if (current == p && !destroyed && !safePingBinder()) {
                    fallback(p, "GSB-PRIV-SHIZUKU-UNAVAILABLE", "Shizuku Binder 在等待窗口内仍不可用；请确认 Shizuku 正在运行");
                }
            }, BINDER_WAIT_MS);
            return;
        }

        continuePending();
    }

    private void continuePending() {
        Pending p = pending;
        if (p == null || destroyed || installInFlight) return;

        try {
            if (!Shizuku.pingBinder()) return;
            if (Shizuku.isPreV11()) {
                fallback(p, "GSB-PRIV-SHIZUKU-UNSUPPORTED", "Shizuku 版本过旧");
                return;
            }

            int permission = Shizuku.checkSelfPermission();
            if (permission != PackageManager.PERMISSION_GRANTED) {
                if (Shizuku.shouldShowRequestPermissionRationale()) {
                    fallback(p, "GSB-PRIV-SHIZUKU-PERMISSION-DENIED", "Shizuku 权限已被拒绝");
                    return;
                }
                if (!permissionRequestInFlight) {
                    permissionRequestInFlight = true;
                    status(p, "需要一次 Shizuku 授权 · 授权后系统安装器将不再弹出");
                    Shizuku.requestPermission(REQUEST_CODE);
                }
                return;
            }

            status(p, "正在启动安全安装服务…");
            Shizuku.bindUserService(serviceArgs, serviceConnection);
        } catch (Throwable t) {
            Log.w(TAG, "GSB-PRIV-SHIZUKU-CONNECT-FAILED", t);
            fallback(p, "GSB-PRIV-SHIZUKU-CONNECT-FAILED", shortMessage(t));
        }
    }

    private void transferAndInstall(Pending p) {
        IPrivilegedInstaller service = remote;
        if (service == null || pending != p || destroyed) {
            fallback(p, "GSB-PRIV-SHIZUKU-SERVICE-MISSING", "特权安装服务不可用");
            return;
        }

        try {
            int begin = service.beginInstall(p.size, p.sha256, p.packageName);
            if (begin != 0) {
                throw new IllegalStateException("begin=" + begin);
            }

            long sent = 0L;
            try (FileInputStream input = new FileInputStream(p.apk)) {
                byte[] buffer = new byte[CHUNK_BYTES];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read == 0) continue;
                    if (pending != p || destroyed) {
                        try {
                            service.cancelInstall();
                        } catch (Throwable ignored) {
                        }
                        return;
                    }
                    int result = service.writeChunk(buffer, read);
                    if (result != 0) {
                        throw new IllegalStateException("write=" + result);
                    }
                    sent += read;
                }
            }

            if (sent != p.size) {
                throw new IllegalStateException("sent=" + sent + "/" + p.size);
            }

            status(p, "安全校验已确认 · 正在静默提交更新…");
            String result = service.finishInstall();
            if (result == null || result.trim().isEmpty()) {
                throw new IllegalStateException("empty result");
            }

            String normalizedResult = result.trim();
            if (normalizedResult.startsWith("ERR:")) {
                String errorCode = normalizedResult.substring(4).trim();
                fallback(p, errorCode, explainFailure(errorCode));
                return;
            }
            if (!normalizedResult.startsWith("OK")) {
                throw new IllegalStateException(normalizedResult);
            }

            Log.i(INSTALL_TAG, "privileged self-update committed result=" + normalizedResult);
            pending = null;
            installInFlight = false;
            unbindService(true);

            if ("OK:RESTART-FAILED".equals(normalizedResult)) {
                main.post(() -> p.callback.onStatus("更新已安装 · 自动重启失败，请手动重新打开竞界"));
                return;
            }

            main.post(p.callback::onCompleted);
        } catch (Throwable t) {
            Log.e(INSTALL_TAG, "GSB-PRIV-INSTALL-TRANSACTION-FAILED", t);
            try {
                service.cancelInstall();
            } catch (Throwable ignored) {
            }
            fallback(p, "GSB-PRIV-INSTALL-TRANSACTION-FAILED", shortMessage(t));
        }
    }

    private static String explainFailure(String errorCode) {
        if ("GSB-PRIV-INSTALL-PACKAGE-DENIED-ADB-POLICY".equals(errorCode)) {
            return "Shizuku 已连接且授权正常，但当前 ROM 禁止 shell/ADB 安装 APK。请在开发者选项开启“允许 ADB 安装 / USB 安装”后重试。";
        }
        if ("GSB-PRIV-INSTALL-COMMIT-TIMEOUT".equals(errorCode)) {
            return "Android Package Manager 在规定时间内没有完成安装";
        }
        if ("GSB-PRIV-INSTALL-COMMIT-FAILED".equals(errorCode)) {
            return "Android Package Manager 拒绝了特权安装请求";
        }
        return errorCode;
    }

    private void fallback(Pending p, String errorCode, String message) {
        if (pending != p) return;
        pending = null;
        installInFlight = false;
        permissionRequestInFlight = false;
        remote = null;
        unbindService(true);
        main.post(() -> p.callback.onFallback(errorCode, message));
    }

    private void status(Pending p, String text) {
        main.post(() -> {
            if (pending == p && !destroyed) p.callback.onStatus(text);
        });
    }

    private boolean safePingBinder() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void unbindService(boolean remove) {
        if (!serviceBound.getAndSet(false) && remote == null) return;
        try {
            if (safePingBinder()) {
                Shizuku.unbindUserService(serviceArgs, serviceConnection, remove);
            }
        } catch (Throwable t) {
            Log.w(TAG, "unbind skipped", t);
        } finally {
            remote = null;
        }
    }

    public void shutdown() {
        destroyed = true;
        Pending p = pending;
        pending = null;
        permissionRequestInFlight = false;
        installInFlight = false;
        try {
            IPrivilegedInstaller service = remote;
            if (service != null) service.cancelInstall();
        } catch (Throwable ignored) {
        }
        unbindService(true);
        Shizuku.removeBinderReceivedListener(binderReceivedListener);
        Shizuku.removeBinderDeadListener(binderDeadListener);
        Shizuku.removeRequestPermissionResultListener(permissionResultListener);
        executor.shutdownNow();
        if (p != null) {
            Log.i(TAG, "pending privileged install cancelled during shutdown");
        }
    }

    private static String normalizeHex(String value) {
        return value == null
                ? ""
                : value.replace(":", "").replace(" ", "").trim().toLowerCase(Locale.ROOT);
    }

    private static String shortMessage(Throwable t) {
        String message = t.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = t.getClass().getSimpleName();
        }
        return message.length() > 180 ? message.substring(0, 180) : message;
    }
}
