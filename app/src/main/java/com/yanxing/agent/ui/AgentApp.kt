package com.yanxing.agent.ui

import com.yanxing.agent.data.ChatMessage
import com.yanxing.agent.data.Conversation
import com.yanxing.agent.data.ConversationGroup
import com.yanxing.agent.data.Memory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentApp(viewModel: ChatViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var showSessions by remember { mutableStateOf(false) }
    var showMemoryManager by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

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
                modifier = Modifier.padding(padding),
            )
            1 -> MemoryScreen(
                memories = state.memories,
                onDelete = viewModel::deleteMemory,
                onClearAll = viewModel::clearAllMemories,
                modifier = Modifier.padding(padding),
            )
            else -> SettingsScreen(
                state = state,
                onBaseUrlChanged = viewModel::updateBaseUrl,
                onApiKeyChanged = viewModel::updateApiKey,
                onModelChanged = viewModel::updateModel,
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

@Composable
private fun ChatScreen(
    state: ChatUiState,
    onDraftChanged: (String) -> Unit,
    onSend: () -> Unit,
    onToggleStreaming: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size, state.inProgressReply) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    Column(modifier = modifier.fillMaxSize()) {
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = state.draft,
                onValueChange = onDraftChanged,
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入消息…") },
                maxLines = 5,
                enabled = !state.isSending,
            )
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onSend, enabled = state.draft.isNotBlank() && !state.isSending) {
                    if (state.isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.Send, contentDescription = "发送")
                    }
                }
                TextButton(onClick = onToggleStreaming) {
                    Text(if (state.streaming) "流式" else "完整")
                }
            }
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
            Text(
                text = message.content,
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    state: ChatUiState,
    onBaseUrlChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onModelChanged: (String) -> Unit,
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
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("保存配置") }
    }
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
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("模型设置") },
        text = { ModelFields(state, onBaseUrlChanged, onApiKeyChanged, onModelChanged) },
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

private fun tabTitle(index: Int) = when (index) {
    1 -> "记忆"
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
                            TextButton(onClick = { onSelect(conversation.id) }, modifier = Modifier.weight(1f)) {
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
                                Text(memory.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            TextButton(onClick = { onDelete(memory.id) }) { Text("删除") }
                        }
                    }
                }
            }
        }
    }
}
