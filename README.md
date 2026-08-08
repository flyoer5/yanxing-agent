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

## 第八阶段已完成

新增：

- **联网搜索**：基于 Tavily Search API
- **搜索开关**：聊天输入框旁的"联网/离线"按钮，随时切换
- **设置页配置**：可填写 Tavily API Key，默认开启搜索
- **结果注入**：开启后发送消息自动搜索，结果注入模型上下文
- **实时状态**：聊天页显示"正在联网搜索…"和"已联网搜索 N 条结果"
- **容错处理**：搜索失败不影响正常对话

## 第九阶段已完成

新增：

- **悬浮窗模式**：设置页开启后，任意界面显示可拖动的"言行"悬浮球
- **快捷面板**：点击悬浮球展开，可快速输入问题、打开主界面
- **无障碍服务**：读取当前屏幕文字，为"替我行动"打基础
- **Root 增强**：检测设备 Root 状态，封装受控命令执行（设备信息、屏幕控制等）
- **设置页状态显示**：悬浮窗开关、无障碍服务状态、Root 检测结果一目了然

## 第十阶段（替我行动 + 操作日志）已完成

新增：

- **替我行动模式**：开启后 AI 读取当前屏幕，规划并自动执行点击、长按、滑动、输入
- **智能元素识别**：精确匹配 → 唯一关键词 → Levenshtein 相似度 → 模糊匹配，4 层策略
- **自动重试**：动作失败自动重试 3 次（每次重新查找节点），等待 UI 稳定
- **逐动作确认**：执行前弹窗确认，支持批准/拒绝/跳过
- **执行进度悬浮窗**：实时显示成功/失败计数、当前步骤，可暂停/继续、拖拽位置
- **操作日志**：底部"日志"Tab，按应用过滤、查看时间/动作/状态/详情，支持清空
- **日志性能优化**：批量异步写入数据库（3x 提速，UI 不阻塞）

## 第十一阶段（多轮行动决策 + 固定签名）已完成

新增：

- **多轮行动决策**：一组动作执行完后自动回传结果给 LLM，AI 根据新屏幕决定继续或结束（最多 5 轮，防死循环）
- **任务完成 done 标记**：LLM 声明完成时写入会话消息并清理上下文
- **Thinking 状态**：聊天页和设置页显示"AI 正在分析第 N 轮执行结果…"
- **修复主链路缺陷**：替我行动消息不再走普通对话，直接进入"读屏 → LLM 决策 → 确认 → 执行"循环
- **修复执行顺序**：单动作任务不再被跳过
- **固定 APK 签名**：证书 CN=Yanxing Agent，有效期 2053 年，每次 CI 产出可直接覆盖安装

## 第十二阶段（执行停止 + 语音输入）已完成

新增：

- **执行停止**：执行中的任务随时可停。进度悬浮窗和聊天页都提供"停止"按钮，停止后不再发起新动作和新决策轮
- **停止摘要与日志**：停止时把已完成的动作摘要写入会话，并以 `cancelled` 状态记入操作日志
- **悬浮球语音输入**：快捷面板增加语音按钮，识别结果填入输入框，由用户确认后发送
- **聊天页语音接通**：麦克风按钮改为真实调用 `SpeechRecognizer`（此前只是占位状态标记）
- **修复进度计数**：多轮决策时进度不再跨轮累加（曾出现"3 / 2"）

## 第十三阶段（主线程阻塞修复 + Release 混淆）已完成

新增：

- **协程调度优化**：`ActionExecutor` 内部方法改 `suspend`，`Thread.sleep` → `kotlinx.coroutines.delay`；`ChatViewModel.executePendingAction` 用 `withContext(Dispatchers.Default)` 执行，避免阻塞主线程导致 ANR
- **Release 混淆开启**：`isMinifyEnabled = true` + `isShrinkResources = true`；ProGuard 规则补齐 Hilt、Room、OkHttp、Kotlin Serialization、Compose 等依赖保留指令
- **包体积优化**：Debug 19.1MB → Release 12.7MB，缩小 34%
- **单元测试验证**：新增 `ActionExecutorCoroutineTest`（5 个用例）确保 `delay` 可被取消、重试逻辑在取消时提前退出

## 第十四阶段（执行回滚 v1.0）已完成

新增：

