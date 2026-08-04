# 言行 Agent 第三阶段开发计划

## 目标
支持图片、文件、语音输入输出，扩展多模态交互能力。

## 阶段
- [complete] 1. 项目骨架与 CI
- [complete] 2. 本地会话/消息数据层
- [complete] 3. OpenAI 兼容 API 与流式输出
- [complete] 4. Compose 聊天与模型配置界面
- [complete] 5. 单元测试、文档、推送验证
- [complete] 6. 多会话、分组与长期记忆
- [in_progress] 7. 语音、图片/文件输入输出
- [pending] 8. 联网搜索
- [pending] 9. 悬浮窗、无障碍与 Root 增强

## 本阶段验收标准
- [ ] 支持从相册选择图片并发送
- [ ] 支持相机拍摄图片并发送
- [ ] 支持发送文件和文档
- [ ] 支持语音输入（麦克风录音转文字）
- [ ] 显示消息中的图片/文件预览
- [ ] 正确构建多模态请求体（base64 或 URL）
- [ ] GitHub Actions 编译、测试成功

## 技术方案

### 图片输入
- 使用 PhotoPicker API（Android 13+）或 Intent.ACTION_PICK
- 压缩后转 base64 或使用 URL
- 消息 UI 显示缩略图

### 文件输入
- Intent.ACTION_OPEN_DOCUMENT
- 支持 PDF、Word、Excel 等
- 转 base64 或文件名+摘要

### 语音输入
- 使用 android-speech 录音转文字
- 或者 Android SpeechRecognizer
- 结果作为用户消息发送

### 语音输出（TTS）
- 使用 android-speak（TTS）朗读 AI 回复
- 可选功能，用户可开关

## API 兼容性
- OpenAI Vision: `messages[].content[]` 包含 `type: "image_url"` 或 `type: "image_base64"`
- Claude: 支持 images 参数
- 统一抽象多模态消息构建

## 错误记录
| 错误 | 尝试 | 处理 |
|---|---:|---|
| - | - | - |
