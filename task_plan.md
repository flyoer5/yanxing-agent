# 言行 Agent 第二阶段开发计划

## 目标
在第一阶段文字对话基础上，加入持久化多会话、项目/主题分组和长期记忆闭环。

## 阶段
- [complete] 1. 项目骨架与 CI
- [complete] 2. 本地会话/消息数据层
- [complete] 3. OpenAI 兼容 API 与流式输出
- [complete] 4. Compose 聊天与模型配置界面
- [complete] 5. 单元测试、文档、推送验证
- [complete] 6. 多会话、分组与长期记忆
- [pending] 7. 语音、图片/文件
- [pending] 8. 联网搜索
- [pending] 9. 悬浮窗、无障碍与 Root 增强

## 本阶段验收标准
- [ ] 重启后仍能看到会话和消息
- [ ] 可以新建、切换、删除会话
- [ ] 会话可以归入项目/主题分组
- [ ] 可以查看、删除长期记忆
- [ ] 默认自动保存明确的偏好/资料/项目表达，并提供撤销
- [ ] 相关记忆会注入请求并显示引用数量
- [ ] GitHub Actions 编译、测试、上传 Debug APK 成功

## 已确认决策
- 应用名：言行 Agent
- 包名：com.yanxing.agent
- Kotlin + Compose + Material 3
- 单模块优先
- minSdk 24，首要测试 Android 13/16
- API 地址、模型、API Key 可配置；API Key 使用 Keystore 加密
- GitHub Actions：PR/main 编译测试；main 和手动触发上传 Debug APK
- 长期记忆默认自动保存，敏感内容不自动保存

## 错误记录
| 错误 | 尝试 | 处理 |
|---|---:|---|
| 无 | - | - |
