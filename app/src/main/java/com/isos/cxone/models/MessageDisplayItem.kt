package com.isos.cxone.models

import java.util.Date
import java.util.UUID
import com.nice.cxonechat.message.Attachment

data class MessageDisplayItem(
    val id: UUID,
    val text: String,
    val isUser: Boolean,
    val createdAt: Date,
    val status: String, // e.g., "Sending", "Sent", "Read"
    val attachments: List<Attachment> = emptyList(),
    val richLink: RichLinkDisplayItem? = null,
    val author: Person? = null
)

/**
 * Data class to hold properties of a RichLink message for UI display.
 */
data class RichLinkDisplayItem(
    val title: String,
    val url: String,
    val imageUrl: String? = null
)