- **逆操作生成器**：`RollbackController` 根据已执行动作生成逆向建议
  - `click` → `back()`（全局返回）
  - `input_text` → `clearText(query)`（清空输入框）
  - `swipe` → 反向滑动
- **ActionExecutor 扩展**：新增 `back()` 和 `clearText()` 方法
- **回滚栈管理**：`executedActions` 栈记录已成功执行的原始动作，`undoLastAction()` 弹出并执行逆操作
- **UI 入口**：悬浮窗加「撤销上一个」按钮，聊天界面 PendingConfirm 状态区也提供撤销按钮
- **日志支持**：逆操作记为 `actionType="rollback"`，结果写入会话摘要

## 第十五阶段（Root 增强 + 编译修复）已完成

新增：

- **Root 能力检测**：`RootShell.isRootAvailable()` 缓存式检测 su 路径（带超时，非 Root 环境不挂起）
- **系统控制命令**：
  - `batteryLevel()` 读取电池百分比
  - `screenBrightness()` / `setScreenBrightness()` 读写屏幕亮度（0-255 范围校验）
  - `wakeScreen()` 点亮屏幕、`showRecents()` 打开最近任务
  - `deviceInfo()` 获取型号 + 系统版本
- **编译修复**：`withService` 改 suspend lambda、修复 `back\|return` 转义、补齐 `Back`/`ClearText` 动作在 `RollbackController` 与 `AgentApp` 的 when 分支
- **单测修复**：亮度范围校验抽为无副作用纯函数 `isBrightnessInRange()`，无 root 环境可稳定断言

## 第十六阶段（执行回滚 v1.1）已完成

新增：

- **手动引导步骤**：`RollbackController.Suggestion` 新增 `manualSteps` 字段，长按 / 返回 / 清空输入等无法自动逆转的动作，生成具体的手动恢复步骤
- **撤销体验改进**：`undoLastAction()` 遇到无法自动逆转的动作时，不再静默跳过——把警告 + 分步引导写入会话，提示用户手动处理
- **栈语义修正**：无法自动逆转的动作从撤销队列移除（而非误导性保留），并刷新撤销按钮状态，用户可继续撤销更早的动作
- **单元测试**：新建 `RollbackControllerTest`（7 个用例），覆盖各动作类型的逆操作建议与四方向滑动反向穷尽验证

## 第十七阶段（Root 命令白名单 + 授权确认）已完成

新增：

- **默认拒绝**：Root 命令执行前必须通过用户授权，未授权一律返回 null，不触碰系统
- **命令白名单**：仅允许内置预定义命令（设备信息、电量、亮度读写、点亮屏幕、最近任务），亮度命令只接受 0-255 数字参数，任意 Shell 字符串一律拒绝
- **授权确认 UI**：设置页新增「Root 增强授权」开关，仅检测到 Root 时可开启；开启前弹出确认对话框，明确告知仅执行白名单命令
- **授权持久化**：授权状态存入 SharedPreferences；应用启动自动注入，ViewModel 销毁时撤销内存授权
- **单测**：新增授权状态测试与白名单校验测试（拒绝任意命令、越界亮度、命令注入等）

## 第十八阶段（悬浮窗结果展示 + Root 命令扩展）已完成

新增：

- **悬浮窗实时结果**：每个动作执行后，在悬浮窗内直接展示结果消息（成功绿色 / 失败红色），行动模式全程无需切回主界面；新增 `showResult()` 与结果状态字段
- **悬浮窗布局**：新增结果文本行，窗口高度自适应调至 400dp
- **Root 命令扩展**：新增返回桌面（`goHome`）与第三方应用列表（`appList`），并纳入白名单
- **单测**：新增 `FloatingProgressOverlayStateTest`（4 用例）验证结果状态流转；`RootShellCommandTest` 白名单补新命令

## 第十九阶段（悬浮窗主题适配 + 边缘吸附）已完成

新增：

- **深色模式适配**：悬浮窗卡片/文字/按钮分色跟随系统主题，深色下暗底浅字不刺眼，浅色保持原有观感
- **配色纯函数**：抽出 `resolveOverlayColors(darkMode)` 与 `OverlayColors` 数据类，明暗自适应无 Android 运行时依赖
- **边缘吸附**：拖动悬浮球松手后自动水平吸附到最近屏幕边缘，避免拖出屏幕或悬在中间
- **单测**：新建 `OverlayThemeTest`（6 用例），覆盖深浅配色映射、对比度、成功/失败可读性与吸附坐标计算

