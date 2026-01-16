package com.isos.cxone.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nice.cxonechat.ChatInstanceProvider
import com.nice.cxonechat.thread.ChatThread
import com.nice.cxonechat.ChatThreadHandler
import com.nice.cxonechat.Cancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.Dispatchers
import java.util.UUID
import com.isos.cxone.models.ThreadDisplayItem
import com.isos.cxone.models.threadOrAgentName
import com.isos.cxone.repository.SelectedThreadRepository
import com.isos.cxone.util.ChatErrorCoordinator
import com.nice.cxonechat.ChatMode
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Date
import com.nice.cxonechat.message.Message
import com.nice.cxonechat.message.MessageDirection.ToAgent
import com.nice.cxonechat.ChatThreadEventHandlerActions.typingStart
import com.nice.cxonechat.ChatThreadEventHandlerActions.typingEnd
import com.nice.cxonechat.message.MessageDirection
import com.nice.cxonechat.message.MessageMetadata
import com.nice.cxonechat.message.MessageAuthor
import com.nice.cxonechat.message.MessageStatus
import com.isos.cxone.models.MessageDisplayItem
import com.isos.cxone.models.RichLinkDisplayItem
import com.isos.cxone.models.asPerson
import com.isos.cxone.models.MessageType
import com.nice.cxonechat.message.OutboundMessage
import com.nice.cxonechat.ChatThreadMessageHandler.OnMessageTransferListener
import kotlinx.coroutines.flow.update
import java.lang.ref.WeakReference
import com.nice.cxonechat.message.Attachment
import com.nice.cxonechat.message.ContentDescriptor
import com.nice.cxonechat.message.Action.ReplyButton
import com.nice.cxonechat.message.Action
import com.isos.cxone.attachment.AttachmentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/**
 * Temporary local definition of AttachmentType, assuming it exists in the SDK.
 * This is defined here to ensure the ViewModel is runnable.
 */

enum class AttachmentType { IMAGE, VIDEO, DOCUMENT }

/**
 * Temporary message object used before the SDK confirms receipt.
 * This class mimics the SDK's internal temporary message structure by defining
 * anonymous implementations of MessageMetadata and MessageAuthor, setting the
 * initial status to Sending.
 */
data class TemporarySentMessage(
    val localId: UUID,
    val text: String,
    val attachments: Iterable<Attachment>,
    val createdAt: Date = Date(),
    val status: MessageStatus = MessageStatus.Sending,
    val author: MessageAuthor,
    val direction: MessageDirection = ToAgent
) {
    val metadata: MessageMetadata
        get() = object : MessageMetadata {
            override val seenAt: Date? = null
            override val readAt: Date? = null
            override val seenByCustomerAt: Date? = null
            override val status: MessageStatus = this@TemporarySentMessage.status
        }

    // Constructor using anonymous objects for MessageMetadata and MessageAuthor
    constructor(id: UUID, text: String, attachments: List<Attachment>) : this(
        localId = id,
        text = text,
        createdAt = Date(),
        attachments = attachments,
        author = object : MessageAuthor() {
            override val id: String = SENDER_ID
            override val firstName: String = ""
            override val lastName: String = ""
            override val imageUrl: String? = null
        },
        direction = ToAgent
    )

    private companion object {
        private const val SENDER_ID = "com.cxone.chat.message.sender.1"
    }
}

class ChatConversationViewModel(private val attachmentResolver: AttachmentResolver) : ViewModel() {

    companion object {
        private const val TAG = "ChatConversationViewModel"
    }

    private val chat = ChatInstanceProvider.get().chat.let(::requireNotNull)

    val chatMode: ChatMode
        get() = chat.chatMode

    // The handler for the currently active thread
    private var threadHandler: ChatThreadHandler? = SelectedThreadRepository.getHandler()
    private var cancellableThread: Cancellable? = null

    private val _thread = MutableStateFlow<ThreadDisplayItem?>(null)
    val thread: StateFlow<ThreadDisplayItem?> = _thread.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // --- ATTACHMENT STATE ---

