# 言行 Agent 第一阶段开发计划

## 目标
创建可由 GitHub Actions 编译的 Android MVP：OpenAI 兼容 API、多轮文字对话、流式/完整输出、本地会话与消息保存。

## 阶段
- [complete] 1. 项目骨架与 CI
- [complete] 2. 本地会话/消息数据层
- [complete] 3. OpenAI 兼容 API 与流式输出
- [complete] 4. Compose 聊天与模型配置界面
- [in_progress] 5. 单元测试、文档、推送验证

## 已确认决策
- 应用名：言行 Agent
- 包名：com.yanxing.agent
- Kotlin + Compose + Material 3
- 单模块优先
- minSdk 24，首要测试 Android 13/16
- API 地址、模型、API Key 可配置；API Key 使用 Keystore 加密
- GitHub Actions：PR/main 编译测试；main 和手动触发上传 Debug APK

## 错误记录
| 错误 | 尝试 | 处理 |
|---|---:|---|
| 无 | - | - |