## 第二十阶段（ActionExecutor 相似度引擎结对测试）已完成

新增：

- **结对测试**：新建 `ActionExecutorSimilarityTest`（7 用例），覆盖「替我行动」核心匹配引擎 `calculateSimilarity`（Levenshtein 距离）——
  - 恒等字符串满分、空字符串边界、长文本近似高分、无关文本低分、对称性、区间约束
- **可测性**：`calculateSimilarity` 由 `private` 提升为 `internal`，纯函数零 Android 依赖
- 踩坑：空对空按「相等即完全匹配」返回 1.0 而非 0，测试断言需与实现语义对齐

## 第二十一阶段（悬浮窗提示条视觉化）已完成

新增：

- **窗口内提示条**：悬浮窗的 `toast()` 从"仅打日志"升级为窗口内提示文本——"撤销完成"、"无法撤销，已给出手动引导"、"没有可撤销的动作"等提示现在直接显示在悬浮窗内，3 秒自动消退
- **状态化提示**：`CheckboxState` 新增 `notice` 字段与 `clearNotice()`，提示走状态流渲染，颜色跟随主题
- **防过期覆盖**：提示条用 postDelayed 延迟清除，仅在内容未变化时生效，避免旧定时器清掉新提示
- **单测**：`FloatingProgressOverlayStateTest` 补 3 用例（提示设置/清除、时长常量、过期竞态）

## 第二十二阶段（决策引擎回滚动作解析测试）已完成

新增：

- **解析测试补齐**：`AIDecisionEngineTest` 由 7 增至 12 用例，覆盖决策引擎对回滚专用动作的解析——
  - `back`、`return` 别名、`clear_text` 动作解析
  - 回滚动作与普通动作（click）混合解析
  - 非法 swipe 方向被丢弃但合法动作保留
- 踩坑：`return` 别名仅在 action 字段缺失时靠内容猜测生效，测试需匹配真实解析语义

## 第二十三阶段（行动日志导出分享）已完成

新增：

- **日志导出**：操作日志页新增「导出」按钮，将日志格式化为可读纯文本，通过系统分享面板分享（保存/发送/打印）
- **格式化纯函数**：`formatActionLogs()` 将日志列表转文本（标题、条数、时间/应用/动作/目标/详情/状态，错误信息可选展示），零 Android 依赖可单测；配套 `actionTypeLabel` / `actionStatusLabel` 中文化
- **单测**：新建 `ActionLogExporterTest`（5 用例），覆盖空列表占位、单条字段、失败含错误、多条第编号、回滚类标签映射

## 第二十四阶段（会话导出分享）已完成

新增：

- **会话导出**：聊天页顶栏新增导出按钮，将当前会话（标题 + 全部消息，角色标签区分，附件数量标注）格式化为纯文本，通过系统分享面板分享
- **格式化纯函数**：`formatConversation(title, messages)` + `roleLabel()` 零 Android 依赖可单测，空会话输出占位文本
- **单测**：新建 `ConversationExporterTest`（5 用例），覆盖空会话、单条消息、角色标签、附件标注、多消息结构

## 第二十五阶段（长期记忆导出分享）已完成

新增：

- **记忆导出**：记忆页新增导出按钮，将长期记忆列表格式化为纯文本（标题、条数、内容 + 分类、敏感标注），通过系统分享面板分享
- **格式化纯函数**：`formatMemories()` 零 Android 依赖可单测，空列表输出占位文本
- **单测**：新建 `MemoryExporterTest`（5 用例），覆盖空列表、单条内容与分类、敏感标注、多条编号、无敏感标志不误报

## 第二十六阶段（系统提示词测试 + CI 升级）已完成

新增：

- **系统提示词测试**：新建 `SystemPromptTest`（6 用例），覆盖决策引擎 `generateSystemPrompt`——核心行为约束、只返回 JSON、屏幕内容注入、上一步操作注入、自定义约束编号、默认约束兜底
- **CI 清理**：`actions/setup-java` 由 v4 升级 v5，消除每次构建的 deprecation 警告（android.yml + manual-build.yml）

## 第二十七阶段（主界面深色模式）已完成

新增：

