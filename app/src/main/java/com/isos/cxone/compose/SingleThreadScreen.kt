package com.isos.cxone.compose

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.isos.cxone.viewmodel.ChatConversationViewModel
import com.isos.cxone.models.MessageDisplayItem
import com.isos.cxone.models.MessageType
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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Divider
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Description
import androidx.compose.runtime.saveable.rememberSaveable
import com.nice.cxonechat.message.Attachment
import com.nice.cxonechat.message.Action
import com.nice.cxonechat.message.Action.ReplyButton
import com.isos.cxone.viewmodel.AttachmentType
import androidx.compose.ui.graphics.vector.ImageVector
import com.isos.cxone.attachment.AttachmentResolver
import com.isos.cxone.attachment.AttachmentResolverImpl
import com.isos.cxone.util.ChatConversationViewModelFactory
import androidx.compose.material.icons.filled.Download
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Downloading
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.isos.cxone.util.openWithAndroid
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.TextButton

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

private var attachmentResolver: AttachmentResolver = AttachmentResolverImpl()

private val defaultViewModelFactory = ChatConversationViewModelFactory(attachmentResolver)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleThreadScreen(
    viewModelFactory: ChatConversationViewModelFactory = defaultViewModelFactory,
    threadId: String,
    navigateUp: () -> Unit,
) {
    val viewModel: ChatConversationViewModel = viewModel(factory = viewModelFactory)
    // Load the thread when the composable first enters the composition
    LaunchedEffect(threadId) {
        viewModel.loadThread(threadId)
    }

    // Observe the state flows
    val thread by viewModel.thread.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val isAgentTyping by viewModel.isAgentTyping.collectAsState()
    val canLoadMore by viewModel.canLoadMore.collectAsState()

    // Assuming the ViewModel exposes these properties for attachment handling
    val pendingAttachments by viewModel.pendingAttachments.collectAsState(initial = emptyList())
    val onRemovePendingAttachment = viewModel::onRemovePendingAttachment
    val allowedMimeTypes by viewModel.allowedMimeTypes.collectAsState()

    // Attachment Selection State
    var currentInputSelector by rememberSaveable { mutableStateOf(InputState.None) }
    val dismissInputSelector: () -> Unit = { currentInputSelector = InputState.None }

    val listState = rememberLazyListState()

    val title = thread?.displayName ?: if (isLoading) "Loading..." else "Conversation"

    val context = LocalContext.current

    // Auto-scroll to the latest message whenever a new message or typing indicator appears
    LaunchedEffect(messages.size, isAgentTyping) {
        if (messages.isNotEmpty() || isAgentTyping) {
            // Index 0 is the newest message/typing indicator in a reversed LazyColumn
            // Only scroll if we are close to the bottom to avoid interruption when loading older messages
            if (listState.firstVisibleItemIndex < 5) {
                listState.animateScrollToItem(0)
            }
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
                        loadMore = viewModel::loadMore,
                        isViewModelLoading = isLoading,
                        onActionClick = viewModel::sendReply
                    )
                    // ATTACHMENT PREVIEW BAR
                    AttachmentPreviewBar(
                        attachments = pendingAttachments,
                        onAttachmentClick = {
                            // Implement attachment viewer logic here (e.g., viewModel.onAttachmentClicked(it))
                            Log.d("Attachments", "Attachment clicked: ${it.friendlyName}")
                        },
                        onAttachmentRemoved = onRemovePendingAttachment
                    )

                    MessageInput(
                        onSend = { text -> viewModel.sendMessage(text, context) },
                        onTypingStart = viewModel::reportTypingStarted,
                        onTypingEnd = viewModel::reportTypingEnd,
                        onAttachmentClick = { currentInputSelector = InputState.Attachment },
                        hasPendingAttachments = pendingAttachments.isNotEmpty()
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

    // ATTACHMENT PICKER DIALOG (outside Scaffold/Column)
    if (currentInputSelector == InputState.Attachment) {
        AttachmentPickerDialog(
            context,
            onCloseRequested = dismissInputSelector,
            onAttachmentTypeSelection = { uri, context, type ->
                viewModel.onAttachmentUriReceived(uri, context, type)
                dismissInputSelector()
            },
            allowedMimeTypes
        )
    }
}

@Composable
private fun ColumnScope.ChatHistory(
    messages: List<MessageDisplayItem>,
    isAgentTyping: Boolean,
    canLoadMore: Boolean,
    listState: LazyListState,
    loadMore: () -> Unit,
    isViewModelLoading: Boolean,
    onActionClick: (ReplyButton) -> Unit,
) {
    // Implement scroll-to-top detection for pagination here.
    // This correctly triggers loadMore() when the user scrolls to the top of the history.
    LaunchedEffect(listState, canLoadMore, isViewModelLoading) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val oldestItemIndex = messages.size - 1

            // Trigger load when the last item (oldest message) is visible.
            // In a reversed list, the last index is the "top" of the history.
            layoutInfo.visibleItemsInfo.lastOrNull()?.index == oldestItemIndex && messages.isNotEmpty()
        }
            .collect { isScrolledToTop ->
                if (canLoadMore && isScrolledToTop && !isViewModelLoading) {
                    // Add a small delay to debounce rapid scroll events
                    delay(100)
                    Log.d("ChatHistory", "Scroll detected near top. Triggering loadMore().")
                    loadMore()
                }
            }
    }

    LazyColumn(
        reverseLayout = true,
        state = listState,
        verticalArrangement = Arrangement.Top,
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        // Typing indicator is always at index 0 (bottom) when visible
        if (isAgentTyping) {
            item(key = "typing_indicator") {
                AgentTypingIndicator(Modifier.padding(bottom = 8.dp))
            }
        }

        // Load more indicator is at the top of the reversed list
        // Display the indicator only if a load is actively in progress AND more data is available.
        // The triggering logic is now in the LaunchedEffect above.
        if (isViewModelLoading && canLoadMore) {
            item(key = "load_more") {
                LoadMoreIndicator()
            }
        }

        items(
            items = messages,
            key = { it.id.toString() }
        ) { message ->
            MessageItem(message, onActionClick = onActionClick)
        }
    }
}

