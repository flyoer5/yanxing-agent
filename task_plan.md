# 言行 Agent 第十一阶段开发计划

## 目标
实现多轮行动决策：执行完一组动作后回传结果给 LLM，根据新屏幕继续决策，直到任务完成或达到轮次上限。

## 阶段
- [complete] 1. 项目骨架与 CI
- [complete] 2. 本地会话/消息数据层
- [complete] 3. OpenAI 兼容 API 与流式输出
- [complete] 4. Compose 聊天与模型配置界面
- [complete] 5. 单元测试、文档、推送验证
- [complete] 6. 多会话、分组与长期记忆
- [complete] 7. 语音、图片/文件输入输出
- [complete] 8. 联网搜索
- [complete] 9. 悬浮窗、无障碍与 Root 增强
- [complete] 10. 替我行动（AI 决策 + 自动操作 + 操作日志）
- [complete] 11. 多轮行动决策（执行结果回传 + 继续决策）+ 固定签名

## 本阶段验收标准
- [x] 动作组执行完后自动读取新屏幕并回传 LLM
- [x] LLM 返回 `done` 或新动作组，未完成则进入下一轮确认
- [x] 达到轮次上限（5 轮）自动结束，不无限循环
- [x] UI 显示"AI 正在分析第 N 轮"状态
- [x] AIDecisionEngine 单元测试覆盖 done/actions 解析
- [x] 修复"替我行动"主链路未接线（executeAction 为死代码）
- [x] 修复单动作任务被跳过执行的顺序缺陷
- [x] APK 使用固定签名，构建产物可覆盖安装
- [x] GitHub Actions 编译、测试成功

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
