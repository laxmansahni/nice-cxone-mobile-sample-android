package com.isos.cxone.models

import com.isos.cxone.viewmodel.AttachmentDisplayItem
import com.isos.cxone.viewmodel.RichLinkDisplayItem
import java.util.Date
import java.util.UUID

data class MessageDisplayItem(
    val id: UUID,
    val text: String,
    val isUser: Boolean,
    val createdAt: Date,
    val status: String, // e.g., "Sending", "Sent", "Read"
    val attachments: List<AttachmentDisplayItem> = emptyList(),
    val richLink: RichLinkDisplayItem? = null,
    val author: Person? = null
)