@Composable
private fun MessageItem(
    message: MessageDisplayItem,
    onActionClick: (ReplyButton) -> Unit
) {
    val isUser = message.isUser
    val author = message.author

    val attachments = message.attachments
    val hasAttachments = attachments.isNotEmpty()
    val isQuickReply = message.type == MessageType.QUICK_REPLY
    val isListPicker = message.type == MessageType.LIST_PICKER
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

            // Wrap Card and QuickReplies in a Column so chips are below the bubble
            Column {
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
                        when {
                            isRichLink -> {
                                RichLinkContent(
                                    title = message.richLink.title, // Use message.text if title is missing
                                    url = message.richLink.url,
                                    imageUrl = message.richLink.imageUrl,
                                )
                            }

                            isListPicker -> {
                                ListPickerContent(message = message, onActionClick = onActionClick)
                            }

                            else -> {
                                Text(
                                    text = message.text,
                                    color = textColor,
                                    style = textStyle
                                )
                                if (hasAttachments) {
                                    // Add a spacer if there was text above the attachments
                                    if (message.text.isNotBlank()) Spacer(Modifier.height(8.dp))
                                    AttachmentsContent(
                                        attachments = attachments,
                                        isUser = isUser,
                                        // Pass the text color for consistent attachment item coloring
                                        textColor = textColor
                                    )
                                }
                            }
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
                // Quick Replies appear OUTSIDE the bubble as per standard UI patterns
                if (isQuickReply && message.actions.isNotEmpty()) {
                    QuickReplyOptions(actions = message.actions, onActionClick = onActionClick)
                }
            }
        }
    }
}


/** Displays a list of attachments within a message bubble. */
@Composable
private fun AttachmentsContent(
    attachments: List<Attachment>,
    isUser: Boolean,
    textColor: Color
) {
    val containerColor =
        if (isUser) MaterialTheme.colorScheme.inversePrimary else MaterialTheme.colorScheme.surface
    val contentColor =
        if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val attachmentContainerModifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .background(containerColor)
        .border(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            RoundedCornerShape(8.dp)
        )

    Column(attachmentContainerModifier) {
        attachments.forEachIndexed { index, attachment ->
            AttachmentDisplayUiItem(
                attachment = attachment,
                contentColor = contentColor,
                textColor = textColor
            )
            if (index < attachments.lastIndex) {
                Divider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    thickness = 1.dp
                )
            }
        }
    }
}

