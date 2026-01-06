package com.isos.cxone.models

import java.util.Date
import java.util.UUID
import com.nice.cxonechat.message.Attachment
import com.nice.cxonechat.message.Action

data class MessageDisplayItem(
    val id: UUID,
    val text: String,
    val isUser: Boolean,
    val createdAt: Date,
    val status: String, // e.g., "Sending", "Sent", "Read"
    val attachments: List<Attachment> = emptyList(),
    val richLink: RichLinkDisplayItem? = null,
    val author: Person? = null,
    val type: MessageType = MessageType.TEXT,
    val title: String? = null,
    val actions: List<Action> = emptyList()
)

/**
 * Data class to hold properties of a RichLink message for UI display.
 */
data class RichLinkDisplayItem(
    val title: String,
    val url: String,
    val imageUrl: String? = null
)

enum class MessageType { TEXT, QUICK_REPLY, LIST_PICKER, RICH_LINK }