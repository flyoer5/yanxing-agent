# Findings

- 当前 GitHub 仓库最初只有 README.md。
- 本地环境按约束不依赖 Android Studio 或本地 Gradle 构建；CI 使用 GitHub Actions 安装 Gradle 8.9 和 Android SDK。
- CI 首次构建发现缺少 `android.useAndroidX=true`，已在 `gradle.properties` 补充。
- 为降低首阶段复杂度，暂不加入导航库、语音、联网搜索、无障碍和 Root；先打通文字对话闭环。
- 使用 OkHttp 直接实现 OpenAI 兼容 `/chat/completions`，避免对不同兼容服务的 Retrofit 序列化差异。