/** Displays a single attachment item (icon, name, size) for a sent message. */
@Composable
private fun AttachmentDisplayUiItem(
    attachment: Attachment,
    contentColor: Color,
    textColor: Color,
) {
    val context = LocalContext.current
    val isImage = attachment.mimeType?.startsWith("image/") == true
    val isVideo = attachment.mimeType?.startsWith("video/") == true
    val isDocument = attachment.mimeType?.startsWith("application/pdf") == true
    // Define the whole attachment item as clickable
    val itemModifier = Modifier
        .fillMaxWidth()
        .clickable {
            val wasOpenedSuccessfully = context.openWithAndroid(
                url = attachment.url,
                mimeType = attachment.mimeType
            )
            if (!wasOpenedSuccessfully) {
                Toast.makeText(
                    context,
                    "No app found on this device to open this type of file.",
                    Toast.LENGTH_SHORT
                ).show()
                // since the system couldn't find an app to resolve the intent.
                Log.e(
                    "SingleThreadScreen",
                    "No activity found to open attachment: ${attachment.mimeType}"
                )
            }
        }

    // The content is now a Column to stack the optional preview and the file information row
    Column(modifier = itemModifier) {

        // 1. Conditional Image Preview (for image attachments only)
        if (isImage) {
            ImagePreview(
                attachment = attachment,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 100.dp) // Limit height for a clean preview size
                    .padding(top = 8.dp, start = 8.dp, end = 8.dp) // Padding around the image
                    .clip(RoundedCornerShape(4.dp)) // Small clip for the image itself
            )
        } else if (isVideo) {
            VideoPreview(
                attachment = attachment,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 100.dp) // Limit height for a clean preview size
                    .padding(top = 8.dp, start = 8.dp, end = 8.dp) // Padding around the image
                    .clip(RoundedCornerShape(4.dp)) // Small clip for the image itself
            )
        } else if (isDocument) {
            // Use DocumentPreview (specifically PDF)
            DocumentPreview(
                attachment = attachment,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 100.dp) // Limit height
                    .padding(top = 8.dp, start = 8.dp, end = 8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        } // 4. Fallback for all other MIME types (doc, xls, general app/*, etc.)
        else {
            FallbackThumbnail(
                attachment = attachment,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 100.dp) // Limit height
                    .padding(top = 8.dp, start = 8.dp, end = 8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        }
        val attachmentIcon = when {
            // Handle image types
            attachment.mimeType?.startsWith("image/") == true -> Icons.Default.Image
            // Handle video types
            attachment.mimeType?.startsWith("video/") == true -> Icons.Default.Videocam
            // Fallback for documents or unknown types
            else -> Icons.Default.Description
        }

        // 2. File Information Row (Icon, Name, Download button)
        // Add 8.dp padding for the content row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Show file icon
            Icon(
                attachmentIcon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    // Use Attachment.friendlyName
                    text = attachment.friendlyName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            // Optional download icon/button
            Icon(
                Icons.Default.Download,
                contentDescription = "Download ${attachment.friendlyName}",
                tint = textColor,
                modifier = Modifier.size(20.dp)
            )
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
private fun LoadMoreIndicator() {
    // Removed the LaunchedEffect(Unit) which caused the infinite loop
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        // This indicator is now only included in the composition when isViewModelLoading is true.
        CircularProgressIndicator(Modifier.width(24.dp))
    }
}

@Composable
private fun MessageInput(
    onSend: (String) -> Unit,
    onTypingStart: () -> Unit,
    onTypingEnd: () -> Unit,
    onAttachmentClick: () -> Unit, // New callback for attachment button
    hasPendingAttachments: Boolean // New state to enable send button if text is empty
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
        // The ViewModel is responsible for checking if attachments exist if the text is blank.
        // We only check if either text or pending attachments are present.
        if (text.isNotBlank() || hasPendingAttachments) {
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
        // Attachment Button
        IconButton(onClick = onAttachmentClick, modifier = Modifier.height(56.dp)) {
            Icon(Icons.Default.AttachFile, contentDescription = "Attach file")
        }
        Spacer(Modifier.width(8.dp))

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
            enabled = text.isNotBlank() || hasPendingAttachments,
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
                overflow = TextOverflow.Ellipsis
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

/** the composable to show pending attachments. */
@Composable
private fun AttachmentPreviewBar(
    attachments: List<Attachment>,
    onAttachmentClick: (Attachment) -> Unit,
    onAttachmentRemoved: (Attachment) -> Unit,
) {
    if (attachments.isEmpty()) return

    Column {
        Divider(Modifier.padding(horizontal = 8.dp))
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            items(attachments, key = { it.url }) { attachment ->
                AttachmentPreviewItem(
                    attachment = attachment,
                    onAttachmentClick = onAttachmentClick,
                    onAttachmentRemoved = onAttachmentRemoved,
                )
            }
        }
    }
}

@Composable
private fun AttachmentPreviewItem(
    attachment: Attachment,
    onAttachmentClick: (Attachment) -> Unit,
    onAttachmentRemoved: (Attachment) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.TopEnd,
        modifier = modifier.padding(end = 12.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clickable(onClick = remember { { onAttachmentClick(attachment) } })
        ) {
            AttachmentPreview(
                attachment = attachment,
                modifier = Modifier.size(64.dp) // Set the fixed size for the thumbnail
            )

            Text(
                text = attachment.friendlyName,
                style = MaterialTheme.typography.labelSmall,
                overflow = TextOverflow.MiddleEllipsis,
                maxLines = 1,
                modifier = Modifier.width(60.dp)
            )
        }

        // Cancel Icon
        IconButton(
            onClick = remember { { onAttachmentRemoved(attachment) } },
            modifier = Modifier
                .size(20.dp)
                .offset(x = 10.dp, y = (-10).dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.errorContainer)
                .align(Alignment.TopEnd)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove attachment ${attachment.friendlyName}",
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
private fun ImagePreview(
    attachment: Attachment,
    modifier: Modifier
) {
    val placeholderPainter = rememberVectorPainter(image = Icons.Outlined.Downloading)
    val fallbackPainter = rememberVectorPainter(image = Icons.Outlined.Description)
    val errorPainter = rememberVectorPainter(image = Icons.Outlined.ErrorOutline)
    // Build the request with an error listener to capture failure details
    val request = ImageRequest.Builder(LocalContext.current)
        .data(attachment.url)
        .crossfade(true)
        .listener(onError = { _, result ->
            // Log the error details to help debug network or token issues
            Log.e(
                "ImagePreview",
                "Coil failed to load image: ${attachment.url}. Reason: ${result.throwable.message}",
                result.throwable
            )
        })
        .build()
    AsyncImage(
        model = request,
        contentDescription = attachment.friendlyName,
        contentScale = ContentScale.Fit,
        placeholder = placeholderPainter,
        fallback = fallbackPainter,
        error = errorPainter,
        modifier = modifier
    )
}

/**
 * Dispatches the rendering to the correct preview composable based on MIME type,
 * similar to the logic in the SDK's AttachmentPreview.
 */
@Composable
private fun AttachmentPreview(
    attachment: Attachment,
    modifier: Modifier
) {
    val mimeType = attachment.mimeType.orEmpty()

    // Apply common framing/sizing for the attachment thumbnail
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        when {
            // 1. Image Preview: Use AsyncImage to load the content URI
            mimeType.startsWith("image/") -> ImagePreview(attachment, Modifier.fillMaxSize())

            // 2. Video Preview: Use the new VideoPreview logic
            mimeType.startsWith("video/") -> VideoPreview(attachment, Modifier.fillMaxSize())
            // 3. Document Preview (specifically PDF)
            mimeType.startsWith("application/pdf", ignoreCase = true) -> DocumentPreview(
                attachment = attachment,
                modifier = Modifier.fillMaxSize()
            )

            // 4. Fallback for all other MIME types (doc, xls, general app/*, etc.)
            else -> FallbackThumbnail(
                attachment = attachment,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** the dialog for selecting attachment type. */
@Composable
private fun AttachmentPickerDialog(
    context: Context,
    onCloseRequested: () -> Unit,
    onAttachmentTypeSelection: (Uri, Context, AttachmentType) -> Unit,
    allowedMimeTypes: List<String>
) {
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            uri?.let {
                Log.i("SingleThreadScreen", "URI received. $uri")
                // Step 4: URI received. Pass the result back to the ViewModel for processing.
                onAttachmentTypeSelection(it, context, AttachmentType.IMAGE)
                onCloseRequested()

            }
        }
    )

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            uri?.let {
                Log.i("SingleThreadScreen", "URI received. $uri")
                // Step 4: URI received. Pass the result back to the ViewModel for processing.
                onAttachmentTypeSelection(it, context, AttachmentType.VIDEO)
                onCloseRequested()

            }
        }
    )

    // LAUNCHER FOR GENERIC DOCUMENTS/FILES
    val documentPickerLauncher = rememberLauncherForActivityResult(
        // OpenMultipleDocuments allows selecting files based on an array of MIME types
        contract = OpenMultipleDocuments(),
        onResult = { uris: List<Uri> ->
            // Process all selected URIs
            uris.forEach { uri ->
                Log.i("SingleThreadScreen", "Document URI received: $uri")
                // Step 4: URI received. Pass the result back to the ViewModel for processing.
                // Note: The ViewModel handles adding to the pending list.
                onAttachmentTypeSelection(uri, context, AttachmentType.DOCUMENT)
            }
            // Dismiss the dialog once file selection is complete (even if no files were picked)
            onCloseRequested()
        }
    )

    AlertDialog(
        onDismissRequest = onCloseRequested,
        title = { Text("Select Attachment Type") },
        text = {
            Column {
                AttachmentTypeItem(
                    icon = Icons.Default.Image,
                    label = "Photo or Image"
                ) {
                    Log.d("SingleThreadScreen", "Launching image picker.")
                    imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }

                AttachmentTypeItem(
                    icon = Icons.Default.Videocam,
                    label = "Video"
                ) {
                    Log.d("SingleThreadScreen", "Launching video picker.")
                    videoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                }

                AttachmentTypeItem(
                    icon = Icons.Default.Description,
                    label = "Document or File"
                ) {
                    // Filter the MIME types to exclude those already handled or unsupported (image/*, video/*, audio/*)
                    val filteredMimeTypes = allowedMimeTypes.filter { mimeType ->
                        !mimeType.startsWith("image/") &&
                                !mimeType.startsWith("video/") &&
                                !mimeType.startsWith("audio/")
                    }

                    // Convert the filtered list to an array as required by OpenMultipleDocuments
                    val mimeTypeArray = filteredMimeTypes.toTypedArray()

                    Log.d(
                        "SingleThreadScreen",
                        "Launching document picker with MIME types: ${mimeTypeArray.joinToString()}"
                    )

                    documentPickerLauncher.launch(mimeTypeArray)
                }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = onCloseRequested) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun AttachmentTypeItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = label) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun QuickReplyOptions(
    actions: List<Action>,
    onActionClick: (ReplyButton) -> Unit
) {
    Row(
        modifier = Modifier
            .padding(top = 8.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        actions.filterIsInstance<ReplyButton>().forEach { action ->
            SuggestionChip(
                onClick = { onActionClick(action) },
                label = { Text(action.text) },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    labelColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun ListPickerContent(
    message: MessageDisplayItem,
    onActionClick: (ReplyButton) -> Unit
) {
    Column(modifier = Modifier.padding(8.dp)) {
        Text(
            text = message.title ?: "Options",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = message.text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        message.actions.filterIsInstance<ReplyButton>().forEach { action ->
            TextButton(
                onClick = { onActionClick(action) },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.List,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = action.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** the state of the user input area (similar to InputState in UserInput.kt). */
internal enum class InputState { None, Attachment }