package com.scluzlep.fxplusmodule.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Looper
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class ShizukuStatusSnapshot(
    val shizukuInstalled: Boolean = false,
    val binderAlive: Boolean = false,
    val permissionGranted: Boolean = false,
    val serviceBound: Boolean = false,
    val privilegedUid: Int = -1,
    val serverVersion: Int = 0,
    val statusMessage: String = ""
)

object ShizukuStatusHelper {
    private const val REQUEST_CODE_SHIZUKU = 10023

    private val initialized = AtomicBoolean(false)
    private val bindLock = Any()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var boundService: IPrivilegedStorageService? = null

    @Volatile
    private var pendingLatch: CountDownLatch? = null

    @Volatile
    private var bindingInProgress: Boolean = false

    @Volatile
    var currentStatus: ShizukuStatusSnapshot = ShizukuStatusSnapshot()
        private set

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refresh("Shizuku 服务已连接")
        ensureServiceBoundAsync()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        clearBoundService()
        refresh("Shizuku 服务连接已断开")
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode != REQUEST_CODE_SHIZUKU) return@OnRequestPermissionResultListener
        val granted = grantResult == PackageManager.PERMISSION_GRANTED
        refresh(if (granted) "已授予 Shizuku 权限" else "未授予 Shizuku 权限")
        if (granted) {
            ensureServiceBoundAsync()
        }
    }

    private val userServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val stub = service?.let { IPrivilegedStorageService.Stub.asInterface(it) }
            synchronized(bindLock) {
                boundService = stub
                bindingInProgress = false
                pendingLatch?.countDown()
                pendingLatch = null
            }
            refresh(if (stub != null) "Shizuku 存储服务已就绪" else "Shizuku 存储服务连接为空")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            synchronized(bindLock) {
                boundService = null
                bindingInProgress = false
                pendingLatch?.countDown()
                pendingLatch = null
            }
            refresh("Shizuku 存储服务已断开")
        }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        if (!initialized.compareAndSet(false, true)) {
            refresh()
            return
        }
        runCatching {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
        }
        refresh()
    }

    fun refresh(actionMessage: String = currentStatus.statusMessage): ShizukuStatusSnapshot {
        val ctx = appContext
        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val installed = binderAlive || isShizukuInstalled(ctx)
        val granted = if (binderAlive) {
            runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }
                .getOrDefault(false)
        } else {
            false
        }
        val uid = if (binderAlive) {
            runCatching { Shizuku.getUid() }.getOrDefault(-1)
        } else {
            -1
        }
        val version = if (binderAlive) {
            runCatching { Shizuku.getVersion() }.getOrDefault(0)
        } else {
            0
        }
        val serviceAlive = boundService?.asBinder()?.let {
            runCatching { it.pingBinder() }.getOrDefault(false)
        } ?: false
        if (!serviceAlive && boundService != null) {
            clearBoundService()
        }

        val updated = ShizukuStatusSnapshot(
            shizukuInstalled = installed,
            binderAlive = binderAlive,
            permissionGranted = granted,
            serviceBound = serviceAlive,
            privilegedUid = uid,
            serverVersion = version,
            statusMessage = actionMessage
        )
        currentStatus = updated
        return updated
    }

    fun requestPermission() {
        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!binderAlive) {
            refresh("Shizuku 服务未运行，请先启动 Shizuku")
            return
        }
        val alreadyGranted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        if (alreadyGranted) {
            refresh("Shizuku 权限已授予")
            ensureServiceBoundAsync()
            return
        }
        runCatching {
            Shizuku.requestPermission(REQUEST_CODE_SHIZUKU)
            refresh("正在申请 Shizuku 授权...")
        }.onFailure { err ->
            refresh("申请 Shizuku 授权失败：${err.message}")
        }
    }

    fun ensureServiceBoundAsync() {
        val ctx = appContext ?: return
        val existing = boundService
        if (existing != null && runCatching { existing.asBinder().pingBinder() }.getOrDefault(false)) {
            return
        }
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) return
        if (!runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)) {
            return
        }
        synchronized(bindLock) {
            if (bindingInProgress) return
            bindingInProgress = true
        }
        runCatching {
            Shizuku.bindUserService(buildUserServiceArgs(ctx), userServiceConnection)
        }.onFailure {
            synchronized(bindLock) {
                bindingInProgress = false
            }
        }
    }

    fun getOrBindService(timeoutMs: Long = 4_000L): IPrivilegedStorageService? {
        val current = boundService
        if (current != null && runCatching { current.asBinder().pingBinder() }.getOrDefault(false)) {
            return current
        }
        clearBoundService()

        val ctx = appContext ?: return null
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) return null
        if (!runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)) {
            return null
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            ensureServiceBoundAsync()
            return boundService
        }

        val latch: CountDownLatch
        val shouldStartBind: Boolean
        synchronized(bindLock) {
            val recheck = boundService
            if (recheck != null && runCatching { recheck.asBinder().pingBinder() }.getOrDefault(false)) {
                return recheck
            }
            val existingLatch = pendingLatch
            if (existingLatch != null) {
                latch = existingLatch
                shouldStartBind = false
            } else {
                latch = CountDownLatch(1)
                pendingLatch = latch
                bindingInProgress = true
                shouldStartBind = true
            }
        }

        if (shouldStartBind) {
            val started = runCatching {
                Shizuku.bindUserService(buildUserServiceArgs(ctx), userServiceConnection)
                true
            }.getOrElse {
                synchronized(bindLock) {
                    bindingInProgress = false
                    if (pendingLatch === latch) {
                        pendingLatch = null
                    }
                    latch.countDown()
                }
                false
            }
            if (!started) return null
        }

        runCatching { latch.await(timeoutMs, TimeUnit.MILLISECONDS) }
        return boundService?.takeIf { runCatching { it.asBinder().pingBinder() }.getOrDefault(false) }
    }

    fun invalidateService() {
        clearBoundService()
        refresh()
    }

    private fun clearBoundService() {
        synchronized(bindLock) {
            boundService = null
            bindingInProgress = false
            pendingLatch?.countDown()
            pendingLatch = null
        }
    }

    private fun buildUserServiceArgs(context: Context): Shizuku.UserServiceArgs {
        val versionCode = runCatching {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.longVersionCode.toInt()
        }.getOrDefault(1)

        return Shizuku.UserServiceArgs(
            ComponentName(context.packageName, ShizukuStorageUserService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("storage")
            .tag("fxplus_storage")
            .debuggable(false)
            .version(versionCode)
    }

    private fun isShizukuInstalled(context: Context?): Boolean {
        if (context == null) return false
        return runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(ShizukuBridgeContract.SHIZUKU_MANAGER_PACKAGE, 0)
            true
        }.getOrDefault(false)
    }
}
