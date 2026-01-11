package com.isos.cxone.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nice.cxonechat.thread.ChatThread
import com.nice.cxonechat.ChatInstanceProvider
import com.nice.cxonechat.ChatThreadHandler
import com.nice.cxonechat.ChatThreadsHandler
import com.nice.cxonechat.ChatMode
import com.nice.cxonechat.prechat.PreChatSurveyResponse
import com.nice.cxonechat.state.FieldDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.isos.cxone.models.ThreadDisplayItem
import com.isos.cxone.models.threadOrAgentName
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
class ChatAllConversationsViewModel : ViewModel() {

    companion object {
        private const val TAG = "ChatAllConversationsViewModel"
    }
    // 1. Threads are now exposed as a StateFlow for Compose to observe
    private val _threads = MutableStateFlow<List<ThreadDisplayItem>>(emptyList())
    val threads: StateFlow<List<ThreadDisplayItem>> = _threads.asStateFlow()

    // --- Start: New refresh mechanism for immediate name update ---
    private val _refreshEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val refreshEvent = _refreshEvent.asSharedFlow()
    // --- End: New refresh mechanism --
    private val chat = ChatInstanceProvider.get().chat.let(::requireNotNull)
    private val handlerThreads: ChatThreadsHandler = chat.threads()
    val preChatSurvey
        get() = handlerThreads.preChatSurvey
    val chatMode: ChatMode
        get() = chat.chatMode


    private val _showDialog = MutableStateFlow<Dialog>(Dialog.None)
    val showDialog: StateFlow<Dialog> = _showDialog.asStateFlow()

    sealed class Dialog {
        data object None : Dialog()
        data class EditThreadName(val thread: ThreadDisplayItem) : Dialog()
    }

    /**
     * Shows the dialog to edit the name for the given thread.
     */
    fun editThreadName(thread: ThreadDisplayItem) {
        _showDialog.value = Dialog.EditThreadName(thread)
    }

    /**
     * Dismisses any currently shown dialog.
     */
    fun dismissDialog() {
        _showDialog.value = Dialog.None
    }

    /**
     * Confirms and persists the new thread name via the SDK.
     */
    fun confirmEditThreadName(thread: ThreadDisplayItem, newName: String) {
        dismissDialog() // Dismiss dialog immediately for responsiveness

        viewModelScope.launch {
            try {
                // Get the specific handler for this chat thread and set the new name
                val threadHandler = handlerThreads.thread(thread.chatThread)
                threadHandler.setName(newName)
                // Emit a signal instead of toggling a boolean
                _refreshEvent.emit(Unit)
            } catch (e: Exception) {
                // Log or handle error if name update fails
                Log.e(TAG, "Error updating thread name: ${e.message}")
            }
        }
    }

    // --- End New Dialog/Edit Name State and Handlers ---
    /**
     * Private mapping function that determines the display name based on ChatMode.
     */
    private fun toDisplayItem(chatThread: ChatThread): ThreadDisplayItem {
        // Log the message count for this specific thread
        Log.d(TAG, "toDisplayItem: Thread ID ${chatThread.id} has ${chatThread.messages.size} messages.")

        return ThreadDisplayItem(
            chatThread = chatThread,
            name = chatThread.threadOrAgentName(chatMode === ChatMode.MultiThread)
        )
    }

    /**
     * Correctly wraps the listener-based ChatThreadsHandler.threads() method into a Kotlin Flow.
     * This Flow is shared using shareIn to ensure the underlying SDK listener is only
     * registered and cancelled once during the ViewModel's lifetime.
     */
    private val threadListFlow: Flow<List<ChatThread>> = callbackFlow {
        // 1. Set up the listener
        val listener = ChatThreadsHandler.OnThreadsUpdatedListener { threads ->
            Log.d(TAG, "Threads listener received ${threads.size} threads via Flow.")
            trySend(threads)
        }

        // 2. Register the listener and get the cancellable handle
        val cancellable = handlerThreads.threads(listener)
        Log.d(TAG, "ChatThreadsHandler listener registered for ALL threads.")

        // 3. awaitClose is called when the last collector stops (or VM is cleared)
        awaitClose {
            cancellable.cancel()
            Log.d(TAG, "ChatThreadsHandler listener cancelled.")
        }
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    init {
        // Start collecting the Flow immediately when the ViewModel is created
        viewModelScope.launch {
            threadListFlow.collect { latestThreads ->
                _threads.value = latestThreads
                    .map { chatThread -> toDisplayItem(chatThread) }
            }
        }
        // Initial call to populate the list
        refreshThreads()
    }

    /**
     * Creates a new chat thread asynchronously, handling logic based on whether the chat is configured
     * for single or multiple threads and providing required pre-chat data.
     *
     * @param preChatSurveyResponse Sequence of responses to the PreChatSurvey fields.
     * @param customFields Map of custom field key-values specific to this new thread.
     * @return A handler for the newly created chat thread.
     */
    suspend fun createThread(
        preChatSurveyResponse: Sequence<PreChatSurveyResponse<out FieldDefinition, out Any>> = emptySequence(),
        customFields: Map<String, String> = emptyMap()
    ): ChatThreadHandler = withContext(Dispatchers.Default) {
        // Explicitly check the multi-thread status as requested.
        if (chatMode === ChatMode.MultiThread ) {
            val survey = handlerThreads.preChatSurvey
            if (survey != null) {
                // In a real application, you would ensure all required 'preChatSurveyResponse'
                // fields are present before calling 'create()'.
                Log.i(TAG,"Multi-thread mode is active. Pre-Chat Survey '${survey.name}' is defined. Attempting creation.")
            }
        }

        // The SDK's ChatThreadsHandler.create method handles the comprehensive validation
        // (including single-thread limits, required pre-chat fields, and fetch completion).
        // It will throw exceptions if validation fails (e.g., MissingPreChatCustomFieldsException).
        handlerThreads.create(customFields, preChatSurveyResponse)
    }

    fun refreshThreads() {
        Log.d(TAG, "refreshThreads() called: Explicitly requesting SDK refresh.")
        handlerThreads.refresh()
    }
}