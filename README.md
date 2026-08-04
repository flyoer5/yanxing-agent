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

## 第二阶段已完成

新增：

- 多会话：新建、切换、删除，重启后恢复最近会话
- 会话标题自动取首条用户消息
- 项目/主题分组：创建分组并给当前会话分组
- 长期记忆页面：查看、删除、清空
- 默认从明确表达中自动提取偏好、资料和项目记忆
- 自动记忆提示支持“撤销”
- 请求前按关键词检索相关记忆并注入上下文
- 聊天页面显示本次引用的记忆/历史数量

## 第三阶段已完成

新增：

- **图片输入**：从相册选择或相机拍摄，自动压缩并转为 base64
- **文件输入**：支持 PDF、Word、Excel 等文档
- **附件预览**：发送前预览已选图片/文件，可删除
- **消息内展示**：AI 回复的图片、文件也可在聊天中显示
- **多模态 API**：支持 OpenAI Vision 风格的多模态请求（image_url 类型）
- **语音输入**：麦克风按钮，录音转文字（需录音权限）

## 后续阶段

- 联网搜索
- 悬浮窗、无障碍服务和 Root 增强模式
