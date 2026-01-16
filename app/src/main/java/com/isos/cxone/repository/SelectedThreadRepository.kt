package com.isos.cxone.repository

import com.nice.cxonechat.thread.ChatThread
import com.nice.cxonechat.ChatThreadHandler

object SelectedThreadRepository {

    private var selectedThread: ChatThread? = null
    private var selectedHandler: ChatThreadHandler? = null

    fun set(thread: ChatThread, handler: ChatThreadHandler) {
        selectedThread = thread
        selectedHandler = handler
    }

    fun getHandler(): ChatThreadHandler? = selectedHandler

    fun getThread(): ChatThread? = selectedThread

    fun clear() {
        selectedThread = null
        selectedHandler = null
    }
}