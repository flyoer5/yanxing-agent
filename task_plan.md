# 言行 Agent 第十阶段开发计划

## 目标
实现系统级增强：悬浮窗快捷入口、无障碍服务读取屏幕内容、Root 权限增强操作，并在此基础上实现 AI 替我行动（自动操作）与操作日志。

## 阶段
- [complete] 1. 项目骨架与 CI
- [complete] 2. 本地会话/消息数据层
- [complete] 3. OpenAI 兼容 API 与流式输出
- [complete] 4. Compose 聊天与模型配置界面
- [complete] 5. 单元测试、文档、推送验证
- [complete] 6. 多会话、分组与长期记忆
- [complete] 7. 语音、图片/文件输入输出
- [complete] 8. 联网搜索
- [complete] 8. 联网搜索
- [complete] 9. 悬浮窗、无障碍与 Root 增强
- [complete] 10. 替我行动（AI 决策 + 自动操作 + 操作日志）

## 本阶段验收标准
- [x] 设置页可开启"悬浮窗模式"
- [x] 悬浮球可拖动、点击展开快捷面板
- [x] 悬浮窗可快速发起对话/语音输入
- [x] 无障碍服务可读取当前屏幕文字
- [x] Root 检测与增强命令执行
- [x] 替我行动：AI 规划 + 智能元素识别 + 自动重试 + 逐动作确认
- [x] 操作日志：记录、查看、按应用过滤、清空
- [x] 执行进度悬浮窗：成功/失败计数、暂停继续、拖拽
- [x] 日志批量异步写入，性能优化
- [x] GitHub Actions 编译、测试成功
- [x] 真机安装验证通过

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
| kapt correctErrorTypes 编译报错 | 临时注释该配置 | 保留注释，功能正常 |
| FloatingProgressOverlay 注释未闭合 + 未声明的 CardView 依赖 | 修正注释、改用 LinearLayout+GradientDrawable 绘制卡片 | 已修复 |
| R.color.primary / R.id 引用不存在 | 改为直接引用视图对象 | 已修复 |
| Material3 无 successContainer | 改用 secondaryContainer | 已修复 |
| ConcurrentLinkedQueue 无 addFirst | 改为 add() | 已修复 |
| ActionExecutor 丢失根节点参数、回调参数不匹配 | 补 rootInActiveWindow 参数、修正回调 | 已修复 |
| 缺失 Attachment / ActionLogEntity 导入 | 补齐导入 | 已修复 |
| Hilt 缺少 ActionLogDao 提供方法 | DataModule 补 provideActionLogDao | 已修复 |
