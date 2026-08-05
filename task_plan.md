# 言行 Agent 第十四阶段开发计划

## 目标
实现执行回滚（v1.0）：撤销上一个动作，支持点击返回、输入清空、滑动反向。

## 阶段
- [complete] 1-13. 前序阶段
- [in-progress] 14. 执行回滚（v1.0）—— 撤销上一个动作

## 本阶段验收标准
- [ ] `ActionExecutor` 新增 `back()`（全局返回）、`clearText(query)`（清空输入框）方法
- [ ] `RollbackController` 根据最近成功日志生成逆操作建议
  - `click` → `back()`
  - `input_text` → `clearText(targetElement)`
  - `swipe` → 反方向 swipe
- [ ] `ChatViewModel` 维护已执行动作栈 `executedActions`，提供 `undoLastAction()` 入口
- [ ] UI 显示：悬浮窗和执行状态区加「撤销」按钮，确认后执行逆操作并记日志
- [ ] 逆操作记为 `actionType="rollback"`，结果写进会话摘要
- [ ] GitHub Actions 编译、测试成功

## 技术方案

### RollbackController
纯逻辑类，不依赖 Android 框架：
```kotlin
object RollbackController {
    fun suggestRollback(action: AIDecisionEngine.Action, currentPackage: String): Suggestion {
        return when (action) {
            is Click -> Suggestion(description = "返回上一页", actions = listOf(Back))
            is InputText -> Suggestion(description = "清空输入框", actions = listOf(ClearText(action.query)))
            is Swipe -> Suggestion(description = "反向滑动 ${direction.name}", actions = listOf(Swipe(reversed(direction))))
            is LongPress -> Suggestion(description = "无法自动撤销，请手动恢复")
        }
    }
}
```
**置信度评分**：点击/滑动有较高的回溯效果（置信度 0.8+），长按无明确逆操作（0.2）。

### ActionExecutor 扩展
```kotlin
suspend fun back(): ActionResult  // service.performGlobalAction(GLOBAL_ACTION_BACK)
suspend fun clearText(query: String): ActionResult  // 聚焦 + SET_TEXT("")
```
注意：`clearText` 需要找到输入框节点后清空内容，不能直接设空字符串（某些应用是只读文本）。

### ChatViewModel 状态管理
- 新字段：`private var executedActions = mutableListOf<AIDecisionEngine.Action>()`
- 每成功执行一个动作后压入栈
- `executePendingAction` 里在动作成功后 `executedActions.add(action)`
- `undoLastAction()`: pop 栈顶 → RollbackController.suggest() → 展示确认卡片 → 执行逆操作 → 记 rollback 日志

### UI
1. **悬浮窗**：`FloatingProgressOverlay` 添加「撤销」按钮（紧接停止按钮下方），点击时触发回调给 ViewModel
2. **聊天界面**：在 Executing/PendingConfirm 状态区域底部增加撤销按钮
3. **确认流程**：直接执行逆操作 + Toast 提示（「已撤销：XX 操作」）—— v1 不做二次确认简化复杂度

### 日志记录
逆操作记入日志表，`actionType="rollback"`, `details="已撤销：点击 [xxx] → 返回"`

### 单元测试
新增 `RollbackControllerTest.kt`，验证不同动作类型的逆操作建议是否正确（无需真机执行）。

## 文件清单
| 文件 | 操作 | 说明 |
|---|---|---|
| `service/ActionExecutor.kt` | 修改 | 新增 `back()`, `clearText()` |
| `service/RollbackController.kt` | 新建 | 逆操作建议生成器 |
| `ui/ChatViewModel.kt` | 修改 | 引入 `executedActions` 栈、`undoLastAction()` |
| `service/FloatingProgressOverlay.kt` | 修改 | 加撤销按钮与回调 |
| `ui/AgentApp.kt` | 修改 | 聊天页面加撤销 UI |
| `test/.../RollbackControllerTest.kt` | 新建 | 逆操作建议单测 |

## 风险与边界
- **长按无明确逆操作**：LongPress 的效果取决于目标应用，简单回退可能无效，UI 需提示「无法自动撤销」
- **输入框恢复原文本**：v1.0 只做「清空」，不提供「恢复原文」功能（原文本不在 ActionLog 中记录）
- **全局返回的可靠性**：某些应用的导航树较深，单次 back 可能不够，留作后续增强
- **无障碍权限**：performGlobalAction 不需要额外授权，但部分定制 ROM 可能不支持
