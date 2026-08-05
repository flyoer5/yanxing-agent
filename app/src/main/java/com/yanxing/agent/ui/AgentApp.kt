package com.yanxing.agent.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outdated.Refresh
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.yanxing.agent.data.Attachment
import com.yanxing.agent.data.ActionLogEntity
import com.yanxing.agent.data.ChatMessage
import com.yanxing.agent.data.Conversation
import com.yanxing.agent.data.ConversationGroup
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
    var selectedTab by remember { mutableStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var showSessions by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(if (selectedTab == 0) "言行 Agent" else tabTitle(selectedTab)) },
                navigationIcon = {
                    if (selectedTab == 0) {
                        IconButton(onClick = { showSessions = true }) {
                            Icon(Icons.Outlined.Chat, contentDescription = "会话列表")
                        }
                    }
                },
                actions = {
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
                    icon = { Icon(Icons.Outlined.Public, contentDescription = null) },
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
                onDraftChanged = viewModel::updateDraft,
                onSend = viewModel::send,
                onToggleStreaming = viewModel::toggleStreaming,
                onAddAttachment = viewModel::addAttachment,
                onRemoveAttachment = viewModel::removeAttachment,
                onVoiceInput = viewModel::startVoiceInput,
                onToggleSearch = viewModel::toggleSearchEnabled,
                onConfirmAction = viewModel::confirmCurrentAction,
                onStopAction = viewModel::stopAction,
                onUndoAction = onUndoAction,
                modifier = Modifier.padding(padding),
            )
            1 -> MemoryScreen(
                memories = state.memories,
                onDelete = viewModel::deleteMemory,
                onClearAll = viewModel::clearAllMemories,
                modifier = Modifier.padding(padding),
            )
            2 -> ActionLogScreen(
                logs = state.actionLogs,
                onClearAll = viewModel::clearAllActionLogs,
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
                onToggleActionMode = viewModel::toggleActionMode,
                onStartActionMode = viewModel::startActionMode,
                onSave = viewModel::saveSettings,
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
            onToggleActionMode = viewModel::toggleActionMode,
            onStartActionMode = viewModel::startActionMode,
            onSave = { viewModel.saveSettings(); showSettings = false },
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
            onCreateGroup = viewModel::createGroup,
            onAssignGroup = viewModel::assignCurrentConversation,
            onDismiss = { showSessions = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(
    state: ChatUiState,
    onDraftChanged: (String) -> Unit,
    onSend: () -> Unit,
    onToggleStreaming: () -> Unit,
    onAddAttachment: (Attachment) -> Unit,
    onRemoveAttachment: (Int) -> Unit,
    onVoiceInput: () -> Unit,
    onToggleSearch: () -> Unit,
    onConfirmAction: (Boolean) -> Unit,
    onStopAction: () -> Unit,
    onUndoAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    // 图片选择器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val mimeType = context.contentResolver.getType(it) ?: "image/jpeg"
            val name = getFileName(context, it) ?: "image_${System.currentTimeMillis()}.jpg"
            // 读取 base64
            val base64 = readFileAsBase64(context, it)
            onAddAttachment(Attachment("image", it.toString(), mimeType, name, 0, base64))
        }
    }

    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val mimeType = context.contentResolver.getType(it) ?: "application/octet-stream"
            val name = getFileName(context, it) ?: "file_${System.currentTimeMillis()}"
            val base64 = readFileAsBase64(context, it)
            onAddAttachment(Attachment("file", it.toString(), mimeType, name, 0, base64))
        }
    }

    // 录音权限请求
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onVoiceInput()
    }

    LaunchedEffect(state.messages.size, state.inProgressReply) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    val canSend = (state.draft.isNotBlank() || state.pendingAttachments.isNotEmpty()) && !state.isSending

    // 行动模式下的特殊处理
    val isActionMode = state.actionModeEnabled

    Column(modifier = modifier.fillMaxSize()) {
        // 消息列表
        if (state.messages.isEmpty()) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("你好，我是言行 Agent", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text("配置模型后，开始和我对话吧", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.messages, key = { it.id }) { message -> MessageBubble(message) }

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
            IconButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                Icon(Icons.Outlined.Image, contentDescription = "发送图片")
            }
            // 文件按钮
            IconButton(onClick = { filePickerLauncher.launch(arrayOf("*/*")) }) {
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
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入消息…") },
                maxLines = 5,
                enabled = !state.isSending,
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
                IconButton(onClick = onSend, enabled = canSend) {
                    if (state.isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.Send, contentDescription = "发送")
                    }
                }
                TextButton(onClick = onToggleStreaming) {
                    Text(if (state.streaming) "流式" else "完整", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
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

@Composable
private fun MessageBubble(message: ChatMessage, modifier: Modifier = Modifier) {
    val isUser = message.role == "user"
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.86f else 0.94f),
            shape = RoundedCornerShape(16.dp),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
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
            }
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

private fun readFileAsBase64(context: android.content.Context, uri: Uri): String? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val bytes = stream.readBytes()
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        }
    } catch (e: Exception) {
        null
    }
}

