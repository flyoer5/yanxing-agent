# yanxing-agent
言行 Agent：懂你的话，替你行动。

## 第一阶段 MVP

当前已包含：

- Kotlin + Jetpack Compose + Material 3
- 单模块 Android 项目，包名 `com.yanxing.agent`
- 最低支持 Android 7.0（API 24）
- 多轮文字对话界面
- OpenAI 兼容 `/v1/chat/completions` 接口
- 流式 / 完整回复切换
- Room 本地保存会话和消息
- Android Keystore 加密保存 API Key
- GitHub Actions 编译、测试和 Debug APK Artifact

## GitHub Actions

- Pull Request：运行单元测试并编译 Debug APK
- `main` 分支：运行测试、编译并上传 Debug APK
- 手动触发：运行测试、编译并上传 Debug APK

## 本地配置

在 App 的“设置”中填写：

1. API 地址，例如 `https://api.example.com/v1`；也支持不带 `/v1` 的根地址
2. API Key
3. 模型名称

API Key 只保存在设备本地的 Android Keystore 加密数据中，不会写入 Git 或备份。

## 后续阶段

- 多会话、项目分组和长期记忆
- 图片、文件、语音输入输出
- 联网搜索
- 悬浮窗、无障碍服务和 Root 增强模式