    private val _pendingAttachments = MutableStateFlow<List<Attachment>>(emptyList())

    /** List of attachments the user has selected but not yet sent. */
    val pendingAttachments: StateFlow<List<Attachment>> = _pendingAttachments.asStateFlow()

    /**
     * Retrieves the list of MIME types allowed for documents, sourced from the SDK configuration.
     * Maps the SDK's AllowedFileType objects to a simple List<String> of mime types.
     */
    val allowedMimeTypes: StateFlow<List<String>> = MutableStateFlow(
        chat
            .configuration
            .fileRestrictions
            .allowedFileTypes.map { it.mimeType }).asStateFlow()
    // --- END ATTACHMENT STATE ---

    // StateFlow to hold temporary messages that are being sent but not yet confirmed by the SDK
    private val sentMessagesFlow = MutableStateFlow<Map<UUID, TemporarySentMessage>>(emptyMap())

    /** The stream of messages confirmed by the SDK. */
    private val sdkMessagesFlow: Flow<List<MessageDisplayItem>> = _thread
        .map { it?.chatThread?.messages }
        .map { sdkMessages ->
            sdkMessages?.map { sdkMsg ->
                var messageText: String = ""
                var title: String? = null
                var actions: List<Action> = emptyList()
                var richLinkItem: RichLinkDisplayItem? = null
                var type = MessageType.TEXT

                when (sdkMsg) {
                    is Message.Text -> {
                        messageText = sdkMsg.text
                    }

                    is Message.QuickReplies -> {
                        title = sdkMsg.title
                        messageText = sdkMsg.title
                        actions = sdkMsg.actions.toList()
                        type = MessageType.QUICK_REPLY
                    }

                    is Message.ListPicker -> {
                        title = sdkMsg.title
                        messageText = sdkMsg.text
                        actions = sdkMsg.actions.toList()
                        type = MessageType.LIST_PICKER
                    }

                    is Message.RichLink -> {
                        // Handle RichLink content and populate the dedicated model
                        messageText = sdkMsg.title
                        richLinkItem = RichLinkDisplayItem(
                            title = sdkMsg.title,
                            url = sdkMsg.url,
                            imageUrl = sdkMsg.media.url
                        )
                        type = MessageType.RICH_LINK
                    }

                    is Message.Unsupported -> messageText = "Unsupported message content"
                }

                MessageDisplayItem(
                    id = sdkMsg.id, // Use the actual SDK message ID
                    text = messageText,
                    isUser = sdkMsg.direction == ToAgent,
                    createdAt = sdkMsg.createdAt,
                    status = sdkMsg.metadata.status.toString(),
                    // Map SDK Attachments to local SimpleAttachment data class
                    attachments = sdkMsg.attachments.toList(),
                    richLink = richLinkItem, // Assign the rich link data
                    author = sdkMsg.author?.asPerson

                )
            } ?: emptyList()
        }
        .flowOn(Dispatchers.Default)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), 1)

    /** The final, combined, and sorted list of messages for the UI. */
    val messages: StateFlow<List<MessageDisplayItem>> = sentMessagesFlow
        .combine(sdkMessagesFlow) { sentMap, sdkList ->
            // Merge temporary sent messages with confirmed SDK messages
            val allMessages = sdkList.toMutableList()

            sentMap.values.forEach { tempMsg ->
                // Create a MessageDisplayItem representation for the UI
                val tempUiMsg = MessageDisplayItem(
                    id = tempMsg.localId,
                    text = tempMsg.text,
                    isUser = true,
                    createdAt = tempMsg.createdAt,
                    status = tempMsg.status.toString(),
                    attachments = tempMsg.attachments.toList()
                )
                allMessages.add(tempUiMsg)
            }

            // Sort by creation date descending (newest first for reversed LazyColumn)
            allMessages.sortedByDescending { it.createdAt }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    /** Agent Typing Status */
    val isAgentTyping: StateFlow<Boolean> = _thread
        .map { it?.chatThread?.threadAgent?.isTyping == true }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Can Load More Messages */
    val canLoadMore: StateFlow<Boolean> = _thread
        .map { it?.chatThread?.hasMoreMessagesToLoad ?: false }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        Log.d(TAG, "Initializing with shared handler instance.")

        // 1. Check if we have a valid handler from the repository
        val handler = threadHandler
        if (handler != null) {
            // Immediately map the existing thread data to our UI state
            _thread.value = toDisplayItem(handler.get())

            // 2. Attach the reactive listener to the EXISTING handler
            attachThreadFlow(handler)
        } else {
            Log.e(
                TAG,
                "No handler found in SelectedThreadRepository! Navigation may have occurred incorrectly."
            )
        }
        // Start collecting errors from the global coordinator
        viewModelScope.launch {
            ChatErrorCoordinator.errors.collect { errorType ->
                if (errorType == "SendingMessageFailed") {
                    Log.d(TAG, "Received failure event from Coordinator. Updating UI.")
                    handleMessageFailure()
                }
            }
        }
    }

    fun handleMessageFailure() {
        viewModelScope.launch(Dispatchers.Default) {
            val currentSentMessages = sentMessagesFlow.value
            // Find the most recent "Sending" message to mark as failed
            val lastEntry =
                currentSentMessages.entries.lastOrNull { it.value.status == MessageStatus.Sending }

            if (lastEntry != null) {
                val (uuid, msg) = lastEntry
                Log.w(TAG, "Marking message as FailedToDeliver: $uuid")

                // Create a copy with the new status
                val failedMessage = msg.copy(status = MessageStatus.FailedToDeliver)

                // Update the flow: Replace the old message with the failed one
                sentMessagesFlow.update { it.plus(uuid to failedMessage) }
            }
        }
    }

    /**
     * Private mapping function that determines the display name based on ChatMode.
     */
    private fun toDisplayItem(chatThread: ChatThread) = ThreadDisplayItem(
        chatThread = chatThread,
        name = chatThread.threadOrAgentName(chatMode === ChatMode.MultiThread)
    )

    private fun attachThreadFlow(handler: ChatThreadHandler) {
        cancellableThread?.cancel()

        // 1. Set the listener that handles SDK updates
        cancellableThread = handler.get { updatedThread ->
            // CRITICAL: Launch the state updates into the ViewModel's scope
            viewModelScope.launch {
                Log.d(
                    TAG,
                    "→ threadFlow() callback → threadId=${updatedThread.id} scrollToken=${updatedThread.scrollToken} messagesCount=${updatedThread.messages.count()}"
                )

                // Update the thread data
                _thread.value = toDisplayItem(updatedThread)

                // CRITICAL: If the SDK confirms a message, remove the temporary local message
                // This logic must run after the SDK message list is updated.
                val confirmedTexts = updatedThread.messages
                    .filter { m -> m.direction == ToAgent }
                    .filterIsInstance<Message.Text>() // Only compare Text messages for simple text content
                    .map { m -> m.text }

                // Find local temporary messages whose text content now matches a confirmed message
                val confirmedLocalIds = sentMessagesFlow.value.filter { (_, tempMsg) ->
                    confirmedTexts.contains(tempMsg.text)
                }.keys

                if (confirmedLocalIds.isNotEmpty()) {
                    // Remove confirmed temporary messages from the local map
                    sentMessagesFlow.value = sentMessagesFlow.value.filterKeys { localId ->
                        !confirmedLocalIds.contains(localId)
                    }
                }
            }
        }

        // 2. Trigger the refresh
        try {
            handler.refresh()
        } catch (e: Exception) {
            Log.w(TAG, "handler.refresh() threw an exception (ignored).", e)
        }
    }

    fun loadMore() {
        viewModelScope.launch {
            threadHandler?.messages()?.loadMore()
            Log.d(TAG, "Called loadMore()")
        }
    }

    /**
     * Asynchronously sets the thread name using the underlying ChatThreadHandler.
     *
     * Note: In a production UI, this would typically be followed by an optimistic update
     * to the UI state to instantly reflect the change while waiting for SDK confirmation.
     * Since this is the exposure layer, we only delegate the call.
     */
    fun setThreadName(name: String) {
        viewModelScope.launch {
            Log.d(TAG, "Attempting to set thread name to: $name")
            threadHandler?.setName(name)
            Log.d(TAG, "Thread name is set ${threadHandler?.get()!!.threadName}")
        }
    }

    /**
     * Sends a text message by constructing an OutboundMessage and using the OnMessageSentListener
     * pattern for immediate UI feedback.
     * @param text The message text to send.
     * @param context The Android Context needed to resolve file attachments into ContentDescriptors.
     */
    fun sendMessage(text: String, context: Context) {
        // Only send if there is text OR pending attachments
        if (text.isBlank() && _pendingAttachments.value.isEmpty()) return

        val pendingItems = _pendingAttachments.value

        // 3. Send the message via SDK using the listener
        viewModelScope.launch {
            try {
                // CRITICAL STEP: Convert the UI-tracking Attachment list to the SDK-required ContentDescriptor list.
                // This requires calling the suspending method on the resolver.
                val resolvedAttachments: List<ContentDescriptor> = pendingItems.mapNotNull { item ->
                    // Use the injected resolver to get the ContentDescriptor
                    attachmentResolver.resolveToContentDescriptor(item, context)
                }

                if (pendingItems.isNotEmpty() && resolvedAttachments.size != pendingItems.size) {
                    // Basic error handling for failed resolution (e.g., file too large)
                    Log.e(
                        TAG,
                        "Failed to resolve all attachments. Only resolved ${resolvedAttachments.size} out of ${pendingItems.size}. Sending available attachments."
                    )
                }

                // 2. Construct the SDK's OutboundMessage, including ContentDescriptors
                val outboundMessage = OutboundMessage(
                    message = text,
                    // FIX: Pass the List<ContentDescriptor> to OutboundMessage
                    attachments = resolvedAttachments
                )

                // 3. Create a temporary message for immediate UI update
                val appMessage: (UUID) -> TemporarySentMessage = { id ->
                    // Pass the original Attachment list (for UI) to the temporary message constructor
                    TemporarySentMessage(id, text, attachments = pendingItems)
                }

                // 4. Create the listener to handle UI state updates
                val listener = OnMessageSentListener(
                    message = appMessage,
                    flow = sentMessagesFlow,
                    loaderFlow = _isLoading
                )

                // 5. Send the message
                threadHandler?.messages()?.send(outboundMessage, listener)
                Log.d(
                    TAG,
                    "Sent message via SDK using listener: $text with ${resolvedAttachments.size} ContentDescriptors"
                )
                // Clear pending attachments immediately after successful hand-off to SDK
                _pendingAttachments.value = emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message", e)
                // In case of immediate failure, manually stop the loader
                _isLoading.update { false }
            }
        }
    }

    /**
     * Implements OnMessageTransferListener to manage the state of the temporary message
     * in the UI based on SDK feedback.
     */
    inner class OnMessageSentListener(
        private val message: (UUID) -> TemporarySentMessage,
        flow: MutableStateFlow<Map<UUID, TemporarySentMessage>>,
        loaderFlow: MutableStateFlow<Boolean>,
    ) : OnMessageTransferListener {

        private val weakRef = WeakReference(flow)
        private val weakRefLoader = WeakReference(loaderFlow)

        override fun onProcessed(id: UUID) {
            val map = weakRef.get() ?: return
            val appMessage = message(id)
            // Add the temporary message to the map for UI display.
            // Note: We use the localId from the TemporarySentMessage as the key.
            map.update { it.plus(appMessage.localId to appMessage) }

            // Stop the loader
            weakRefLoader.get()?.let {
                it.update { false }
            }
        }

        override fun onProcessing(message: OutboundMessage) {
            // Notifies that message processing has started
            weakRefLoader.get()?.let {
                it.update { true }
            }
        }
    }

    fun onAttachmentUriReceived(uri: Uri, context: Context, type: AttachmentType) {
        // 1. Create a local Attachment object from the Uri metadata.
        val attachment = createAttachmentFromUri(uri, context, type)
        // 2. Apply duplicate check based on the attachment's URL (which holds the file URI string)
        _pendingAttachments.update { currentList ->
            if (currentList.any { it.url == attachment.url }) {
                Log.w(
                    TAG,
                    "Attachment with URL ${attachment.url} (File: ${attachment.friendlyName}) already exists in pending queue. Skipping addition."
                )
                // Return the current list unchanged if a duplicate is found
                currentList
            } else {
                // Add the new attachment if no duplicate is found
                currentList + attachment
            }
        }
        Log.d(TAG, "Attachment processed: ${attachment.friendlyName}")
    }

    fun onRemovePendingAttachment(attachment: Attachment) {
        _pendingAttachments.value = _pendingAttachments.value.filter { it.url != attachment.url }
    }

    fun reportTypingStarted() {
        viewModelScope.launch {
            threadHandler?.events()?.typingStart()
        }
    }

    fun reportTypingEnd() {
        viewModelScope.launch {
            threadHandler?.events()?.typingEnd()
        }
    }

    /**
     * Converts a content Uri into an SDK Attachment object for local display and pending queue.
     * This function queries the ContentResolver to extract the friendly file name and MIME type.
     *
     * @param uri The content Uri representing the selected file.
     * @param context The Android Context needed to access the ContentResolver.
     * @param type The AttachmentType hint (Image, Video, Document).
     * @return An anonymous implementation of the SDK's Attachment interface.
     */
    private fun createAttachmentFromUri(
        uri: Uri,
        context: Context,
        type: AttachmentType
    ): Attachment {
        val contentResolver = context.contentResolver
        var friendlyName: String = uri.lastPathSegment ?: "Attachment"
        var mimeType: String? = null

        // 1. Attempt to query ContentResolver for official file name and MIME type
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    // Try to get display name
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        friendlyName = cursor.getString(nameIndex) ?: friendlyName
                    }
                }
            }
            // 2. Get MIME type from content resolver
            mimeType = contentResolver.getType(uri)
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving URI metadata for $uri", e)
        }

        // 3. Fallback MIME type based on the selected AttachmentType
        mimeType = mimeType ?: when (type) {
            AttachmentType.IMAGE -> "image/*"
            AttachmentType.VIDEO -> "video/*"
            AttachmentType.DOCUMENT -> "application/octet-stream"
        }

        // 4. Create an anonymous object implementing the SDK's Attachment interface
        val finalFriendlyName = friendlyName
        val finalMimeType = mimeType

        return object : Attachment {
            override val friendlyName: String = finalFriendlyName

            // The URL field of Attachment is used here to store the local file URI string for display/tracking
            override val url: String = uri.toString()
            override val mimeType: String? = finalMimeType
        }
    }

    /**
     * Sends a reply (postback) for a QuickReply or ListPicker selection.
     */
    fun sendReply(action: ReplyButton) {
        viewModelScope.launch {
            try {
                // 1. Convert the ReplyButton Action into an OutboundMessage
                // This uses the operator fun invoke(action: Action.ReplyButton) in OutboundMessage
                val outboundReply = OutboundMessage(action)

                // Add immediate UI feedback for the button click
                val appMessage: (UUID) -> TemporarySentMessage = { id ->
                    TemporarySentMessage(id, action.text, attachments = emptyList())
                }
                val listener = OnMessageSentListener(
                    message = appMessage,
                    flow = sentMessagesFlow,
                    loaderFlow = _isLoading
                )
                // 2. Send the converted message via the handler
                threadHandler?.messages()?.send(outboundReply, listener)

                Log.d(
                    TAG,
                    "Sent reply for action: ${action.text} with postback: ${action.postback}"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send reply", e)
            }
        }
    }

    override fun onCleared() {
        Log.w(TAG, "ViewModel cleared → cancelling listeners.")
        cancellableThread?.cancel()
        cancellableThread = null

        // Explicitly nullify the handler to allow GC to collect the messages
        threadHandler = null

        // Clear the state flows to release the message objects
        _thread.value = null
        sentMessagesFlow.value = emptyMap()
        super.onCleared()
    }
}
