package com.yanxing.agent.service

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Root 增强：检测设备是否已 Root，并提供受控的命令执行。
 * 仅在用户显式启用"Root 增强模式"时使用。
 *
 * 注意：所有外部进程调用都带超时并关闭 stdin，避免在
 * 非 Root 环境（如 CI）中因 su 等待密码输入而挂起。
 */
object RootShell {

    private const val PROCESS_TIMEOUT_MS = 3_000L
    private var cachedRootAvailable: Boolean? = null

    /** 设备是否已 Root（缓存检测结果） */
    fun isRootAvailable(): Boolean {
        cachedRootAvailable?.let { return it }
        val available = runCatching {
            // 1. 检查 Android 常见的 su 路径（CI/桌面环境不存在这些路径）
            val androidPaths = listOf(
                "/system/bin/su",
                "/system/xbin/su",
                "/sbin/su",
                "/su/bin/su",
                "/system/app/Superuser.apk",
                "/data/adb/magisk",
            )
            if (androidPaths.any { java.io.File(it).exists() }) {
                return@runCatching true
            }

            // 2. 通过 which 检测（带超时，避免异常环境挂起）
            runCatching {
                val process = ProcessBuilder("which", "su").start()
                // 立即关闭 stdin，防止任何读取 stdin 的行为
                process.outputStream.close()
                val finished = process.waitFor(PROCESS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                if (finished) {
                    val code = process.exitValue()
                    process.destroy()
                    code == 0
                } else {
                    process.destroy()
                    false
                }
            }.getOrDefault(false)
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
            // 关闭 stdin，防止 su 等待密码输入导致挂起
            process.outputStream.close()

            // 带超时读取输出
            val output = StringBuilder()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val readJob = Thread {
                try {
                    reader.use { r ->
                        var line: String?
                        while (r.readLine().also { line = it } != null) {
                            output.append(line).append("\n")
                        }
                    }
                } catch (_: Exception) {
                    // 读取中断或超时销毁进程时忽略
                }
            }
            readJob.start()

            val finished = process.waitFor(PROCESS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroy()
                readJob.join(500)
                return null
            }
            readJob.join(500)
            if (process.exitValue() == 0) output.toString().trim() else null
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
