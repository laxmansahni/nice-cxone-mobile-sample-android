package com.isos.cxone.compose

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.isos.cxone.viewmodel.ChatConversationViewModel
import com.isos.cxone.models.MessageDisplayItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleThreadScreen(
    viewModel: ChatConversationViewModel = viewModel(),
    threadId: String,
    navigateUp: () -> Unit,
) {
    // 1. Load the thread when the composable first enters the composition
    LaunchedEffect(threadId) {
        viewModel.loadThread(threadId)
    }

    // 2. Observe the state flows
    val thread by viewModel.thread.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val isAgentTyping by viewModel.isAgentTyping.collectAsState()
    val canLoadMore by viewModel.canLoadMore.collectAsState()

    val listState = rememberLazyListState()

    val title = thread?.displayName ?: if (isLoading) "Loading..." else "Conversation"

    // 3. Auto-scroll to the latest message whenever a new message or typing indicator appears
    LaunchedEffect(messages.size, isAgentTyping) {
        if (messages.isNotEmpty() || isAgentTyping) {
            // Index 0 is the newest message/typing indicator in a reversed LazyColumn
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = navigateUp) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back to list")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            when {
                isLoading && thread == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                thread != null -> {
                    // Full Chat UI
                    ChatHistory(
                        messages = messages,
                        isAgentTyping = isAgentTyping,
                        canLoadMore = canLoadMore,
                        listState = listState,
                        loadMore = viewModel::loadMore
                    )

                    MessageInput(
                        onSend = viewModel::sendMessage,
                        onTypingStart = viewModel::reportTypingStarted,
                        onTypingEnd = viewModel::reportTypingEnd
                    )
                }
                else -> {
                    // Error/Not found state
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Could not load conversation for ID: $threadId",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.ChatHistory(
    messages: List<MessageDisplayItem>,
    isAgentTyping: Boolean,
    canLoadMore: Boolean,
    listState: LazyListState,
    loadMore: () -> Unit,
) {
    LazyColumn(
        reverseLayout = true,
        state = listState,
        verticalArrangement = Arrangement.Top,
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
    ) {
        // Typing indicator is always at index 0 (bottom) when visible
        if (isAgentTyping) {
            item(key = "typing_indicator") {
                AgentTypingIndicator(Modifier.padding(bottom = 8.dp))
            }
        }

        // Load more indicator is at the top of the reversed list
        if (canLoadMore) {
            item(key = "load_more") {
                LoadMoreIndicator { loadMore() }
            }
        }

        items(
            items = messages,
            key = { it.id.toString() }
        ) { message ->
            MessageItem(message)
        }
    }
}

@Composable
private fun MessageItem(message: MessageDisplayItem) {
    val isUser = message.isUser
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = alignment
    ) {
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = color),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = message.text,
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message.status,
                        color = textColor.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun AgentTypingIndicator(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(0.6f),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .width(16.dp)
                .height(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.width(8.dp))
        Text("Agent is typing...", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun LoadMoreIndicator(loadMore: () -> Unit) {
    // Automatically trigger loadMore when this item appears (i.e., when scrolling to the top)
    LaunchedEffect(Unit) {
        loadMore()
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(Modifier.width(24.dp))
    }
}

@Composable
private fun MessageInput(
    onSend: (String) -> Unit,
    onTypingStart: () -> Unit,
    onTypingEnd: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    var typingJob by remember { mutableStateOf<Job?>(null) }

    // Constants for typing debounce
    val TYPING_DEBOUNCE_MS = 1500L

    val onTextChanged = { newText: String ->
        val wasBlank = text.isBlank()
        text = newText

        // 1. Report typing start if moving from blank to non-blank
        if (wasBlank && newText.isNotBlank()) {
            onTypingStart()
        }

        // 2. Debounce reporting typing end
        typingJob?.cancel()
        typingJob = coroutineScope.launch {
            delay(TYPING_DEBOUNCE_MS)
            onTypingEnd()
        }
    }

    val onSendClicked = {
        if (text.isNotBlank()) {
            onSend(text.trim())
            text = ""
            // Immediately report typing end after sending
            typingJob?.cancel()
            onTypingEnd()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .background(MaterialTheme.colorScheme.surface),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChanged,
            label = { Text("Message") },
            modifier = Modifier.weight(1f),
            maxLines = 5
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = onSendClicked,
            enabled = text.isNotBlank(),
            modifier = Modifier.height(56.dp)
        ) {
            Icon(Icons.Default.Send, contentDescription = "Send")
        }
    }
}
