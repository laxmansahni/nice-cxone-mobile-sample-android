package com.isos.cxone.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nice.cxonechat.ChatInstanceProvider
import com.nice.cxonechat.thread.ChatThread
import com.nice.cxonechat.ChatThreadHandler
import com.nice.cxonechat.ChatThreadsHandler
import com.nice.cxonechat.Cancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.Dispatchers
import java.util.UUID
import com.isos.cxone.models.ThreadDisplayItem
import com.isos.cxone.models.threadOrAgentName
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
data class AttachmentDisplayItem(val name: String, val url: String)



/**
 * Temporary message object used before the SDK confirms receipt.
 * This class mimics the SDK's internal temporary message structure by defining
 * anonymous implementations of MessageMetadata and MessageAuthor, setting the
 * initial status to Sending.
 */
data class TemporarySentMessage(
    val localId: UUID,
    val text: String,
    val attachments: List<AttachmentDisplayItem> = emptyList(),
    val createdAt: Date = Date(),
    val metadata: MessageMetadata,
    val author: MessageAuthor,
    val direction: MessageDirection = ToAgent
) {
    // Constructor using anonymous objects for MessageMetadata and MessageAuthor
    constructor(id: UUID, text: String) : this(
        localId = id,
        text = text,
        createdAt = Date(),
        attachments = emptyList(),
        metadata = object : MessageMetadata {
            override val seenAt: Date? = null
            override val readAt: Date? = null
            override val seenByCustomerAt: Date? = null
            override val status: MessageStatus = MessageStatus.Sending // Initial status is Sending
        },
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

class ChatConversationViewModel : ViewModel() {

    companion object {
        private const val TAG = "ChatConversationViewModel"
    }

    private val chat = ChatInstanceProvider.get().chat.let(::requireNotNull)

    private val handlerThreads: ChatThreadsHandler? = chat.threads().also {
        Log.d(TAG, "ChatThreadsHandler initialized.")
    }

    val chatMode: ChatMode
        get() = chat.chatMode

    // The handler for the currently active thread
    private var threadHandler: ChatThreadHandler? = null
    private var cancellableThread: Cancellable? = null

    private val _thread = MutableStateFlow<ThreadDisplayItem?>(null)
    val thread: StateFlow<ThreadDisplayItem?> = _thread.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // StateFlow to hold temporary messages that are being sent but not yet confirmed by the SDK
    private val sentMessagesFlow = MutableStateFlow<Map<UUID, TemporarySentMessage>>(emptyMap())
    /** The stream of messages confirmed by the SDK. */
    private val sdkMessagesFlow: Flow<List<MessageDisplayItem>> = _thread
        .map { it?.chatThread?.messages }
        .map { sdkMessages ->
            // Convert SDK messages to SimpleMessage list. We must use a 'when' expression
            // to safely access content based on the message type (fixing the 'text' error).
            sdkMessages?.map { sdkMsg ->
                val messageText = when (sdkMsg) {
                    is Message.Text -> sdkMsg.text // Correct access for Text messages
                    is Message.QuickReplies -> sdkMsg.title // Use title for structured messages
                    is Message.ListPicker -> sdkMsg.title
                    is Message.RichLink -> sdkMsg.title
                }

                MessageDisplayItem(
                    id = sdkMsg.id, // Use the actual SDK message ID
                    text = messageText,
                    isUser = sdkMsg.direction == ToAgent,
                    createdAt = sdkMsg.createdAt,
                    status = if (sdkMsg.direction == ToAgent) sdkMsg.metadata.status.toString() else "Received",
                    // Map SDK Attachments to local SimpleAttachment data class
                    attachments = sdkMsg.attachments.map { sdkAttachment ->
                        AttachmentDisplayItem(sdkAttachment.friendlyName, sdkAttachment.url)
                    }
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
                // Create a SimpleMessage representation for the UI
                val tempUiMsg = MessageDisplayItem(
                    id = tempMsg.localId,
                    text = tempMsg.text,
                    isUser = true,
                    createdAt = tempMsg.createdAt,
                    status = "Sending",
                    attachments = tempMsg.attachments
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
    /**
     * Private mapping function that determines the display name based on ChatMode.
     */
    private fun toDisplayItem(chatThread: ChatThread) = ThreadDisplayItem(
        chatThread = chatThread,
        name = chatThread.threadOrAgentName(chatMode === ChatMode.MultiThread)
    )

    /**
     * Correctly wraps the listener-based ChatThreadsHandler.threads() method into a Kotlin Flow.
     * This Flow is shared using shareIn to ensure the underlying SDK listener is only
     * registered and cancelled once during the ViewModel's lifetime.
     */
    private val threadFlow: Flow<List<ChatThread>>? = handlerThreads?.let { handler ->
        callbackFlow {
            // 1. Set up the listener
            val listener = ChatThreadsHandler.OnThreadsUpdatedListener { threads ->
                trySend(threads)
            }

            // 2. Register the listener and get the cancellable handle
            val cancellable = handler.threads(listener)
            Log.d(TAG, "ChatThreadsHandler listener registered.")

            // 3. awaitClose is called when the last collector stops (or VM is cleared)
            awaitClose {
                cancellable.cancel()
                Log.d(TAG, "ChatThreadsHandler listener cancelled.")
            }
        }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)
    }

    /**
     * Refreshes the list of threads from the SDK server. This is called before
     * consuming the threadFlow to ensure we have the latest data.
     */
    private fun refreshThreads() {
        Log.d(TAG, "Calling refreshThreads on ChatThreadsHandler.")
        try {
            handlerThreads?.refresh()
        } catch (e: Exception) {
            Log.e(TAG, "Error calling refresh() on ChatThreadsHandler", e)
        }
    }

    /**
     * Loads the specific ChatThread associated with the given ID and sets up a listener.
     */
    fun loadThread(threadId: String) {
        if (cancellableThread != null) {
            Log.d(TAG, "Already listening to a thread. Skipping load.")
            return
        }

        val flow = threadFlow ?: run {
            Log.e(TAG, "Thread Flow is null. Cannot load thread.")
            return
        }

        _isLoading.value = true

        viewModelScope.launch(Dispatchers.Default) {
            try {
                // FIX: Parse the String threadId into a UUID object for comparison
                val targetUuid: UUID = try {
                    UUID.fromString(threadId)
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "Invalid thread ID format: $threadId. Must be a valid UUID.", e)
                    _isLoading.value = false
                    return@launch
                }

                // 1. Explicit refresh is crucial based on reference code.
                refreshThreads()

                // 2. Wait for the flow to emit the updated list and find the target thread
                // We suspend until we get a list that contains the thread we are looking for.
                val threadList = flow.first { list ->
                    // Corrected comparison: UUID == UUID
                    list.isNotEmpty() && list.any { it.id == targetUuid }
                }

                // Corrected comparison: UUID == UUID
                val chatThread = threadList.find { it.id == targetUuid }

                if (chatThread == null) {
                    throw IllegalStateException("Thread with ID $threadId not found after refresh.")
                }

                Log.d(TAG, "Found ChatThread object for ID: $threadId")

                // 3. Get the specific ChatThreadHandler for that ChatThread
                threadHandler = handlerThreads!!.thread(chatThread)
                _thread.value = toDisplayItem(chatThread)
                _isLoading.value = false
                // 4. Attach the flow listener
                attachThreadFlow(threadHandler!!)

            } catch (e: Exception) {
                Log.e(TAG, "Error loading thread $threadId", e)
                _thread.value = null // Ensure thread is null on error
                _isLoading.value = false
            }
        }
    }



    private fun attachThreadFlow(handler: ChatThreadHandler) {
        cancellableThread?.cancel()

        // 1. Set the listener that handles SDK updates
        cancellableThread = handler.get { updatedThread ->
            // CRITICAL: Launch the state updates into the ViewModel's scope
            viewModelScope.launch {
                Log.d(TAG, "→ threadFlow() callback → threadId=${updatedThread.id}")

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

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val localId = UUID.randomUUID()
        val tempMsg = TemporarySentMessage(localId, text)

        // 1. Add temporary message to local flow for instant UI update
        sentMessagesFlow.value = sentMessagesFlow.value.plus(localId to tempMsg)

        // 2. Send the message via SDK
        viewModelScope.launch {
            try {
                // The SDK's send method is asynchronous and non-blocking.
                threadHandler?.messages()?.send(text)
                Log.d(TAG, "Sent message via SDK: $text")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message", e)
            }
        }
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

    override fun onCleared() {
        Log.w(TAG, "ViewModel cleared → cancelling listeners.")
        cancellableThread?.cancel()
        super.onCleared()
    }
}
