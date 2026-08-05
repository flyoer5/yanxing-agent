# 言行 Agent 第十五阶段开发计划

## 目标
Root 增强命令扩展：支持电量读取、屏幕亮度调节等系统控制命令。

## 阶段
- [complete] 1-14. 前序阶段
- [in-progress] 15. Root 增强命令扩展（电池/亮度/唤醒）

## 本阶段验收标准
- [ ] `RootShell` 新增 `batteryLevel()` → 返回电池百分比 (Int) 或 null
- [ ] `RootShell` 新增 `screenBrightness()` → 读取当前亮度值 (0..255)，`setScreenBrightness(value: Int)` → 设置亮度
- [ ] `RootShell` 新增 `wakeScreen()` → 点亮屏幕
- [ ] `RootShell` 新增 `goBackOrCloseApp()` → 尝试 back() + finishActivity()
- [ ] `ActionExecutor` 补充 `finishActivity()` 方法（可选）
- [ ] UI：设置页增加「Root 增强」区域，展示可用命令与使用说明
- [ ] 单元测试：模拟 root 环境验证命令输出解析正确性
- [ ] GitHub Actions 编译、测试成功

## 技术方案

### RootShell 扩展方法

```kotlin
object RootShell {
    // 已有：execute(command): String?
    
    /** 读取电池百分比 */
    fun batteryLevel(): Int? = runCatching {
        val output = execute(BATTERY_LEVEL)
        output?.toIntOrNull() ?: throw IllegalArgumentException("Invalid battery value: $output")
    }.getOrNull()

    /** 读取当前屏幕亮度 (0-255) */
    fun screenBrightness(): Int? = runCatching {
        val output = execute("settings get system screen_brightness")
        output?.trim()?.toIntOrNull() ?: throw IllegalArgumentException("Invalid brightness value: $output")
    }.getOrNull()

    /** 设置屏幕亮度 (0-255) */
    fun setScreenBrightness(value: Int): Boolean {
        if (value !in 0..255) return false
        val output = execute("settings put system screen_brightness ${value.coerceIn(0, 255)}")
        return output != null && output.isNotBlank()
    }

    /** 点亮屏幕 */
    fun wakeScreen(): Boolean = execute(SCREEN_ON) != null

    /** 优先执行返回，失败则尝试关闭当前应用 */
    fun goBackOrCloseApp(): Boolean {
        val backSuccess = ActionExecutor.back().success
        if (backSuccess) return true
        // 备选：通过 shell 命令结束当前进程
        val currentPkg = ScreenReaderAccessibilityService.lastScreenPackage
        return if (currentPkg.isNotEmpty()) {
            execute("am force-stop $currentPkg") != null
        } else {
            false
        }
    }
}
```

### UI 入口
1. **设置页**：在设置页面的底部增加「Root 增强」区域，列出可用命令和说明
2. **聊天界面**：用户输入"读取电量""设置亮度 80"等指令时，AI 可以调用对应的 Root 命令（需显式提示权限确认）

### 注意事项
- 所有命令仅在 `isRootAvailable() == true` 时可用
- 操作高风险命令（如强制停止应用）时，UI 需明确提示风险
- 某些定制 ROM 可能不支持的部分命令路径（如 `/sys/class/power_supply/battery`），失败时优雅降级

### 单元测试
- `RootShellCommandTest.kt`：用 Mock 方式验证命令输出的解析逻辑
- 由于 `execute()` 是同步阻塞且有超时，测试中需要注入 mockable 的实现（或使用 `test-debug` 构建）

## 文件清单
| 文件 | 操作 | 说明 |
|---|---|---|
| `service/RootShell.kt` | 修改 | 新增 `batteryLevel`, `screenBrightness`, `setScreenBrightness`, `wakeScreen` 等方法 |
| `service/ActionExecutor.kt` | 修改（可选） | 补充 `finishActivity()` 或全局返回的扩展 |
| `ui/AgentApp.kt` | 修改 | 设置页增加 Root 增强信息卡片 |
| `test/.../RootShellCommandTest.kt` | 新建 | Root 命令输出解析测试 |

## 风险与边界
- **设备兼容**：不同厂商的电池状态路径可能不同（有的用 `/proc/acpi/battery`，有的用 `/sys/class/power_supply`），fallback 策略需覆盖常见情况
- **安全风险**：强制停止应用可能导致数据丢失，UI 需明确警示
- **Root 权限**：未 Root 的设备无法使用这些命令，需降级为友好提示
