package com.yanxing.agent.service

import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Root 增强：检测设备是否已 Root，并提供受控的命令执行。
 * 仅在用户显式启用"Root 增强模式"时使用。
 */
object RootShell {

    private var cachedRootAvailable: Boolean? = null

    /** 设备是否已 Root（缓存检测结果） */
    fun isRootAvailable(): Boolean {
        cachedRootAvailable?.let { return it }
        val available = runCatching {
            val paths = listOf(
                "/system/bin/su",
                "/system/xbin/su",
                "/sbin/su",
                "/su/bin/su",
                "/system/app/Superuser.apk",
                "/data/adb/magisk",
            )
            val pathExists = paths.any { java.io.File(it).exists() }

            val execDetected = runCatching {
                val process = ProcessBuilder("which", "su").start()
                val result = process.waitFor() == 0
                process.destroy()
                result
            }.getOrDefault(false)

            pathExists || execDetected
        }.getOrDefault(false)
        cachedRootAvailable = available
        return available
    }

    /**
     * 执行 Root 命令（需要设备已 Root 且用户授权）
     * @return 命令输出；失败返回 null
     */
    fun execute(command: String): String? {
        if (!isRootAvailable()) return null
        return runCatching {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = StringBuilder()
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }
            }
            val exitCode = process.waitFor()
            if (exitCode == 0) output.toString().trim() else null
        }.getOrNull()
    }

    /** 清除缓存（切换环境后可调用） */
    fun resetCache() {
        cachedRootAvailable = null
    }

    /** 常见增强命令示例 */
    object Commands {
        const val GET_DEVICE_INFO = "getprop ro.product.model && getprop ro.build.version.release"
        const val BATTERY_LEVEL = "cat /sys/class/power_supply/battery/capacity"
        const val CLEAR_RECENTS = "cmd activity recents clear-all"
        const val SCREEN_ON = "input keyevent KEYCODE_WAKEUP"
        const val SET_SCREEN_BRIGHTNESS = "settings put system screen_brightness 255"
    }
}