- **主界面深色适配**：新增 `values-night/themes.xml` 同名主题，系统切深色模式时自动应用——Material 暗色底色 + 深色状态栏/导航栏 + 浅色系统图标，与 Compose 侧 `isSystemInDarkTheme` 配色保持一致
- 至此悬浮窗（第十九阶段）与主界面深色模式全部打通，全应用跟随系统主题

## 第二十八阶段（消息复制）已完成

新增：

- **消息复制**：点击或长按任意聊天消息气泡，将内容复制到剪贴板并弹出 Snackbar 提示，方便用户引用 AI 台词/操作结果
- 实现：`MessageBubble` 新增 `onCopy` 回调（optional），消息列表接入剪贴板 + 提示；`combinedClickable` 需 `ExperimentalFoundationApi` OptIn

## 第二十九阶段（流式生成可中断）已完成

新增：

- **停止生成**：流式回复生成期间，发送按钮变为停止按钮（✕），点击立即取消当前生成，无需等完整回复
- 实现：`ChatViewModel` 持有 `generationJob`（生成协程句柄），新增 `cancelGeneration()`；`ChatScreen` 按 `isSending` 状态切换按钮图标与行为

## 第三十阶段（长文本截断优化）已完成

新增：

- **日志长文本截断**：操作日志的详情/错误信息超长时显示 2 行 + 省略号，不再撑爆列表项
- **悬浮窗文本适配**：当前任务、结果行、提示条统一 2 行省略，长消息不撑爆 400dp 悬浮窗
- **测试补强**：`ActionLogExporterTest` 新增时间戳格式断言（`yyyy-MM-dd HH:mm:ss` 正则验证）
- 踩坑：Kotlin 反引号函数名内不能含 `:`；JUnit `assertTrue(message, condition)` 参数顺序

## 第三十一阶段（记忆编辑）已完成

新增：

- **记忆编辑**：记忆页每条记忆新增「编辑」按钮，打开对话框修改内容与分类（保存按钮内容为空时禁用），无需删了重记
- 实现：`MemoryDao` 新增 `findById`；`ChatRepository.updateMemory(id, content, category)` 用 upsert 覆盖、保留原 id 与创建时间，存在性/空内容校验；`ChatViewModel.updateMemory` 失败时提示错误

## 第三十二阶段（会话搜索）已完成

新增：

- **会话搜索**：会话列表对话框新增搜索框，按标题实时过滤（忽略大小写），无匹配时显示空态提示
- 实现：纯 UI 状态（`searchQuery` + `filteredConversations`），无需改动 ViewModel/数据层

## 第三十三阶段（续接提示词测试补全）已完成

新增：

- **测试补全**：`AIDecisionEngineTest` 26 用例——新增 `lastResult` 注入断言、长屏幕文本截断边界（10_000 字符截断后 < 6_000）
- 踩坑：`generateContinuationPrompt` 返回 `ChatMessageDto` 而非 String，断言需 `.content.orEmpty()`；轮次文案为"这是第 N 轮决策（上限 M 轮）"

## 第三十四阶段（Root 白名单全命令覆盖测试）已完成

新增：

- **白名单回归加固**：`RootShellCommandTest` 白名单测试从抽样 4 条命令扩展为**全部 8 条固定命令逐一断言**（设备信息/电量/读取亮度/清空最近任务/点亮/打开最近/返回桌面/应用列表），新增命令漏进白名单的回归风险归零

## 里程碑

全部 34 个阶段已完成：基础对话 → 多会话/记忆 → 多模态 → 联网搜索 → 系统增强 → AI 替你行动 → 多轮自主决策 → 可停可控 + 语音入口 → 上线质量（防 ANR + 混淆瘦身）→ 执行回滚 v1.0 → Root 增强 → 回滚引导 v1.1 → Root 白名单授权 → 悬浮窗结果展示 + Root 命令扩展 → 悬浮窗主题与吸附 → 相似度引擎结对测试 → 悬浮窗提示条视觉化 → 决策引擎回滚解析测试 → 行动日志导出 → 会话导出 → 长期记忆导出 → 系统提示词测试 + CI 升级 → 主界面深色模式 → 消息复制 → 流式生成可中断 → 长文本截断优化 → 记忆编辑 → 会话搜索 → 续接提示词测试补全 → Root 白名单全命令覆盖。
