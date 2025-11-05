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

class ChatConversationViewModel : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
    }

    private val chat = ChatInstanceProvider.get().chat.also {
        Log.d(TAG, "Chat instance obtained: $it")
    }

    private val handlerThreads: ChatThreadsHandler? = chat?.threads()?.also {
        Log.d(TAG, "ChatThreadsHandler initialized.")
    }

    private var chatThreadHandler: ChatThreadHandler? = null
    private var cancellableThreads: Cancellable? = null
    private var cancellableThread: Cancellable? = null
    private var isCreatingThread = false
    private var hasThreadAttached = false

    private val _thread = MutableStateFlow<ChatThread?>(null)
    val thread: StateFlow<ChatThread?> = _thread

    init {
        Log.d(TAG, "ViewModel initialized → setting up thread listener.")
        viewModelScope.launch { observeThreads() }
    }

    private suspend fun observeThreads() {
        val handler = handlerThreads ?: run {
            Log.e(TAG, "ChatThreadsHandler is null → Chat not ready.")
            return
        }

        Log.v(TAG, "Attaching persistent threads() listener...")

        cancellableThreads = handler.threads { threadsList ->
            Log.d(TAG, "→ threads() callback fired with count=${threadsList.size}")

            if (threadsList.isNotEmpty()) {
                val existingThread = threadsList.first()
                if (!hasThreadAttached) {
                    Log.i(
                        TAG,
                        "Existing or newly created thread found (id=${existingThread.id}) → attaching."
                    )
                    chatThreadHandler = handler.thread(existingThread)
                    chatThreadHandler?.let { attachThreadFlow(it) }
                } else {
                    Log.v(TAG, "Thread already attached → ignoring further updates.")
                }

                // ✅ Thread exists → safe to clear creation flag
                isCreatingThread = false
                return@threads
            }

            // No threads yet — try creating only once
            if (isCreatingThread) {
                Log.v(TAG, "Thread creation already in progress → skipping create().")
                return@threads
            }

            isCreatingThread = true
            Log.i(TAG, "No existing threads → creating one via handler.create()")

            viewModelScope.launch {
                try {
                    val newHandler = handler.create()
                    chatThreadHandler = newHandler
                    Log.v(TAG, "create() returned handler=${chatThreadHandler.hashCode()}")
                    // Since locally created threads don't trigger the listener, we manually set
                    // a value here.
                    val thread = chatThreadHandler!!.get()
                    Log.i(TAG, "New thread created with id=${thread.id}.")
                    _thread.value = thread
                    // For future updates, we attach to the thread flow.
                    attachThreadFlow(handler = chatThreadHandler!!)
                } catch (e: Exception) {
                    Log.e(TAG, "Error during create()", e)
                    isCreatingThread = false // Reset only on error
                }
            }
        }

        Log.v(TAG, "Triggering initial threads refresh()")
        try {
            handler.refresh()
        } catch (e: Exception) {
            Log.e(TAG, "Error calling refresh()", e)
        }
    }


    private fun attachThreadFlow(handler: ChatThreadHandler) {
        if (hasThreadAttached) {
            Log.v(TAG, "Thread already attached → skipping duplicate subscription.")
            return
        }

        hasThreadAttached = true
        Log.d(TAG, "Subscribing to thread handler (hash=${handler.hashCode()}) via get()")

        try {
            handler.refresh()
        } catch (e: Exception) {
            Log.w(TAG, "handler.refresh() threw an exception (ignored).", e)
        }

        cancellableThread = handler.get { thread ->
            Log.d(TAG, "→ threadFlow() callback → threadId=${thread.id}")
            _thread.value = thread
        }
    }

    override fun onCleared() {
        Log.w(TAG, "ViewModel cleared → cancelling listeners.")
        try {
            cancellableThreads?.cancel()
            cancellableThread?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling listeners", e)
        }
        super.onCleared()
    }
}
