package com.isos.cxone.models

import com.nice.cxonechat.message.Message.ListPicker
import com.nice.cxonechat.message.Message.QuickReplies
import com.nice.cxonechat.message.Message.RichLink
import com.nice.cxonechat.message.Message.Text
import com.nice.cxonechat.message.Message.Unsupported
import com.nice.cxonechat.thread.ChatThread
import java.util.Date

/** Helper to convert a Date to a simple relative time string (placeholder for real logic). */
private fun Date.toRelativeTime(): String {
    val diff = System.currentTimeMillis() - this.time
    return when {
        diff < 60000 -> "Just now"
        diff < 3600000 -> "${diff / 60000}m ago"
        diff < 86400000 -> "${diff / 3600000}h ago"
        else -> "${diff / 86400000}d ago"
    }
}

data class ThreadDisplayItem( val chatThread: ChatThread,
                              val name: String?,)
{
    // We assume ChatThread has a UUID ID field.
    val id: String = chatThread.id.toString()

    // Messages sorted in descending order (newest first)
    private val messages by lazy {
        chatThread.messages
            .asSequence()
            .sortedByDescending { it.createdAt }
    }

    private val lastMessageObject by lazy {
        messages.firstOrNull()
    }

    /**
     * The last message converted to a displayable text, following the pattern from Thread.kt.
     */
    val lastMessage: String by lazy {
        lastMessageObject
            ?.run {
                when (this) {
                    is Text -> text
                    is RichLink -> fallbackText
                    is QuickReplies -> fallbackText
                    is ListPicker -> fallbackText
                    is Unsupported -> text
                }
            }
            ?.takeIf { it.isNotBlank() } ?: "No message content."
    }

    val lastMessageTime: String by lazy {
        lastMessageObject?.createdAt?.toRelativeTime() ?: ""
    }

    /** True if more messages can be added (i.e., not archived/closed). */
    val isActive: Boolean = chatThread.canAddMoreMessages

    /** Public facing display name */
    val displayName: String = name ?: "Untitled Chat (${id.take(4)}...)"
}

/** Extension function to map the SDK ChatThread to the UI model. */
fun ChatThread.threadOrAgentName(isMultiThreadEnabled: Boolean): String? =
    threadName.takeIf { isMultiThreadEnabled }?.takeIf { it.isNotBlank() } ?: threadAgent?.fullName
