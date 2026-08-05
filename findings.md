# Findings

- 第一阶段使用随机会话 ID，第二阶段改为由 `ChatViewModel` 持有当前会话 ID，并由根界面负责切换。
- Room 数据库当前 version=1；本阶段新增 `groups`、`memories` 表，并给 `conversations` 增加 `groupId`，使用 destructive migration 仅适用于当前开发期空数据场景，正式版需提供迁移。
- 长期记忆自动提取采用保守规则：只从以"我喜欢/我偏好/请记住/我正在/我的项目是"等明确表达中提取；不保存 API Key、密码、验证码、Token 等凭据。
- 记忆检索首版使用本地关键词匹配，后续再加入本地语义索引；相关记忆以系统消息注入请求。
- 悬浮窗语音识别受限于 Service 无 Activity 上下文，无法动态弹出权限申请；首次失败时引导用户到主界面授权 RECORD_AUDIO。
- `voiceInputMode` 在第十二阶段前是空壳（只有标记，从不消费），麦克风按钮实际未实现；本阶段修正为实际调用 `SpeechRecognizer`。
- 进度悬浮窗多轮决策时计数累加，需在每组动作开始（index == 0）时 `resetProgress()`，避免显示"3 / 2"之类错乱。
