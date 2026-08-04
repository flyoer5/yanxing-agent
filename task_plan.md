# 言行 Agent 第九阶段开发计划

## 目标
实现系统级增强：悬浮窗快捷入口、无障碍服务读取屏幕内容、Root 权限增强操作。

## 阶段
- [complete] 1. 项目骨架与 CI
- [complete] 2. 本地会话/消息数据层
- [complete] 3. OpenAI 兼容 API 与流式输出
- [complete] 4. Compose 聊天与模型配置界面
- [complete] 5. 单元测试、文档、推送验证
- [complete] 6. 多会话、分组与长期记忆
- [complete] 7. 语音、图片/文件输入输出
- [complete] 8. 联网搜索
- [in_progress] 9. 悬浮窗、无障碍与 Root 增强

## 本阶段验收标准
- [ ] 设置页可开启"悬浮窗模式"
- [ ] 悬浮球可拖动、点击展开快捷面板
- [ ] 悬浮窗可快速发起对话/语音输入
- [ ] 无障碍服务可读取当前屏幕文字
- [ ] Root 检测与增强命令执行
- [ ] GitHub Actions 编译、测试成功

## 技术方案

### 悬浮窗
- `SYSTEM_ALERT_WINDOW` 权限 + 前台服务
- `TYPE_APPLICATION_OVERLAY` 窗口，View 实现（避免 Compose 在悬浮窗的复杂度）
- 悬浮球可拖动，点击展开迷你面板
- 迷你面板：文字输入、语音按钮、打开主界面

### 无障碍服务
- `AccessibilityService` + `BIND_ACCESSIBILITY_SERVICE` 权限
- `accessibility_service_config.xml` 声明
- 读取当前界面文本（eventType TYPE_WINDOW_CONTENT_CHANGED / TYPE_WINDOW_STATE_CHANGED）
- 通过 Broadcast/静态单例向 App 传递屏幕内容

### Root 增强
- 检测 `su` 是否存在（PATH 中查找）
- `su -c` 执行命令（仅限用户显式授权的场景）
- 提供 `RootShell` 封装，失败时优雅降级

## 错误记录
| 错误 | 尝试 | 处理 |
|---|---:|---|
| - | - | - |
