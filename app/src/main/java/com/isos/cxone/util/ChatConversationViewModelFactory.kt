package com.isos.cxone.util

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.isos.cxone.attachment.AttachmentResolver
import com.isos.cxone.viewmodel.ChatConversationViewModel

/**
 * A custom factory to manually inject dependencies into the ViewModel.
 * This is the standard way to handle dependency injection without DI frameworks
 * when using the Compose 'viewModel()' function.
 */
class ChatConversationViewModelFactory(
    private val attachmentResolver: AttachmentResolver
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatConversationViewModel::class.java)) {
            return ChatConversationViewModel(attachmentResolver) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class ${modelClass.name}")
    }
}