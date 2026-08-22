package com.yanxing.agent.service

/**
 * Root 增强命令的统一入口（从 ChatViewModel 拆出）。
 * 全部为阻塞调用（底层 su 子进程最长数秒），供 ViewModel 在后台调度器上调用。
 */
object RootCommandExecutor {

    /** 读取电池百分比（如 "85%"） */
    fun readBatteryLevel(): String? {
        if (!RootShell.isRootAvailable()) return null
        val level = runCatching { RootShell.batteryLevel() }.getOrNull() ?: return null
        return "${level}%"
    }

    /** 读取当前屏幕亮度 */
    fun readScreenBrightness(): Int? {
        if (!RootShell.isRootAvailable()) return null
        return runCatching { RootShell.screenBrightness() }.getOrNull()
    }

    /** 设置屏幕亮度 */
    fun setScreenBrightness(value: Int): Boolean {
        if (!RootShell.isRootAvailable()) return false
        return runCatching { RootShell.setScreenBrightness(value) }.getOrDefault(false)
    }

    /** 点亮屏幕 */
    fun wakeScreen(): Boolean {
        if (!RootShell.isRootAvailable()) return false
        return runCatching { RootShell.wakeScreen() }.getOrDefault(false)
    }

    /** 返回桌面 */
    fun goHome(): Boolean {
        if (!RootShell.isRootAvailable()) return false
        return runCatching { RootShell.goHome() }.getOrDefault(false)
    }

    /** 获取第三方应用包名列表 */
    fun getAppList(): String? {
        if (!RootShell.isRootAvailable()) return null
        return runCatching { RootShell.appList() }.getOrNull()
    }

    /** 获取设备信息 */
    fun getDeviceInfo(): String? {
        if (!RootShell.isRootAvailable()) return null
        return runCatching { RootShell.deviceInfo() }.getOrNull()?.replace("&&", "\n")
    }
}
