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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.isos.cxone.viewmodel.ChatConversationViewModel
import com.isos.cxone.models.MessageDisplayItem
import com.isos.cxone.models.Person
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.emoji2.text.EmojiCompat
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.filled.Link
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri

private val JumboEmojiStyle = TextStyle(
    fontSize = 34.sp,
    lineHeight = 40.8.sp,
    fontWeight = FontWeight.Normal, // W400
    // Using default font family
)

private const val EMOJI_TEXT_MAX_LENGTH = 3

/**
 * Counts the number of emojis in a given message using EmojiCompat.
 *
 *
 * @param message The message to count emojis in.
 * @param limit The maximum number of emojis to count.
 * @return The number of emojis in the message, or -1 if the limit is exceeded or if any character is not an emoji.
 */
private fun EmojiCompat.emojiCount(message: String, limit: Int = Int.MAX_VALUE): Int {
    var emojiCount = 0
    var offset = 0
    while (offset < message.length && emojiCount <= limit) {
        if (getEmojiStart(message, offset) == offset) {
            emojiCount++
            offset = getEmojiEnd(message, offset)
        } else {
            emojiCount = -1
            break
        }
    }
    return if (emojiCount > limit) -1 else emojiCount
}

/**
 * Checks if a message qualifies for the jumbo emoji rendering, matching the criteria:
 * 1. EmojiCompat must be initialized.
 * 2. Must not be blank.
 * 3. Must contain between 1 and EMOJI_TEXT_MAX_LENGTH (3) *emojis* and nothing else.
 */
private fun String.isEmojiMessage(): Boolean {
    val emoji = runCatching { EmojiCompat.get() }.getOrNull()
    val messageText = this

    // Check #1, #2, and #3 (using the logic from ConversationUiState.kt)
    return emoji != null &&
            messageText.isNotBlank() &&
            // Count must be 1, 2, or 3, AND the message must contain ONLY emojis
            emoji.emojiCount(messageText, EMOJI_TEXT_MAX_LENGTH) in 1..EMOJI_TEXT_MAX_LENGTH
}

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
    val author = message.author

    // ASSUMPTION: Rich link check based on nullable properties in MessageDisplayItem
    val isRichLink = !message.richLink?.url.isNullOrBlank()

    // 1. Determine if it's an emoji message using the new SDK-alike logic
    val isEmoji = message.text.isEmojiMessage() && !isRichLink // Rich link takes precedence

    // 2. Determine Styling based on user/agent, emoji, and rich link status
    val messageBackgroundColor = when {
        isEmoji -> Color.Transparent // Jumbo emojis have no bubble
        isRichLink -> MaterialTheme.colorScheme.surface // Use a clean, non-accented background for rich links
        isUser -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    // Text color needs to be visible on both colored bubbles and transparent background
    val textColor = when {
        isRichLink -> MaterialTheme.colorScheme.onSurface
        isUser && !isEmoji -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val cardElevation = if (isEmoji) 0.dp else 1.dp
    val textStyle = if (isEmoji) JumboEmojiStyle else MaterialTheme.typography.bodyMedium
    // Rich link has custom padding inside RichLinkContent, standard text/emoji uses 10.dp
    val contentPadding = if (isRichLink || isEmoji) 0.dp else 10.dp


    // This ensures that if the configuration/locale changes, the formatter is correctly recreated.
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    // Format the creation date
    val timeText = remember(message.createdAt) { timeFormatter.format(message.createdAt) }

    // The main container for the message block, aligned left or right
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {

        // 1. Agent Name (Displayed only for agent messages)
        if (!isUser && author != null) {
            Text(
                text = "${author.firstName} ${author.lastName}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp, start = 8.dp)
            )
        }

        // 2. Avatar + Card Row (limits the width of the message bubble)
        Row(
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Top,
            // Rich links are typically narrower than the full width
            modifier = Modifier.fillMaxWidth(if (isRichLink || isEmoji) 1.0f else 0.85f)
        ) {

            // Agent Avatar (Left side of the bubble)
            if (!isUser && author != null) {
                AuthorAvatar(person = message.author, avatarSize = 32.dp)
                Spacer(Modifier.width(8.dp))
            }

            // Message Card
            Card(
                // Use RoundedCornerShape(0.dp) for emoji messages to ensure no visible background corner remains
                shape = if (isEmoji) RoundedCornerShape(0.dp) else RoundedCornerShape(
                    topStart = 8.dp,
                    topEnd = 8.dp,
                    bottomStart = if (isUser) 8.dp else 2.dp,
                    bottomEnd = if (isUser) 2.dp else 8.dp
                ),
                colors = CardDefaults.cardColors(containerColor = messageBackgroundColor),
                elevation = CardDefaults.cardElevation(defaultElevation = cardElevation)
            ) {
                Column(modifier = Modifier.padding(contentPadding)) {
                    if (isRichLink) {
                        RichLinkContent(
                            title = message.richLink.title, // Use message.text if title is missing
                            url = message.richLink.url,
                            imageUrl = message.richLink.imageUrl,
                        )
                    } else {
                        // Renders the original text/emoji content
                        Text(
                            text = message.text,
                            color = textColor,
                            style = textStyle
                        )
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Display the time
                        Text(
                            text = timeText,
                            color = textColor.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelSmall
                        )

                        Spacer(Modifier.width(4.dp)) // Small spacer between time and status
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

@Composable
fun AuthorAvatar(
    modifier: Modifier = Modifier,
    person: Person,
    avatarSize: Dp = 48.dp
) {
    // Determine the model for the AsyncImage request.
    // We explicitly use the ImageRequest to set a custom placeholder/error behavior if needed,
    // though the placeholder in AsyncImage itself handles the image state.
    val model = ImageRequest.Builder(LocalContext.current)
        .data(person.imageUrl)
        .crossfade(true)
        .build()

    // Container box for the circular avatar
    Box(
        modifier = modifier
            .size(avatarSize)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (!person.imageUrl.isNullOrEmpty()) {
            // 1. Display the image using Coil if the URL is present and not blank.
            AsyncImage(
                model = model,
                contentDescription = "Avatar for ${person.name}",
                modifier = Modifier
                    .matchParentSize() // The image takes the size of the Box
            )
        } else {
            // 2. Display a default icon if the imageUrl is null or empty.
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Placeholder avatar",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(avatarSize * 0.6f)
            )
        }
    }
}

@Composable
private fun RichLinkContent(
    title: String,
    url: String,
    imageUrl: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier.clickable {
            // Open the rich link URL in an external browser
            try {
                val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                context.startActivity(intent)
            } catch (e: Exception) {
                // Log error or show a toast if the URL can't be opened
                Log.e("SingleThreadScreen", "$url can't be opened.", e)
            }
        }
    ) {
        // 1. Image Preview (optional)
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null, // decorative
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp) // Fixed height for rich link preview image
            )
            Spacer(Modifier.height(8.dp))
        }

        // 2. Title
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp)
        )

        Spacer(Modifier.height(4.dp))

        // 3. URL and Icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Text(
                text = url,
                style = MaterialTheme.typography.labelMedium,
                color = linkColor,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Default.Link,
                contentDescription = "External link",
                tint = linkColor,
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(Modifier.height(8.dp)) // Padding at the bottom of the card content
    }
}
