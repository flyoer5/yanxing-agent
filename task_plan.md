# 言行 Agent 第十三阶段开发计划

## 目标
修复主线程阻塞 bug + 开启 Release 混淆，提升上线质量。

## 阶段
- [complete] 1-12. 前序阶段
- [in-progress] 13. Release 混淆 + 修复主线程阻塞

## 本阶段验收标准
- [ ] `executePendingAction` 里 `ActionExecutor` 调用切换到 `Dispatchers.Default`，避免阻塞主线程
- [ ] `ActionExecutor` 内 `Thread.sleep` 改为 `delay`（协程版本，可响应取消）
- [ ] `build.gradle.kts` Release 构建开启 `isMinifyEnabled = true`
- [ ] `proguard-rules.pro` 补全所有依赖的保留规则（Hilt、Room、OkHttp、Kotlin Serialization、Compose、data 类）
- [ ] CI 验证 Release APK 可安装、签名校验通过、包体积显著缩小
- [ ] 补充单元测试验证协程取消响应（ActionExecutor 模拟测试）
- [ ] GitHub Actions 编译、测试成功

## 技术方案

### 修复主线程阻塞
**问题根源**：`executePendingAction` 跑在 `viewModelScope.launch`（默认 `Dispatchers.Main`），直接调用 `ActionExecutor.click/longPress/inputText`，这些方法内部有：
- `Thread.sleep(50)` 用于等待 UI 稳定
- `Thread.sleep(200)` 用于重试间隔
- `ActionExecutor` 是 `object`，方法全同步阻塞

慢机上会触发 ANR（Android Not Responding）。

**解决方案**：
1. `ChatViewModel.executePendingAction` 里调用 `ActionExecutor.*` 时用 `withContext(Dispatchers.Default)` 包住
2. `ActionExecutor` 内部：
   - 把 `Thread.sleep` 改成 `kotlinx.coroutines.delay`
   - `withRetrySupport` 改为 `suspend` 函数
   - 所有操作方法（`click/longPress/swipe/inputText`）改为 `suspend`
3. `RootShell.execute` 里的 `Thread.sleep` 保持不变（它本身已经在 IO 操作上下文）

### Release 混淆
**ProGuard 规则**（`app/proguard-rules.pro`）：
```proguard
# Hilt
-dontwarn com.google.errorprone.annotations.**
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# OkHttp / Retrofit
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.yanxing.agent.**$$serializer { *; }
-keepclassmembers class com.yanxing.agent.** {
    *** Companion;
}
-keepclasseswithmembers class com.yanxing.agent.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# AccessibilityService（避免反射查找失败）
-keep class com.yanxing.agent.service.ScreenReaderAccessibilityService { *; }
-keep class com.yanxing.agent.service.FloatingWindowService { *; }

# Data 类（Room/API 序列化依赖字段名）
-keepclassmembers class com.yanxing.agent.data.** { *; }
-keepclassmembers class com.yanxing.agent.network.** { *; }

# Enum（保留 valueOf）
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# AndroidX & Material
-dontwarn com.google.android.material.**
-keep class com.google.android.material.** { *; }
```

**包体积预期**：Debug 19MB → Release 混淆后约 12-13MB（缩小 30-35%）

### 单元测试
新增 `ActionExecutorCoroutineTest.kt`，验证：
1. `delay` 可被协程取消（不会永久阻塞）
2. 重试逻辑在取消时提前退出
3. 操作在 Default dispatcher 上执行不会阻塞 Main

## 文件清单
| 文件 | 操作 | 说明 |
|---|---|---|
| `service/ActionExecutor.kt` | 修改 | 方法改 suspend，`Thread.sleep` → `delay` |
| `ui/ChatViewModel.kt` | 修改 | `executePendingAction` 加 `withContext(Dispatchers.Default)` |
| `app/build.gradle.kts` | 修改 | Release 开启 `isMinifyEnabled = true` |
| `app/proguard-rules.pro` | 修改 | 补全所有依赖的混淆规则 |
| `test/.../ActionExecutorCoroutineTest.kt` | 新建 | 协程取消响应测试 |

## 错误记录
| 错误 | 尝试 | 处理 |
|---|---:|---|
| （待填充） | - | - |
