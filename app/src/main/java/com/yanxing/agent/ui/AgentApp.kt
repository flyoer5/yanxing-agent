package com.yanxing.agent.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.yanxing.agent.data.Attachment
import com.yanxing.agent.data.ActionLogEntity
import com.yanxing.agent.data.formatActionLogs
import com.yanxing.agent.data.formatLogTime
import com.yanxing.agent.data.formatMessageTime
import com.yanxing.agent.data.formatMessageStatus
import com.yanxing.agent.data.formatConversation
import com.yanxing.agent.data.formatMemories
import com.yanxing.agent.data.ChatMessage
import com.yanxing.agent.data.Conversation
import com.yanxing.agent.data.ConversationGroup
import com.yanxing.agent.data.filterConversations
import com.yanxing.agent.data.sortConversations
import com.yanxing.agent.data.Memory
import com.yanxing.agent.data.ActionStatus
import com.yanxing.agent.service.AIDecisionEngine
import com.yanxing.agent.service.ScreenReaderAccessibilityService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentApp(
    viewModel: ChatViewModel = hiltViewModel(),
    initialText: String? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editedMessageIds by viewModel.editedMessageIds.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var showSessions by remember { mutableStateOf(false) }
    var pendingTopBarArchive by remember { mutableStateOf<Conversation?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val appContext = LocalContext.current

    LaunchedEffect(initialText) {
        if (!initialText.isNullOrBlank()) {
            viewModel.updateDraft(initialText)
        }
    }
    
    // 撤销按钮的回调
    val onUndoAction = { viewModel.undoLastAction() }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(state.settingsSaved) {
        if (state.settingsSaved) snackbarHostState.showSnackbar("模型配置已保存")
    }
    LaunchedEffect(state.memoryNotice) {
        state.memoryNotice?.let {
            val result = snackbarHostState.showSnackbar(
                message = "已记住：${it.content}",
                actionLabel = "撤销",
                duration = androidx.compose.material3.SnackbarDuration.Long,
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                viewModel.deleteMemory(it.id)
            }
            viewModel.dismissMemoryNotice()
        }
    }

    pendingTopBarArchive?.let { conversation ->
        AlertDialog(
            onDismissRequest = { pendingTopBarArchive = null },
            title = { Text("归档当前会话？") },
            text = { Text("「${conversation.title}」将从默认会话列表隐藏，可稍后恢复。") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.toggleArchiveConversation(conversation.id)
                        pendingTopBarArchive = null
                    },
                ) { Text("归档") }
            },
            dismissButton = {
                TextButton(onClick = { pendingTopBarArchive = null }) { Text("取消") }
            },
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    if (selectedTab == 0) {
                        val currentTitle = state.conversations
                            .find { it.id == state.selectedConversationId }?.title ?: "言行 Agent"
                        Column(
                            modifier = Modifier.clickable { showSessions = true }
                        ) {
                            Text(currentTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = state.model.ifBlank { "未配置模型（点击设置）" },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (state.model.isBlank()) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                modifier = if (state.model.isBlank()) {
                                    Modifier.clickable { showSettings = true }
                                } else Modifier,
                            )
                        }
                    } else {
                        Text(tabTitle(selectedTab))
                    }
                },
                navigationIcon = {
                    if (selectedTab == 0) {
                        IconButton(onClick = { showSessions = true }) {
                            Icon(Icons.Outlined.Chat, contentDescription = "会话列表")
                        }
                    }
                },
                actions = {
                    val topBarConversation = state.conversations.find { it.id == state.selectedConversationId }
                    if (topBarConversation != null) {
                        IconButton(
                            onClick = {
                                if (topBarConversation.archived) viewModel.toggleArchiveConversation(topBarConversation.id)
                                else pendingTopBarArchive = topBarConversation
                            },
                            modifier = Modifier.semantics {
                                contentDescription = if (topBarConversation.archived) "恢复当前会话" else "归档当前会话"
                            },
                        ) {
                            Text(if (topBarConversation.archived) "↩" else "归档", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    IconButton(onClick = {
                        val conversation = state.conversations.find { it.id == state.selectedConversationId }
                        val title = conversation?.title ?: "当前会话"
                        val text = formatConversation(title, state.messages)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        runCatching {
                            appContext.startActivity(Intent.createChooser(intent, "导出会话"))
                        }
                    }) {
                        Icon(Icons.Outlined.Share, contentDescription = "导出会话")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "模型设置")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Outlined.Chat, contentDescription = null) },
                    label = { Text("聊天") },
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Outlined.Memory, contentDescription = null) },
                    label = { Text("记忆") },
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Outlined.List, contentDescription = null) },
                    label = { Text("日志") },
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    label = { Text("设置") },
                )
            }
        },
    ) { padding ->
        when (selectedTab) {
            0 -> ChatScreen(
                state = state,
                editedMessageIds = editedMessageIds,
                onDraftChanged = viewModel::updateDraft,
                onSend = viewModel::send,
                onResend = viewModel::resendMessage,
                onEditMessage = viewModel::editMessage,
                onCancelGeneration = viewModel::cancelGeneration,
                onToggleStreaming = viewModel::toggleStreaming,
                onAddAttachment = viewModel::addAttachment,
                onRemoveAttachment = viewModel::removeAttachment,
                onVoiceInput = viewModel::startVoiceInput,
                onToggleSearch = viewModel::toggleSearchEnabled,
                onConfirmAction = viewModel::confirmCurrentAction,
                onStopAction = viewModel::stopAction,
                onUndoAction = onUndoAction,
                onShowSnackbar = { message -> snackbarHostState.showSnackbar(message) },
                modifier = Modifier.padding(padding),
            )
            1 -> MemoryScreen(
                memories = state.memories,
                onDelete = viewModel::deleteMemory,
                onUpdate = viewModel::updateMemory,
                onAdd = viewModel::addMemory,
                onClearAll = viewModel::clearAllMemories,
                appContext = appContext,
                modifier = Modifier.padding(padding),
            )
            2 -> ActionLogScreen(
                logs = state.actionLogs,
                onClearAll = viewModel::clearAllActionLogs,
                onClearPackage = viewModel::clearActionLogsForPackage,
                modifier = Modifier.padding(padding),
            )
            else -> SettingsScreen(
                state = state,
                onBaseUrlChanged = viewModel::updateBaseUrl,
                onApiKeyChanged = viewModel::updateApiKey,
                onModelChanged = viewModel::updateModel,
                onSearchApiKeyChanged = viewModel::updateSearchApiKey,
                onToggleSearch = viewModel::toggleSearchEnabled,
                onToggleFloatingWindow = viewModel::toggleFloatingWindow,
                onSetRootAuthorization = viewModel::setRootAuthorization,
                onToggleActionMode = viewModel::toggleActionMode,
                onStartActionMode = viewModel::startActionMode,
                onAccessibilitySettings = {
                    runCatching {
                        appContext.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                },
                onSave = {
                    viewModel.saveSettings()
                    showSettings = false
                    coroutineScope.launch { snackbarHostState.showSnackbar("设置已保存") }
                },
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (showSettings) {
        SettingsDialog(
            state = state,
            onBaseUrlChanged = viewModel::updateBaseUrl,
            onApiKeyChanged = viewModel::updateApiKey,
            onModelChanged = viewModel::updateModel,
            onSearchApiKeyChanged = viewModel::updateSearchApiKey,
            onToggleSearch = viewModel::toggleSearchEnabled,
            onToggleFloatingWindow = viewModel::toggleFloatingWindow,
            onSetRootAuthorization = viewModel::setRootAuthorization,
            onToggleActionMode = viewModel::toggleActionMode,
            onStartActionMode = viewModel::startActionMode,
                onAccessibilitySettings = {
                    runCatching {
                        appContext.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                },
            onSave = {
                    viewModel.saveSettings()
                    showSettings = false
                    coroutineScope.launch { snackbarHostState.showSnackbar("设置已保存") }
                },
            onDismiss = { showSettings = false },
        )
    }

    if (showSessions) {
        SessionsDialog(
            conversations = state.conversations,
            groups = state.groups,
            selectedId = state.selectedConversationId,
            onNew = { viewModel.newConversation(); showSessions = false },
            onSelect = { viewModel.switchConversation(it); showSessions = false },
            onDelete = viewModel::deleteConversation,
                onRename = viewModel::renameConversation,
                onTogglePin = viewModel::togglePinConversation,
                onToggleArchive = viewModel::toggleArchiveConversation,
                onSearchByContent = viewModel::searchConversationsByContent,
            onCreateGroup = viewModel::createGroup,
            onAssignGroup = viewModel::assignCurrentConversation,
                onDeleteGroup = viewModel::deleteGroup,
                onRenameGroup = viewModel::renameGroup,
            onDismiss = { showSessions = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(
    state: ChatUiState,
    editedMessageIds: Set<String>,
    onDraftChanged: (String) -> Unit,
    onSend: () -> Unit,
    onResend: (ChatMessage) -> Unit,
    onEditMessage: (String, String, (Boolean) -> Unit) -> Unit,
    onCancelGeneration: () -> Unit,
    onToggleStreaming: () -> Unit,
    onAddAttachment: (Attachment) -> Unit,
    onRemoveAttachment: (Int) -> Unit,
    onVoiceInput: () -> Unit,
    onToggleSearch: () -> Unit,
    onConfirmAction: (Boolean) -> Unit,
    onStopAction: () -> Unit,
    onUndoAction: () -> Unit,
    onShowSnackbar: suspend (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val inputFocusRequester = remember { FocusRequester() }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var messageTimeTick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000L)
            messageTimeTick++
        }
    }
    val messageNow = remember(messageTimeTick) { System.currentTimeMillis() }
    // 切换会话时自动聚焦输入框（切 tab 回来不弹键盘；行动执行中不抢焦点）
    LaunchedEffect(state.selectedConversationId) {
        if (state.actionStatus is ActionStatus.Idle) {
            kotlinx.coroutines.delay(200)
            inputFocusRequester.requestFocus()
        }
    }
    // 行动模式执行时自动收起键盘（避免遮挡悬浮窗与屏幕内容）
    LaunchedEffect(state.actionStatus) {
        if (state.actionStatus !is ActionStatus.Idle) {
            keyboardController?.hide()
        }
    }
    val listState = rememberLazyListState()
    var showJumpToBottom by remember { mutableStateOf(false) }

    // 图片选择器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { picked ->
            val mimeType = context.contentResolver.getType(picked) ?: "image/jpeg"
            val name = getFileName(context, picked) ?: "image_${System.currentTimeMillis()}.jpg"
            // 拷贝到应用私有目录（IO 协程）：内容 URI 的读取权限不持久；
            // base64 只在发送时现读，不常驻内存
            coroutineScope.launch {
                val attachment = withContext(Dispatchers.IO) {
                    copyToLocalAttachment(context, picked, "image", mimeType, name)
                }
                if (attachment != null) onAddAttachment(attachment)
                else onShowSnackbar("图片读取失败")
            }
        }
    }

    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { picked ->
            val mimeType = context.contentResolver.getType(picked) ?: "application/octet-stream"
            val name = getFileName(context, picked) ?: "file_${System.currentTimeMillis()}"
            coroutineScope.launch {
                val attachment = withContext(Dispatchers.IO) {
                    copyToLocalAttachment(context, picked, "file", mimeType, name)
                }
                if (attachment != null) onAddAttachment(attachment)
                else onShowSnackbar("文件读取失败")
            }
        }
    }

    // 录音权限请求
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onVoiceInput()
        } else {
            coroutineScope.launch { onShowSnackbar("需要录音权限才能使用语音输入") }
        }
    }

    LaunchedEffect(state.messages.size, state.inProgressReply, state.actionStatus) {
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val nearBottom = state.messages.isEmpty() ||
            last >= (state.messages.lastIndex - 1)
        // 行动执行中始终跟随最新状态
        if (state.actionStatus !is ActionStatus.Idle) {
            if (state.messages.isNotEmpty()) {
                listState.animateScrollToItem(state.messages.lastIndex)
            }
            showJumpToBottom = false
        } else if (nearBottom && state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
            showJumpToBottom = false
        } else {
            showJumpToBottom = state.messages.isNotEmpty()
        }
    }

    val canSend = (state.draft.isNotBlank() || state.pendingAttachments.isNotEmpty()) &&
        (!state.isSending || state.actionStatus !is ActionStatus.Idle)

    // 行动模式下的特殊处理
    val isActionMode = state.actionModeEnabled

    Column(modifier = modifier.fillMaxSize()) {
        // 消息列表
        if (state.messages.isEmpty()) {
            // 新会话行动中：显示行动状态概要（读屏/确认/决策）
            if (state.actionStatus !is ActionStatus.Idle) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when (state.actionStatus) {
                        is ActionStatus.Readying -> {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("正在读取当前界面…", style = MaterialTheme.typography.labelSmall)
                        }
                        is ActionStatus.Ready -> {
                            Text("已读取界面，等待确认动作", style = MaterialTheme.typography.labelSmall)
                        }
                        is ActionStatus.Thinking -> {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("正在思考下一步…", style = MaterialTheme.typography.labelSmall)
                        }
                        is ActionStatus.Executing -> {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("正在执行动作…", style = MaterialTheme.typography.labelSmall)
                        }
                        is ActionStatus.Completed -> {
                            Text("行动已完成", style = MaterialTheme.typography.labelSmall)
                        }
                        is ActionStatus.PendingConfirm.Waiting -> {
                            val pending = state.actionStatus
                            Column {
                                Text(
                                    "等待确认第 ${pending.index + 1}/${pending.actions.size} 个操作",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                Row(
                                    modifier = Modifier.padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Button(
                                        onClick = { onConfirmAction(true) },
                                        modifier = Modifier.height(32.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                    ) { Text("执行", style = MaterialTheme.typography.labelMedium) }
                                    TextButton(
                                        onClick = { onConfirmAction(false) },
                                        modifier = Modifier.height(32.dp),
                                    ) { Text("跳过", style = MaterialTheme.typography.labelMedium) }
                                }
                            }
                        }
                        is ActionStatus.PendingConfirm.Canceled -> {
                            Text("行动已取消", style = MaterialTheme.typography.labelSmall)
                        }
                        else -> {}
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("你好，我是言行 Agent", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text("配置模型后，开始和我对话吧", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(
                    "试试说：帮我打开设置 / 替我在这个页面点击",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { keyboardController?.hide() },
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        onCopy = { text ->
                            clipboardManager.setText(AnnotatedString(text))
                            coroutineScope.launch {
                                val copiedLabel = buildString {
                                    append("已复制")
                                    val status = formatMessageStatus(
                                        createdAt = message.createdAt,
                                        isEdited = message.id in editedMessageIds,
                                        now = messageNow,
                                    )
                                    if (status.isNotBlank()) append(" $status")
                                    append("的消息")
                                }
                                onShowSnackbar(copiedLabel)
                            }
                        },
                        onResend = if (message.role == "user") {
                            { onResend(message) }
                        } else null,
                        onEdit = if (message.role == "user") {
                            { editingMessage = message }
                        } else null,
                                        isEdited = message.id in editedMessageIds,
                                        now = messageNow,
                                        operationsEnabled = !state.isSending,
                    )
                }

                // 行动模式的状态和屏幕内容显示
                if (isActionMode) {
                    when (val status = state.actionStatus) {
                        is ActionStatus.Readying -> {
                            item(key = "action-loading") {
                                Row(
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("正在读取当前界面…", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        is ActionStatus.Ready -> {
                            item(key = "screen-content") {
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = androidx.compose.material3.CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    ),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                    ) {
                                        Text(
                                            text = "${state.lastScreenPackage.take(24)} (已就绪)",
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = status.screenText.replace("\n", " "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        is ActionStatus.Thinking -> {
                            item(key = "action-thinking") {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "AI 正在分析第 ${status.round} 轮执行结果…",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.weight(1f),
                                    )
                                    StopActionButton(onStopAction)
                                }
                            }
                        }
                        is ActionStatus.Executing -> {
                            item(key = "action-executing") {
                                val progress = if (status.total == 0) 0f else status.current.toFloat() / status.total
                                LinearProgressIndicator(
                                    progress = { progress.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "正在执行 ${status.current}/${status.total}: ${status.actionDesc ?: ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                    StopActionButton(onStopAction)
                                }
                            }
                        }
                        is ActionStatus.PendingConfirm.Waiting -> {
                            val action = status.actions.getOrNull(status.index)
                            if (action != null) {
                                item(key = "action-confirm-${status.index}") {
                                    Column {
                                        ConfirmActionCard(action) { approved ->
                                            onConfirmAction(approved)
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            StopActionButton(onStopAction)
                                            UndoActionButton(onUndoAction)
                                        }
                                    }
                                }
                            }
                        }
                        is ActionStatus.PendingConfirm.Canceled -> {
                            item(key = "action-canceled") {
                                Text("行动已停止", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        is ActionStatus.Completed -> {
                            item(key = "action-result") {
                                val success = status.successCount == status.totalCount
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = androidx.compose.material3.CardDefaults.cardColors(
                                        containerColor = if (success) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.errorContainer,
                                    ),
                                ) {
                                    Text(
                                        text = "执行完成：${status.successCount}/${status.totalCount} 成功",
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.padding(8.dp),
                                        color = if (success) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                        is ActionStatus.Idle -> {}
                    }
                }

                if (state.searching) {
                    item(key = "searching") {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "正在联网搜索…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (state.searchResultCount > 0 && !state.searching) {
                    item(key = "search-result") {
                        Text(
                            text = "已联网搜索 ${state.searchResultCount} 条结果并注入上下文",
                            modifier = Modifier.padding(horizontal = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (state.memoryReferenceCount > 0 && state.inProgressReply.isEmpty()) {
                    item(key = "memory-reference") {
                        Text(
                            text = "本次使用了 ${state.memoryReferenceCount} 条记忆/历史内容",
                            modifier = Modifier.padding(horizontal = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (state.inProgressReply.isNotEmpty()) {
                    item(key = "in-progress") {
                        MessageBubble(ChatMessage("in-progress", "assistant", state.inProgressReply))
                    }
                }
                if (state.isSending && state.inProgressReply.isNotEmpty()) {
                    item(key = "typing-indicator") {
                        // 打字指示：三点循环更新（LaunchedEffect 简单循环，避免动画 API 版本差异）
                        var dotCount by remember { mutableStateOf(0) }
                        LaunchedEffect(Unit) {
                            while (true) {
                                delay(450L)
                                dotCount = (dotCount + 1) % 4
                            }
                        }
                        Text(
                            text = "正在生成" + ".".repeat(dotCount),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // 待发送附件预览
        if (state.pendingAttachments.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(state.pendingAttachments.indices.toList()) { index ->
                    val att = state.pendingAttachments[index]
                    AttachmentPreview(att, onRemove = { onRemoveAttachment(index) })
                }
            }
        }

        // 行动目标指示条（行动中显示）
        if (state.actionGoal.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = buildString {
                        append("🎯 正在执行：${state.actionGoal}")
                        val st = state.actionStatus
                        if (st is ActionStatus.Thinking) {
                            append("（第 ${st.round} 轮决策）")
                        } else if (st is ActionStatus.Executing) {
                            append("（执行中 ${st.current}/${st.total}）")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onStopAction) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "停止行动",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }

        // 跳到底部浮钮（新消息到达且用户不在底部时）
        if (showJumpToBottom) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                androidx.compose.material3.SuggestionChip(
                    onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(state.messages.lastIndex)
                        }
                        showJumpToBottom = false
                    },
                    label = { Text("↓ 跳到最新消息") },
                    colors = androidx.compose.material3.SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                )
            }
        }

        // 输入区域
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // 图片按钮
            IconButton(
                onClick = { imagePickerLauncher.launch("image/*") },
                enabled = !state.isSending,
            ) {
                Icon(Icons.Outlined.Image, contentDescription = "发送图片")
            }
            // 文件按钮
            IconButton(
                onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                enabled = !state.isSending,
            ) {
                Icon(Icons.Outlined.AttachFile, contentDescription = "发送文件")
            }
            // 语音输入按钮
            IconButton(onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                    onVoiceInput()
                } else {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }) {
                Icon(Icons.Outlined.Mic, contentDescription = "语音输入")
            }

            OutlinedTextField(
                value = state.draft,
                onValueChange = onDraftChanged,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(inputFocusRequester),
                placeholder = {
                    Text(
                        if (isActionMode) "输入任务，如：打开微信" else "输入消息…"
                    )
                },
                maxLines = 5,
                // 行动执行中允许输入（插话），仅普通回复生成时禁用
                enabled = !state.isSending || state.actionStatus !is ActionStatus.Idle,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (canSend) onSend()
                }),
                trailingIcon = {
                    if (state.draft.isNotEmpty()) {
                        IconButton(onClick = { onDraftChanged("") }) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "清空输入",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
            Spacer(Modifier.width(4.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 联网搜索开关
                IconButton(
                    onClick = onToggleSearch,
                    enabled = !state.isSending,
                ) {
                    Icon(
                        Icons.Outlined.Public,
                        contentDescription = "联网搜索",
                        tint = if (state.searchEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (state.searchEnabled) "联网" else "离线",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.searchEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(
                    onClick = when {
                        state.actionStatus !is ActionStatus.Idle -> onStopAction
                        state.isSending -> onCancelGeneration
                        else -> onSend
                    },
                    enabled = state.isSending || canSend || state.actionStatus !is ActionStatus.Idle,
                ) {
                    when {
                        state.actionStatus !is ActionStatus.Idle ->
                            Icon(Icons.Outlined.Close, contentDescription = "停止行动")
                        state.isSending ->
                            Icon(Icons.Outlined.Close, contentDescription = "停止生成")
                        else ->
                            Icon(Icons.Outlined.Send, contentDescription = "发送")
                    }
                }
                TextButton(onClick = onToggleStreaming) {
                    Text(if (state.streaming) "流式" else "完整", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }

    editingMessage?.let { message ->
        var editedContent by remember(message.id) { mutableStateOf(message.content) }
        AlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text("编辑消息") },
            text = {
                OutlinedTextField(
                    value = editedContent,
                    onValueChange = { editedContent = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 8,
                    label = { Text("消息内容") },
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onEditMessage(message.id, editedContent) { success ->
                                if (success) {
                                    editingMessage = null
                                    onResend(message)
                                }
                            }
                        },
                        enabled = editedContent.isNotBlank(),
                    ) { Text("保存并重发") }
                    TextButton(
                        onClick = {
                            onEditMessage(message.id, editedContent) { success ->
                                if (success) editingMessage = null
                            }
                        },
                        enabled = editedContent.isNotBlank(),
                    ) { Text("仅保存") }
                }
            },
            dismissButton = {
                TextButton(onClick = { editingMessage = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun AttachmentPreview(attachment: Attachment, onRemove: () -> Unit) {
    Box(
        modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp))
    ) {
        when (attachment.type) {
            "image" -> {
                AsyncImage(
                    model = attachment.uri,
                    contentDescription = "待发送图片",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            else -> {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.AttachFile, contentDescription = null, modifier = Modifier.size(24.dp))
                }
            }
        }
        // 删除按钮
        IconButton(
            onClick = onRemove,
            modifier = Modifier.align(Alignment.TopEnd).size(20.dp),
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "移除",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    onCopy: ((String) -> Unit)? = null,
    onResend: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    isEdited: Boolean = false,
    now: Long = System.currentTimeMillis(),
    operationsEnabled: Boolean = true,
) {
    val isUser = message.role == "user"
    var showActionMenu by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.86f else 0.94f)
                .combinedClickable(
                    onClick = { onCopy?.invoke(message.content) },
                    onLongClick = if (onEdit != null || onResend != null) {
                        { showActionMenu = true }
                    } else {
                        { onCopy?.invoke(message.content) }
                    },
                ),
            shape = if (isUser) {
                // 用户气泡贴右：右下角小圆角
                RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
            } else {
                // AI 气泡贴左：左下角小圆角
                RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
            },
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            ),
            elevation = androidx.compose.material3.CardDefaults.cardElevation(
                defaultElevation = 1.dp,
            ),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // 附件预览（图片）
                if (message.attachments.any { it.type == "image" }) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(message.attachments.filter { it.type == "image" }) { att ->
                            AsyncImage(
                                model = att.uri,
                                contentDescription = "图片",
                                modifier = Modifier.size(120.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                }
                // 文件列表
                if (message.attachments.any { it.type == "file" }) {
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        message.attachments.filter { it.type == "file" }.forEach { att ->
                            Row(
                                modifier = Modifier.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Outlined.AttachFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(att.name, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                // 文本内容
                if (message.content.isNotBlank()) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                // 时间戳与已编辑标记
                Row(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .semantics {
                            contentDescription = formatMessageStatus(
                                createdAt = message.createdAt,
                                isEdited = isEdited,
                                now = now,
                            )
                        },
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (message.createdAt > 0L) {
                        Text(
                            text = formatMessageTime(message.createdAt, now),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isEdited) {
                        Text(
                            text = "已编辑",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    // 用户消息重发/编辑按钮（气泡外，避免干扰内容区域）
    if (isUser && (onResend != null || onEdit != null)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            if (onEdit != null) {
                TextButton(
                    onClick = onEdit,
                    enabled = operationsEnabled,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        "编辑",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (onResend != null) {
                TextButton(
                    onClick = onResend,
                    enabled = operationsEnabled,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        "重发",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
    // 长按气泡快捷操作菜单
    DropdownMenu(expanded = showActionMenu, onDismissRequest = { showActionMenu = false }) {
        DropdownMenuItem(
            text = { Text("复制") },
            onClick = {
                showActionMenu = false
                onCopy?.invoke(message.content)
            },
        )
        if (onEdit != null) {
            DropdownMenuItem(
                text = { Text("编辑") },
                enabled = operationsEnabled,
                onClick = {
                    showActionMenu = false
                    onEdit()
                },
            )
        }
        if (onResend != null) {
            DropdownMenuItem(
                text = { Text("重发") },
                enabled = operationsEnabled,
                onClick = {
                    showActionMenu = false
                    onResend()
                },
            )
        }
    }
}

// ============ 辅助函数 ============

private fun getFileName(context: android.content.Context, uri: Uri): String? {
    var name: String? = null
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0) name = cursor.getString(nameIndex)
        }
    }
    return name
}

/**
 * 把选择的内容 URI 拷贝到应用私有目录（attachments/），返回以 file:// uri 标识的附件。
 * - 内容 URI 的读取权限在进程重启后失效，拷贝后可长期预览
 * - base64 不在此处生成，发送时按需现读，避免大字符串常驻内存与数据库
 */
private fun copyToLocalAttachment(
    context: android.content.Context,
    uri: Uri,
    type: String,
    mimeType: String,
    displayName: String?,
): Attachment? = try {
    val dir = java.io.File(context.filesDir, "attachments").apply { mkdirs() }
    val ext = mimeType.substringAfter('/').takeIf { it.isNotBlank() && it.all(Char::isLetterOrDigit) }
        ?.let { ".$it" } ?: ""
    val dest = java.io.File(
        dir,
        "att_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(8)}$ext",
    )
    val input = context.contentResolver.openInputStream(uri) ?: return null
    input.use { source -> dest.outputStream().use { source.copyTo(it) } }
    Attachment(
        type = type,
        uri = dest.toURI().toString(),
        mimeType = mimeType,
        name = displayName ?: dest.name,
        size = dest.length(),
        base64 = null,
    )
} catch (e: Exception) {
    null
}

// ============ 其他屏幕 ============

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    state: ChatUiState,
    onBaseUrlChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onModelChanged: (String) -> Unit,
    onSearchApiKeyChanged: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onToggleFloatingWindow: () -> Unit,
    onSetRootAuthorization: (Boolean) -> Unit,
    onToggleActionMode: () -> Unit,
    onStartActionMode: () -> Unit,
    onAccessibilitySettings: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settingsContext = LocalContext.current
    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("模型配置", style = MaterialTheme.typography.headlineSmall)
        Text("支持任意 OpenAI 兼容 API。Key 使用 Android Keystore 加密保存。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        ModelFields(state, onBaseUrlChanged, onApiKeyChanged, onModelChanged)
        SearchFields(state, onSearchApiKeyChanged, onToggleSearch)
        SystemFeaturesFields(state, onToggleFloatingWindow, onSetRootAuthorization) {
            runCatching { settingsContext.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        ActionModeFields(state, onToggleActionMode, onStartActionMode) {
            runCatching { settingsContext.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("保存配置") }
    }
}

@Composable
private fun ActionModeFields(
    state: ChatUiState,
    onToggleActionMode: () -> Unit,
    onStartActionMode: () -> Unit,
    onAccessibilitySettings: () -> Unit,
) {
    Text("替我行动", style = MaterialTheme.typography.titleMedium)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("启用行动模式", modifier = Modifier.weight(1f))
        Switch(checked = state.actionModeEnabled, onCheckedChange = { onToggleActionMode() })
    }
    Text(
        text = if (state.actionModeEnabled) "已开启：可在聊天界面让 AI 控制其他 App" else "关闭状态",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(8.dp))

    // 无障碍服务状态和行动入口
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("无障碍服务", style = MaterialTheme.typography.labelMedium)
            Text(
                text = if (state.accessibilityEnabled) "已启用 ✓" else "未启用 ✗",
                style = MaterialTheme.typography.bodySmall,
                color = if (state.accessibilityEnabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
            )
        }
        Button(
            onClick = {
                if (state.accessibilityEnabled) onStartActionMode()
                else onAccessibilitySettings()
            },
            enabled = state.actionModeEnabled && !state.isSending,
        ) {
            Text(if (state.accessibilityEnabled) "开始行动" else "先去开启无障碍")
        }
    }

    // 执行进度显示
    when (val status = state.actionStatus) {
        is ActionStatus.Idle -> {}
        is ActionStatus.Readying -> {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("正在读取当前界面…", style = MaterialTheme.typography.labelSmall)
            }
        }
        is ActionStatus.Ready -> {
            Spacer(Modifier.height(8.dp))
            Text("屏幕内容已就绪，可在聊天界面输入需求", style = MaterialTheme.typography.labelSmall)
        }
        is ActionStatus.Thinking -> {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("AI 正在分析第 ${status.round} 轮执行结果…", style = MaterialTheme.typography.labelSmall)
            }
        }
        is ActionStatus.Executing -> {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("正在执行：${status.current}/${status.total} (${status.actionDesc ?: ""})", style = MaterialTheme.typography.labelSmall)
            }
        }
        is ActionStatus.PendingConfirm.Waiting -> {
            Spacer(Modifier.height(8.dp))
            Text("等待确认第 ${status.index + 1}/${status.actions.size} 个操作", style = MaterialTheme.typography.labelSmall)
        }
        is ActionStatus.PendingConfirm.Canceled -> {
            Spacer(Modifier.height(8.dp))
            Text("行动已取消", style = MaterialTheme.typography.labelSmall)
        }
        is ActionStatus.Completed -> {
            Spacer(Modifier.height(8.dp))
            val successText = "${status.successCount}/${status.totalCount} 成功"
            Text(successText, style = MaterialTheme.typography.labelSmall, color = if (status.successCount == status.totalCount) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SystemFeaturesFields(
    state: ChatUiState,
    onToggleFloatingWindow: () -> Unit,
    onSetRootAuthorization: (Boolean) -> Unit,
    onOpenAccessibility: () -> Unit,
) {
    var showRootConfirmation by remember { mutableStateOf(false) }

    Text("系统增强", style = MaterialTheme.typography.titleMedium)
    // 悬浮窗开关
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("悬浮窗模式", modifier = Modifier.weight(1f))
        Switch(
            checked = state.floatingWindowEnabled,
            onCheckedChange = { onToggleFloatingWindow() },
        )
    }
    Text(
        text = when {
            state.floatingWindowEnabled -> "悬浮窗已开启，可在任意界面快速发起对话"
            else -> "开启后在任意界面显示悬浮球，点击可快速发起对话"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    // 无障碍服务状态（未启用时可点击跳转设置）
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .then(
                if (!state.accessibilityEnabled) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onOpenAccessibility() }
                } else Modifier,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (state.accessibilityEnabled) "无障碍服务" else "无障碍服务（点击开启）",
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (state.accessibilityEnabled) "已启用" else "未启用",
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.accessibilityEnabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error,
        )
    }
    // Root 状态
    state.rootAvailable?.let { root ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Root 权限", modifier = Modifier.weight(1f))
            Text(
                text = if (root) "已检测到" else "未检测到",
                style = MaterialTheme.typography.bodyMedium,
                color = if (root) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // 电池电量（需 Root）
        if (root && state.batteryLevel.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("当前电量", modifier = Modifier.weight(1f))
                Text(
                    text = state.batteryLevel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Root 增强授权")
                Text(
                    text = if (state.rootAuthorized) "已授权：仅允许白名单命令"
                    else "未授权：Root 命令默认拒绝",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.rootAuthorized,
                enabled = root,
                onCheckedChange = { enabled ->
                    if (enabled) showRootConfirmation = true
                    else onSetRootAuthorization(false)
                },
            )
        }
        Text(
            "仅允许设备信息、电量、亮度、点亮屏幕和最近任务等预定义命令；不会执行任意 Shell 字符串。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showRootConfirmation) {
        AlertDialog(
            onDismissRequest = { showRootConfirmation = false },
            title = { Text("确认启用 Root 增强？") },
            text = {
                Text(
                    "启用后，言行只能执行内置白名单命令，并通过 su 调用 Root。" +
                        "请确认你了解这些命令会改变设备状态。"
                )
            },
            confirmButton = {
                Button(onClick = {
                    showRootConfirmation = false
                    onSetRootAuthorization(true)
                }) { Text("确认授权") }
            },
            dismissButton = {
                TextButton(onClick = { showRootConfirmation = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SearchFields(
    state: ChatUiState,
    onSearchApiKeyChanged: (String) -> Unit,
    onToggleSearch: () -> Unit,
) {
    Text("联网搜索", style = MaterialTheme.typography.titleMedium)
    var showSearchKey by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = state.searchApiKey,
        onValueChange = onSearchApiKeyChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Tavily API Key") },
        placeholder = { Text("tvly-...") },
        singleLine = true,
        visualTransformation = if (showSearchKey) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { showSearchKey = !showSearchKey }) {
                Icon(
                    if (showSearchKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (showSearchKey) "隐藏搜索 Key" else "显示搜索 Key",
                )
            }
        },
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("默认开启联网搜索", modifier = Modifier.weight(1f))
        Switch(checked = state.searchEnabled, onCheckedChange = { onToggleSearch() })
    }
    Text(
        "联网搜索使用 Tavily API（https://tavily.com），用于获取实时信息。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelFields(
    state: ChatUiState,
    onBaseUrlChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onModelChanged: (String) -> Unit,
) {
    var baseUrlMenuOpen by remember { mutableStateOf(false) }
    val presetBaseUrls = listOf(
        "https://api.openai.com/v1",
        "https://api.deepseek.com/v1",
        "https://api.anthropic.com/v1",
    )
    androidx.compose.material3.ExposedDropdownMenuBox(
        expanded = baseUrlMenuOpen,
        onExpandedChange = { baseUrlMenuOpen = it },
    ) {
        OutlinedTextField(
            value = state.baseUrl,
            onValueChange = onBaseUrlChanged,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            label = { Text("API 地址") },
            placeholder = { Text("https://api.example.com/v1") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            trailingIcon = {
                androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(
                    expanded = baseUrlMenuOpen,
                )
            },
        )
        androidx.compose.material3.DropdownMenu(
            expanded = baseUrlMenuOpen,
            onDismissRequest = { baseUrlMenuOpen = false },
        ) {
            presetBaseUrls.forEach { url ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(url) },
                    onClick = {
                        onBaseUrlChanged(url)
                        baseUrlMenuOpen = false
                    },
                )
            }
        }
    }
    var showApiKey by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = state.apiKey,
        onValueChange = onApiKeyChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("API Key") },
        singleLine = true,
        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { showApiKey = !showApiKey }) {
                Icon(
                    if (showApiKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (showApiKey) "隐藏 API Key" else "显示 API Key",
                )
            }
        },
    )
    var modelMenuOpen by remember { mutableStateOf(false) }
    val presetModels = listOf("gpt-4o-mini", "gpt-4o", "deepseek-chat", "claude-3-5-sonnet")
    androidx.compose.material3.ExposedDropdownMenuBox(
        expanded = modelMenuOpen,
        onExpandedChange = { modelMenuOpen = it },
    ) {
        OutlinedTextField(
            value = state.model,
            onValueChange = onModelChanged,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            label = { Text("模型名称") },
            placeholder = { Text("例如 gpt-4o-mini") },
            singleLine = true,
            trailingIcon = {
                androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(
                    expanded = modelMenuOpen,
                )
            },
        )
        androidx.compose.material3.DropdownMenu(
            expanded = modelMenuOpen,
            onDismissRequest = { modelMenuOpen = false },
        ) {
            presetModels.forEach { modelName ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(modelName) },
                    onClick = {
                        onModelChanged(modelName)
                        modelMenuOpen = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsDialog(
    state: ChatUiState,
    onBaseUrlChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onModelChanged: (String) -> Unit,
    onSearchApiKeyChanged: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onToggleFloatingWindow: () -> Unit,
    onSetRootAuthorization: (Boolean) -> Unit,
    onToggleActionMode: () -> Unit,
    onStartActionMode: () -> Unit,
    onAccessibilitySettings: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dialogContext = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("模型设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ModelFields(state, onBaseUrlChanged, onApiKeyChanged, onModelChanged)
                SearchFields(state, onSearchApiKeyChanged, onToggleSearch)
                SystemFeaturesFields(state, onToggleFloatingWindow, onSetRootAuthorization) {
                    runCatching { dialogContext.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                }
                ActionModeFields(state, onToggleActionMode, onStartActionMode) {
            runCatching { dialogContext.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
            }
        },
        confirmButton = { Button(onClick = onSave) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun PlaceholderScreen(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun ConfirmActionCard(action: AIDecisionEngine.Action, onConfirm: (Boolean) -> Unit) {
    val isClick = action is AIDecisionEngine.Action.Click
    val isSwipe = action is AIDecisionEngine.Action.Swipe

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = if (action is AIDecisionEngine.Action.InputText) "输入文本确认"
                    else if (isClick) "点击操作确认"
                    else if (isSwipe) "滑动操作确认"
                    else if (action is AIDecisionEngine.Action.Back) "返回操作确认"
                    else if (action is AIDecisionEngine.Action.ClearText) "清空输入确认"
                    else "长按操作确认",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))

            // 显示具体动作描述
            Text(
                text = when (action) {
                    is AIDecisionEngine.Action.Click -> "点击：${action.query}"
                    is AIDecisionEngine.Action.LongPress -> "长按：${action.query}"
                    is AIDecisionEngine.Action.Swipe -> "滑动方向：${action.direction.name}"
                    is AIDecisionEngine.Action.InputText -> "输入：\"${action.text}\""
                    is AIDecisionEngine.Action.Back -> "返回上一页"
                    is AIDecisionEngine.Action.ClearText -> "清空输入框：${action.query}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { onConfirm(true) },
                    enabled = !isSwipe, // 滑动动作不需要确认（风险低）
                    modifier = Modifier.weight(1f),
                ) {
                    Text("允许执行")
                }
                OutlinedButton(
                    onClick = { onConfirm(false) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("跳过")
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = "💡 AI 认为需要此操作，但你可以拒绝它。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StopActionButton(onStop: () -> Unit) {
    androidx.compose.material3.TextButton(
        onClick = onStop,
        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.error,
        ),
    ) {
        Icon(
            Icons.Outlined.Close,
            contentDescription = "停止执行",
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text("停止", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun UndoActionButton(onUndo: () -> Unit) {
    androidx.compose.material3.TextButton(
        onClick = onUndo,
        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
        ),
    ) {
        Icon(
            Icons.Outlined.Refresh,
            contentDescription = "撤销上一个动作",
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text("撤销", style = MaterialTheme.typography.labelMedium)
    }
}

private fun tabTitle(index: Int) = when (index) {
    0 -> "聊天"
    1 -> "记忆"
    2 -> "日志"
    else -> "设置"
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SessionsDialog(
    conversations: List<Conversation>,
    groups: List<ConversationGroup>,
    selectedId: String,
    onNew: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRename: (id: String, newTitle: String) -> Unit,
    onTogglePin: (String) -> Unit,
    onToggleArchive: (String) -> Unit,
    onSearchByContent: (keyword: String, onResult: (List<String>) -> Unit) -> Unit,
    onCreateGroup: (String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onRenameGroup: (id: String, newName: String) -> Unit,
    onAssignGroup: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var renameTarget by remember { mutableStateOf<Conversation?>(null) }
    var renameGroupTarget by remember { mutableStateOf<ConversationGroup?>(null) }
    var pendingDelete by remember { mutableStateOf<Conversation?>(null) }
    var pendingArchive by remember { mutableStateOf<Conversation?>(null) }
    // 相对时间每分钟刷新
    var timeTick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000L)
            timeTick++
        }
    }
    var newGroupName by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var groupFilter by remember { mutableStateOf<String?>(null) }
    var showArchived by remember { mutableStateOf(false) }
    // 分组被删除后重置失效筛选
    LaunchedEffect(groups) {
        if (groupFilter != null && groups.none { it.id == groupFilter }) {
            groupFilter = null
        }
    }
    var contentMatchIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            contentMatchIds = emptySet()
            return@LaunchedEffect
        }
        // 防抖：停顿 300ms，快速输入时旧的 LaunchedEffect 会被取消，避免频繁请求
        delay(300L)
        val queryAtRequest = searchQuery
        // 用 query 快照比对过滤过期回调（竞态保护）
        onSearchByContent(queryAtRequest) { ids ->
            if (searchQuery == queryAtRequest) {
                contentMatchIds = ids.toSet()
            }
        }
    }
    val filteredConversations = remember(conversations, searchQuery, contentMatchIds, groupFilter, showArchived) {
        sortConversations(
            filterConversations(
                conversations = conversations,
                query = searchQuery,
                contentMatchIds = contentMatchIds,
                groupId = groupFilter,
                showArchived = showArchived,
            )
        )
    }
    val archivedCount = remember(conversations) { conversations.count { it.archived } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("会话") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) { Text("新建会话") }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (showArchived) "已显示归档会话${if (archivedCount > 0) "（${archivedCount} 个）" else ""}"
                        else "显示已归档会话${if (archivedCount > 0) "（${archivedCount} 个）" else ""}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(
                        checked = showArchived,
                        onCheckedChange = { showArchived = it },
                        modifier = Modifier.semantics {
                            contentDescription = if (showArchived) "隐藏已归档会话" else "显示已归档会话"
                        },
                    )
                }
                if (groups.isNotEmpty()) {
                    Text("当前会话分组", style = MaterialTheme.typography.labelLarge)
                    if (groupFilter != null) {
                        Text(
                            text = "已筛选该分组（长按分组名筛选，点击取消）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Text(
                            text = "点击分配，长按筛选",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { onAssignGroup(null) }) { Text("未分组") }
                        groups.forEach { group ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = { onAssignGroup(group.id) },
                                    modifier = Modifier.combinedClickable(
                                        onClick = { onAssignGroup(group.id) },
                                        onLongClick = {
                                            groupFilter = if (groupFilter == group.id) null else group.id
                                        },
                                    ),
                                ) { Text(group.name) }
                                IconButton(
                                    onClick = { renameGroupTarget = group },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.Edit,
                                        contentDescription = "重命名分组 ${group.name}",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.outline,
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteGroup(group.id) },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "删除分组 ${group.name}",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("新建项目/主题分组") },
                    singleLine = true,
                    trailingIcon = {
                        TextButton(onClick = { onCreateGroup(newGroupName); newGroupName = "" }) {
                            Text("添加")
                        }
                    },
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("搜索会话") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                )
                if (showArchived) {
                    Text(
                        text = "当前显示 ${filteredConversations.size} 个会话（含已归档）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (filteredConversations.isEmpty()) {
                    Text(
                        text = when {
                            searchQuery.isNotBlank() -> "没有匹配的会话"
                            archivedCount > 0 && !showArchived -> "暂无未归档会话，可打开上方开关查看 ${archivedCount} 个已归档会话"
                            else -> "暂无会话"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp),
                    )
                }
                LazyColumn(modifier = Modifier.height(280.dp)) {
                    items(filteredConversations, key = { it.id }) { conversation ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                onClick = { onSelect(conversation.id) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = buildString {
                                            if (conversation.id == selectedId) append("✓ ")
                                            if (conversation.pinned) append("📌 ")
                                            if (conversation.archived) append("[已归档] ")
                                            append(conversation.title)
                                        },
                                        maxLines = 1,
                                    )
                                    Text(
                                        text = remember(timeTick) { formatRelativeTime(conversation.updatedAt) },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            TextButton(onClick = { pendingDelete = conversation }) { Text("删除") }
                            TextButton(onClick = { renameTarget = conversation }) { Text("重命名") }
                            TextButton(onClick = { onTogglePin(conversation.id) }) {
                                Text(if (conversation.pinned) "取消置顶" else "置顶")
                            }
                            TextButton(onClick = {
                                if (conversation.archived) onToggleArchive(conversation.id)
                                else pendingArchive = conversation
                            }) {
                                Text(if (conversation.archived) "取消归档" else "归档")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )

    // 重命名会话对话框
    renameTarget?.let { conversation ->
        var newTitle by remember(conversation.id) { mutableStateOf(conversation.title) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名会话") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRename(conversation.id, newTitle)
                        renameTarget = null
                    },
                    enabled = newTitle.isNotBlank(),
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            },
        )
    }

    // 重命名分组对话框
    renameGroupTarget?.let { group ->
        var newGroupName by remember(group.id) { mutableStateOf(group.name) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { renameGroupTarget = null },
            title = { Text("重命名分组") },
            text = {
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameGroup(group.id, newGroupName)
                        renameGroupTarget = null
                    },
                    enabled = newGroupName.isNotBlank(),
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { renameGroupTarget = null }) { Text("取消") }
            },
        )
    }

    // 删除会话确认对话框
    pendingDelete?.let { conversation ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除会话？") },
            text = { Text("「${conversation.title}」将被删除，此操作不可恢复。") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(conversation.id)
                        pendingDelete = null
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }

    // 归档会话确认对话框
    pendingArchive?.let { conversation ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingArchive = null },
            title = { Text("归档会话？") },
            text = { Text("「${conversation.title}」将从默认会话列表隐藏，可在“显示已归档会话”中恢复。") },
            confirmButton = {
                Button(
                    onClick = {
                        onToggleArchive(conversation.id)
                        pendingArchive = null
                    },
                ) { Text("归档") }
            },
            dismissButton = {
                TextButton(onClick = { pendingArchive = null }) { Text("取消") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoryScreen(
    memories: List<Memory>,
    onDelete: (String) -> Unit,
    onUpdate: (id: String, content: String, category: String) -> Unit,
    onAdd: (content: String, category: String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
    appContext: android.content.Context? = null,
) {
    var editingMemory by remember { mutableStateOf<Memory?>(null) }
    var addingMemory by remember { mutableStateOf(false) }
    var pendingDeleteMemory by remember { mutableStateOf<Memory?>(null) }
    var categoryFilter by remember { mutableStateOf<String?>(null) }
    var confirmClearMemories by remember { mutableStateOf(false) }
    val categories = remember(memories) { memories.map { it.category }.distinct().sorted() }
    val shownMemories = remember(memories, categoryFilter) {
        if (categoryFilter == null) memories
        else memories.filter { it.category == categoryFilter }
    }
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("长期记忆", style = MaterialTheme.typography.headlineSmall)
                Text("Agent 会从明确表达中自动记住信息", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { addingMemory = true }) { Text("新增") }
            if (memories.isNotEmpty()) {
                TextButton(onClick = {
                    val text = buildString {
                        appendLine(formatMemories(shownMemories))
                        if (shownMemories.size < memories.size) {
                            append("\n（已按筛选导出 ${shownMemories.size}/${memories.size} 条）")
                        }
                    }
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    appContext?.let {
                        runCatching { it.startActivity(Intent.createChooser(intent, "导出长期记忆")) }
                    }
                }) { Text("导出") }
            }
            TextButton(
                onClick = { confirmClearMemories = true },
                enabled = memories.isNotEmpty(),
            ) { Text("清空") }
        }
        Spacer(Modifier.height(12.dp))
        if (memories.isEmpty()) {
            PlaceholderScreen("还没有长期记忆", Modifier.fillMaxWidth().weight(1f))
        } else {
            // 分类筛选
            if (categories.size > 1) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = categoryFilter == null,
                            onClick = { categoryFilter = null },
                            label = { Text("全部") },
                        )
                    }
                    items(categories) { category ->
                        FilterChip(
                            selected = categoryFilter == category,
                            onClick = { categoryFilter = category },
                            label = { Text(category) },
                        )
                    }
                }
            }
            if (shownMemories.isEmpty()) {
                PlaceholderScreen("该分类下暂无记忆", Modifier.fillMaxWidth().weight(1f))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shownMemories, key = { it.id }) { memory ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        memory.content,
                                        modifier = Modifier.weight(1f, fill = false),
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (memory.isSensitive) {
                                        Text("⚠️", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                Text(
                                    memory.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            TextButton(onClick = { pendingDeleteMemory = memory }) { Text("删除") }
                            TextButton(onClick = { editingMemory = memory }) { Text("编辑") }
                        }
                    }
                }
            }
        }
    }
}

    // 编辑记忆对话框
    editingMemory?.let { memory ->
        var editContent by remember(memory.id) { mutableStateOf(memory.content) }
        var editCategory by remember(memory.id) { mutableStateOf(memory.category) }
        var categoryMenuOpen by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { editingMemory = null },
            title = { Text("编辑记忆") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editContent,
                        onValueChange = { editContent = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("内容") },
                        minLines = 2,
                    )
                    Spacer(Modifier.height(8.dp))
                    // 分类：下拉建议已有分类，也可手输
                    androidx.compose.material3.ExposedDropdownMenuBox(
                        expanded = categoryMenuOpen,
                        onExpandedChange = { categoryMenuOpen = it },
                    ) {
                        OutlinedTextField(
                            value = editCategory,
                            onValueChange = { editCategory = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            label = { Text("分类") },
                            singleLine = true,
                            trailingIcon = {
                                androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(
                                    expanded = categoryMenuOpen,
                                )
                            },
                        )
                        androidx.compose.material3.DropdownMenu(
                            expanded = categoryMenuOpen,
                            onDismissRequest = { categoryMenuOpen = false },
                        ) {
                            categories.forEach { category ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(category) },
                                    onClick = {
                                        editCategory = category
                                        categoryMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdate(memory.id, editContent, editCategory)
                        editingMemory = null
                    },
                    enabled = editContent.isNotBlank(),
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editingMemory = null }) { Text("取消") }
            },
        )
    }

    // 新增记忆对话框
    if (addingMemory) {
        var newContent by remember { mutableStateOf("") }
        var newCategory by remember { mutableStateOf("通用") }
        AlertDialog(
            onDismissRequest = { addingMemory = false },
            title = { Text("新增记忆") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newContent,
                        onValueChange = { newContent = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("内容") },
                        minLines = 2,
                    )
                    Spacer(Modifier.height(8.dp))
                    var addCategoryMenuOpen by remember { mutableStateOf(false) }
                    androidx.compose.material3.ExposedDropdownMenuBox(
                        expanded = addCategoryMenuOpen,
                        onExpandedChange = { addCategoryMenuOpen = it },
                    ) {
                        OutlinedTextField(
                            value = newCategory,
                            onValueChange = { newCategory = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            label = { Text("分类") },
                            singleLine = true,
                            trailingIcon = {
                                androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(
                                    expanded = addCategoryMenuOpen,
                                )
                            },
                        )
                        androidx.compose.material3.DropdownMenu(
                            expanded = addCategoryMenuOpen,
                            onDismissRequest = { addCategoryMenuOpen = false },
                        ) {
                            categories.forEach { category ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(category) },
                                    onClick = {
                                        newCategory = category
                                        addCategoryMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAdd(newContent, newCategory)
                        addingMemory = false
                    },
                    enabled = newContent.isNotBlank(),
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { addingMemory = false }) { Text("取消") }
            },
        )
    }

    // 删除单条记忆确认
    pendingDeleteMemory?.let { memory ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDeleteMemory = null },
            title = { Text("删除这条记忆？") },
            text = { Text("记忆将被删除，此操作不可恢复。") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(memory.id)
                        pendingDeleteMemory = null
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteMemory = null }) { Text("取消") }
            },
        )
    }

    // 清空记忆确认
    if (confirmClearMemories) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmClearMemories = false },
            title = { Text("清空全部记忆？") },
            text = { Text("所有长期记忆将被删除，此操作不可恢复。") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAll()
                        confirmClearMemories = false
                    },
                ) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearMemories = false }) { Text("取消") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionLogScreen(
    logs: List<ActionLogEntity>,
    onClearAll: () -> Unit,
    onClearPackage: (String) -> Unit,
    modifier: Modifier = Modifier,
    appContext: android.content.Context? = null,
) {
    var onlyFailures by remember { mutableStateOf(false) }
    var packageFilter by remember { mutableStateOf<String?>(null) }
    var confirmClearLogs by remember { mutableStateOf(false) }
    var pendingClearPackage by remember { mutableStateOf<String?>(null) }
    val packages = remember(logs) { logs.map { it.packageName }.distinct().sorted() }
    val shownLogs = remember(logs, onlyFailures, packageFilter) {
        logs.filter {
            (!onlyFailures || it.status == "failed") &&
                (packageFilter == null || it.packageName == packageFilter)
        }
    }
    val context = LocalContext.current
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("操作日志", style = MaterialTheme.typography.headlineSmall)
                Text("记录‘替我行动’的操作历史", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (logs.isNotEmpty()) {
                TextButton(onClick = {
                    // 导出当前筛选结果（而非全部日志），所见即所得
                    val text = buildString {
                        if (shownLogs.isEmpty()) {
                            appendLine("筛选条件下无匹配的操作日志")
                        } else {
                            appendLine(formatActionLogs(shownLogs))
                        }
                        if (shownLogs.size < logs.size) {
                            append("\n（已按筛选导出 ${shownLogs.size}/${logs.size} 条）")
                        }
                    }
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    runCatching {
                        context.startActivity(Intent.createChooser(intent, "导出操作日志"))
                    }
                }) { Text("导出") }
            }
            TextButton(
                onClick = { confirmClearLogs = true },
                enabled = logs.isNotEmpty(),
            ) { Text("清空") }
        }
        Spacer(Modifier.height(12.dp))

        // 失败筛选开关
        if (logs.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.FilterChip(
                    selected = onlyFailures,
                    onClick = { onlyFailures = !onlyFailures },
                    label = { Text("仅看失败") },
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (onlyFailures) {
                        "${shownLogs.size} 条失败记录"
                    } else {
                        val failedCount = logs.count { it.status == "failed" }
                        if (failedCount > 0) "共 ${logs.size} 条，失败 $failedCount 条"
                        else "共 ${logs.size} 条"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (onlyFailures || packageFilter != null) {
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = {
                        onlyFailures = false
                        packageFilter = null
                    }) { Text("重置筛选") }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // 应用筛选
        if (packages.size > 1) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = packageFilter == null,
                        onClick = { packageFilter = null },
                        label = { Text("全部应用") },
                    )
                }
                items(packages) { packageName ->
                    FilterChip(
                        selected = packageFilter == packageName,
                        onClick = { packageFilter = packageName },
                        label = { Text(packageName) },
                    )
                }
                if (packageFilter != null) {
                    item {
                        TextButton(onClick = { pendingClearPackage = packageFilter }) {
                            Text("清空该应用", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        if (logs.isEmpty()) {
            PlaceholderScreen("暂无操作日志", Modifier.fillMaxWidth().weight(1f))
        } else if (shownLogs.isEmpty()) {
            PlaceholderScreen("筛选后无匹配记录", Modifier.fillMaxWidth().weight(1f))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(shownLogs, key = { it.id }) { log ->
                    ActionLogItem(log)
                }
            }
        }
    }

    // 清空日志确认
    if (confirmClearLogs) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmClearLogs = false },
            title = { Text("清空全部日志？") },
            text = { Text("所有操作日志将被删除，此操作不可恢复。") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAll()
                        confirmClearLogs = false
                    },
                ) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearLogs = false }) { Text("取消") }
            },
        )
    }

    // 清空该应用日志确认
    pendingClearPackage?.let { packageName ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingClearPackage = null },
            title = { Text("清空该应用日志？") },
            text = { Text("将删除「$packageName」的全部操作日志，此操作不可恢复。") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearPackage(packageName)
                        pendingClearPackage = null
                        packageFilter = null
                    },
                ) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { pendingClearPackage = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ActionLogItem(log: ActionLogEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = log.packageName.takeLastWhile { it != '.' }, // 只显示应用名
                    style = MaterialTheme.typography.titleMedium,
                    color = when (log.status) {
                        "success" -> MaterialTheme.colorScheme.secondary
                        "failed" -> MaterialTheme.colorScheme.error
                        "running" -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    text = formatLogTime(log.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier.padding(horizontal = 6.dp).clip(RoundedCornerShape(4.dp))
                        .background(when (log.actionType) {
                            "click" -> MaterialTheme.colorScheme.primaryContainer
                            "swipe" -> MaterialTheme.colorScheme.secondaryContainer
                            "input_text" -> MaterialTheme.colorScheme.tertiaryContainer
                            "input_key" -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }),
                ) {
                    Text(
                        text = when (log.actionType) {
                            "click" -> "点击"
                            "swipe" -> "滑动"
                            "input_text" -> "输入文本"
                            "input_key" -> "按键"
                            else -> "操作"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when (log.actionType) {
                            "click" -> MaterialTheme.colorScheme.onPrimaryContainer
                            "swipe" -> MaterialTheme.colorScheme.onSecondaryContainer
                            "input_text" -> MaterialTheme.colorScheme.onTertiaryContainer
                            "input_key" -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }

                Box(
                    modifier = Modifier.padding(horizontal = 6.dp).clip(RoundedCornerShape(4.dp))
                        .background(when (log.status) {
                            "success" -> MaterialTheme.colorScheme.secondaryContainer
                            "failed" -> MaterialTheme.colorScheme.errorContainer
                            "running" -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }),
                ) {
                    Text(
                        text = when (log.status) {
                            "success" -> "✓ 成功"
                            "failed" -> "✗ 失败"
                            "running" -> "⏳ 执行中"
                            else -> "未知"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when (log.status) {
                            "success" -> MaterialTheme.colorScheme.onSecondaryContainer
                            "failed" -> MaterialTheme.colorScheme.onErrorContainer
                            "running" -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            if (log.targetElement != null && log.targetElement.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "目标：${log.targetElement}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (log.details.isNotBlank() && log.details != log.targetElement) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "详情：${log.details}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (log.errorMessage != null && log.errorMessage!!.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "错误：${log.errorMessage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 将时间戳格式化为相对时间（如"3 分钟前"），纯函数可测 */
fun formatRelativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000} 分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000} 小时前"
        diff < 7 * 86_400_000L -> "${diff / 86_400_000} 天前"
        else -> "更早"
    }
}
