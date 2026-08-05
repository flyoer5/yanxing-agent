# 言行 Agent 第十二阶段开发计划

## 目标
实现执行停止机制 + 悬浮球语音输入，提升替我行动模式的可控性和便利性。

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
- [in-progress] 12. 执行停止 + 悬浮球语音输入

## 本阶段验收标准
- [ ] `FloatingProgressOverlay` 显示"停止"按钮，点击时触发停止信号
- [ ] `ChatViewModel` 增加 `stopAction()` 方法和停止标志位
- [ ] `executePendingAction` / `continueDecision` 每轮检查标志位，已停止则提前终止
- [ ] 停止后记录日志并重置上下文，UI 显示"已停止"状态
- [ ] `floating_panel.xml` 布局添加语音按钮
- [ ] 语音按钮调用 `android-speech listen`，识别结果填充到输入框
- [ ] 识别失败或超时时显示错误提示
- [ ] 增加 `ActionExecutorTest` 单元测试（覆盖停止逻辑）
- [ ] GitHub Actions 编译、测试成功

## 技术方案

### 执行停止
- **停止信号传递**：`FloatingProgressOverlay` 通过回调通知 `ChatViewModel.stopAction()`
- **标志位检查**：在 `executePendingAction` 开头、`continueDecision` 发起 LLM 请求前检查 `actionCancelled` 标志
- **清理与重置**：停止时调用 `finishAction("用户停止执行", isError = true)`，隐藏悬浮窗，清理上下文
- **UI 反馈**：`FloatingProgressOverlay` 按钮文字为"停止"，点击后按钮变灰或文字改为"已停止"

### 悬浮球语音输入
- **布局修改**：`floating_panel.xml` 在"发送"按钮左侧增加语音按钮（IconButton，`@android:drawable/ic_btn_speak_now`）
- **语音识别流程**：
  1. 点击按钮时禁用输入框和按钮（显示加载状态）
  2. 调用 `android-speech listen --language zh-CN --max 1 --timeout 30`
  3. 解析 JSON：`{"success": true, "text": "识别结果"}` 或 `{"success": false, "error": "..."}`
  4. 成功时填充到 `panel_input`，失败时 Toast 提示错误
  5. 恢复按钮状态
- **权限检查**：Manifest 已有 `RECORD_AUDIO`，但悬浮窗无法动态请求权限，首次失败时提示用户在主界面授权

### 单元测试
- **ActionExecutorTest**：测试停止标志位在执行中、继续决策时的行为
- **AIDecisionEngineTest** 补充：验证 `done` 解析与 `generateContinuationPrompt` 格式

## 文件清单
| 文件 | 操作 | 说明 |
|---|---|---|
| `service/FloatingProgressOverlay.kt` | 修改 | 显示停止按钮，绑定回调 |
| `ui/ChatViewModel.kt` | 修改 | 增加 `stopAction()` 和标志位检查 |
| `data/ChatRepository.kt` | 无需修改 | `ActionStatus` 已有 `Canceled` 状态 |
| `service/FloatingWindowService.kt` | 修改 | 语音按钮处理与 `android-speech` 调用 |
| `res/layout/floating_panel.xml` | 修改 | 添加语音按钮 |
| `test/.../ActionExecutorTest.kt` | 新建 | 停止逻辑单元测试 |

## 错误记录
| 错误 | 尝试 | 处理 |
|---|---:|---|
| （待填充） | - | - |
