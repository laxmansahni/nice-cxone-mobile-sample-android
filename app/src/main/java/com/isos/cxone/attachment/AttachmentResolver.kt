package com.isos.cxone.attachment

import android.content.Context
import com.nice.cxonechat.message.Attachment
import com.nice.cxonechat.message.ContentDescriptor

/**
 * Interface responsible for handling the selection and resolution of attachments based on type.
 * This contract sits between the ViewModel and the Android Framework (Context/Activities).
 */
interface AttachmentResolver {
    /**
     * Converts a local Attachment model (holding the file URI/URL) into the SDK's ContentDescriptor.
     * This operation is suspending as it will involve file I/O, validation (size/type),
     * and content resolution using the Android Context.
     *
     * @param attachment The local attachment object to resolve.
     * @param context An Android Context required for content resolution (e.g., ContentResolver).
     * @return The ContentDescriptor for the SDK, or null if resolution failed (e.g., file too large, read error).
     */
    suspend fun resolveToContentDescriptor(attachment: Attachment, context: Context): ContentDescriptor?
}