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
    @Volatile private var rootAuthorized = false

    /** 设置 Root 增强授权状态；默认关闭，应用重启后由设置层重新注入。 */
    fun setAuthorized(authorized: Boolean) {
        rootAuthorized = authorized
    }

    fun isAuthorized(): Boolean = rootAuthorized

    /** 只允许预定义命令，亮度命令仅允许数字参数。 */
    fun isCommandAllowed(command: String): Boolean {
        val normalized = command.trim()
        val fixedCommands = setOf(
            Commands.GET_DEVICE_INFO,
            Commands.BATTERY_LEVEL,
            Commands.GET_SCREEN_BRIGHTNESS,
            Commands.CLEAR_RECENTS,
            Commands.SCREEN_ON,
            Commands.SHOW_RECENTS,
            Commands.GO_HOME,
            Commands.APP_LIST,
        )
        if (normalized in fixedCommands) return true
        val brightnessPrefix = "settings put system screen_brightness "
        val brightnessValue = normalized
            .takeIf { it.startsWith(brightnessPrefix) }
            ?.removePrefix(brightnessPrefix)
            ?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
            ?.toIntOrNull()
        return brightnessValue != null && isBrightnessInRange(brightnessValue)
    }

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
        if (!rootAuthorized || !isCommandAllowed(command)) return null
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

    // ===== 系统控制命令 =====

    /** 读取电池百分比（0-100），失败返回 null */
    fun batteryLevel(): Int? = runCatching {
        val output = execute(Commands.BATTERY_LEVEL) ?: return null
        output.trim().toIntOrNull()
    }.getOrNull()

    /** 读取当前屏幕亮度（0-255），失败返回 null */
    fun screenBrightness(): Int? = runCatching {
        val output = execute(Commands.GET_SCREEN_BRIGHTNESS) ?: return null
        output.trim().toIntOrNull()
    }.getOrNull()

    /** 校验屏幕亮度值是否在合法范围（0-255），纯逻辑无副作用 */
    fun isBrightnessInRange(value: Int): Boolean = value in 0..255

    /** 设置屏幕亮度（0-255），成功返回 true */
    fun setScreenBrightness(value: Int): Boolean {
        if (!isBrightnessInRange(value)) return false
        val output = execute("settings put system screen_brightness ${value.coerceIn(0, 255)}")
        // settings put 无输出即成功
        return output != null
    }

    /** 点亮屏幕 */
    fun wakeScreen(): Boolean = execute(Commands.SCREEN_ON) != null

    /** 打开系统最近任务页 */
    fun showRecents(): Boolean = execute(Commands.SHOW_RECENTS) != null

    /** 返回桌面（回到主屏） */
    fun goHome(): Boolean = execute(Commands.GO_HOME) != null

    /** 获取第三方应用包名列表（换行分隔） */
    fun appList(): String? = execute(Commands.APP_LIST)

    /** 获取设备信息（型号 + 系统版本） */
    fun deviceInfo(): String? = execute(Commands.GET_DEVICE_INFO)

    /** 常见增强命令示例 */
    object Commands {
        const val GET_DEVICE_INFO = "getprop ro.product.model && getprop ro.build.version.release"
        const val BATTERY_LEVEL = "cat /sys/class/power_supply/battery/capacity"
        const val GET_SCREEN_BRIGHTNESS = "settings get system screen_brightness"
        const val CLEAR_RECENTS = "cmd activity recents clear-all"
        const val SCREEN_ON = "input keyevent KEYCODE_WAKEUP"
        const val SHOW_RECENTS = "cmd activity recents"
        const val GO_HOME = "input keyevent KEYCODE_HOME"
        const val APP_LIST = "pm list packages -3"
        const val SET_SCREEN_BRIGHTNESS = "settings put system screen_brightness"
    }
}