// ============ 其他屏幕 ============

@Composable
private fun SettingsScreen(
    state: ChatUiState,
    onBaseUrlChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onModelChanged: (String) -> Unit,
    onSearchApiKeyChanged: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onToggleFloatingWindow: () -> Unit,
    onToggleActionMode: () -> Unit,
    onStartActionMode: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("模型配置", style = MaterialTheme.typography.headlineSmall)
        Text("支持任意 OpenAI 兼容 API。Key 使用 Android Keystore 加密保存。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        ModelFields(state, onBaseUrlChanged, onApiKeyChanged, onModelChanged)
        SearchFields(state, onSearchApiKeyChanged, onToggleSearch)
        SystemFeaturesFields(state, onToggleFloatingWindow)
        ActionModeFields(state, onToggleActionMode, onStartActionMode)
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("保存配置") }
    }
}

@Composable
private fun ActionModeFields(
    state: ChatUiState,
    onToggleActionMode: () -> Unit,
    onStartActionMode: () -> Unit,
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
            onClick = onStartActionMode,
            enabled = state.accessibilityEnabled && state.actionModeEnabled,
        ) {
            Text("开始行动")
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
) {
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
    // 无障碍服务状态
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("无障碍服务", modifier = Modifier.weight(1f))
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
    }
}

@Composable
private fun SearchFields(
    state: ChatUiState,
    onSearchApiKeyChanged: (String) -> Unit,
    onToggleSearch: () -> Unit,
) {
    Text("联网搜索", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = state.searchApiKey,
        onValueChange = onSearchApiKeyChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Tavily API Key") },
        placeholder = { Text("tvly-...") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
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

@Composable
private fun ModelFields(
    state: ChatUiState,
    onBaseUrlChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onModelChanged: (String) -> Unit,
) {
    OutlinedTextField(
        value = state.baseUrl,
        onValueChange = onBaseUrlChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("API 地址") },
        placeholder = { Text("https://api.example.com/v1") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
    )
    OutlinedTextField(
        value = state.apiKey,
        onValueChange = onApiKeyChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("API Key") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
    )
    OutlinedTextField(
        value = state.model,
        onValueChange = onModelChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("模型名称") },
        placeholder = { Text("例如 gpt-4o-mini") },
        singleLine = true,
    )
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
    onToggleActionMode: () -> Unit,
    onStartActionMode: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("模型设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ModelFields(state, onBaseUrlChanged, onApiKeyChanged, onModelChanged)
                SearchFields(state, onSearchApiKeyChanged, onToggleSearch)
                SystemFeaturesFields(state, onToggleFloatingWindow)
                ActionModeFields(state, onToggleActionMode, onStartActionMode)
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
                    enabled = !isSwipe && !isSwipe, // 滑动不需要确认
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

@Composable
private fun SessionsDialog(
    conversations: List<Conversation>,
    groups: List<ConversationGroup>,
    selectedId: String,
    onNew: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onCreateGroup: (String) -> Unit,
    onAssignGroup: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var newGroupName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("会话") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) { Text("新建会话") }
                if (groups.isNotEmpty()) {
                    Text("当前会话分组", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { onAssignGroup(null) }) { Text("未分组") }
                        groups.forEach { group ->
                            TextButton(onClick = { onAssignGroup(group.id) }) { Text(group.name) }
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
                LazyColumn(modifier = Modifier.height(280.dp)) {
                    items(conversations, key = { it.id }) { conversation ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                onClick = { onSelect(conversation.id) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = if (conversation.id == selectedId) "✓ ${conversation.title}" else conversation.title,
                                    maxLines = 1,
                                )
                            }
                            TextButton(onClick = { onDelete(conversation.id) }) { Text("删除") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun MemoryScreen(
    memories: List<Memory>,
    onDelete: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("长期记忆", style = MaterialTheme.typography.headlineSmall)
                Text("Agent 会从明确表达中自动记住信息", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onClearAll, enabled = memories.isNotEmpty()) { Text("清空") }
        }
        Spacer(Modifier.height(12.dp))
        if (memories.isEmpty()) {
            PlaceholderScreen("还没有长期记忆", Modifier.fillMaxWidth().weight(1f))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(memories, key = { it.id }) { memory ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(memory.content)
                                Text(
                                    memory.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            TextButton(onClick = { onDelete(memory.id) }) { Text("删除") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionLogScreen(
    logs: List<ActionLogEntity>,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("操作日志", style = MaterialTheme.typography.headlineSmall)
                Text("记录‘替我行动’的操作历史", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onClearAll, enabled = logs.isNotEmpty()) { Text("清空") }
        }
        Spacer(Modifier.height(12.dp))

        if (logs.isEmpty()) {
            PlaceholderScreen("暂无操作日志", Modifier.fillMaxWidth().weight(1f))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(logs, key = { it.id }) { log ->
                    ActionLogItem(log)
                }
            }
        }
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
                    text = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                        .format(java.util.Date(log.timestamp)),
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
                )
            }

            if (log.errorMessage != null && log.errorMessage!!.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "错误：${log.errorMessage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
