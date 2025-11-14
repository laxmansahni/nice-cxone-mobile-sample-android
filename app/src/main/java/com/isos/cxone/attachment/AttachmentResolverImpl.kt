package com.isos.cxone.attachment

import android.app.ActivityManager
import android.app.ActivityManager.MemoryInfo
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import com.nice.cxonechat.ChatInstanceProvider
import com.nice.cxonechat.message.Attachment
import com.nice.cxonechat.message.ContentDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.core.net.toUri
import kotlin.math.min

/**
 * Concrete implementation of the AttachmentResolver.
 * In a real application, this class would launch Intents to access the device's file picker.
 */
class AttachmentResolverImpl(
) : AttachmentResolver {

    companion object {
        private const val TAG = "AttachmentResolverImpl"
    }

    // Regex for document/media types handled by the document data source (non-image, non-audio)
    private val DOCUMENT_MEDIA_REGEX = Regex("""(video/.*|application/.*|text/.*)""")

    /**
     * Converts a local Attachment model into the SDK's ContentDescriptor.
     * Logic differentiates between Images (read into memory as JPEG bytes) and
     * Documents/Media (passed as Uri for lazy loading).
     */
    override suspend fun resolveToContentDescriptor(
        attachment: Attachment,
        context: Context
    ): ContentDescriptor? {
        val uri: Uri = try {
            attachment.url.toUri()
        } catch (e: Exception) {
            Log.e(TAG, "Invalid URI format in attachment.url: ${attachment.url}", e)
            return null
        }

        val mimeType = attachment.mimeType ?: run {
            Log.e(TAG, "Mime type is missing for attachment: ${attachment.friendlyName}")
            return null
        }

        if (!checkFileSize(uri, context)) {
            val maxSizeBytes = maxSize(context).getOrElse { 0L }
            val maxSizeMb = maxSizeBytes / (1024 * 1024)
            // Lacking the SDK's resource strings, we log the error and return null.
            Log.e(
                TAG,
                "File size validation failed for ${attachment.friendlyName}. File exceeds the dynamic max size (Max MB: $maxSizeMb)."
            )
            return null
        }

        // IMPORTANT: File size validation based on chat configuration is SKIPPED here
        // as the necessary configuration provider is not available. This must be addressed
        // externally or by injecting the max size constraint.

        return runInterruptible(Dispatchers.IO) {
            when {
                // 1. Image Resolution (based on ImageContentDataSource)
                mimeType.startsWith("image/") -> resolveImage(context, uri)

                // 2. Document/Media Resolution (based on DocumentContentDataSource)
                DOCUMENT_MEDIA_REGEX.matches(mimeType) -> resolveDocument(context, uri, mimeType)

                else -> {
                    Log.e(TAG, "Attachment type not supported or recognized: $mimeType")
                    null
                }
            }
        }
    }

    // --- Private File Size Validation Helpers (Mirrored from ContentDataSourceList.kt) ---

    private fun getFileSize(uri: Uri, context: Context) = runCatching {
        requireNotNull(
            context
                .contentResolver
                .openFileDescriptor(uri, "r")
                ?.use { fd ->
                    fd.statSize
                }
        )
    }

    /**
     * The core size check: returns true if the file size is <= maxSize, or if either read failed.
     */
    private fun checkFileSize(uri: Uri, context: Context): Boolean {
        val maxSize = maxSize(context)
        val fileSize = getFileSize(uri, context)
        // Returns true if: read fails, max size retrieval fails, OR file size is less than or equal to max size.
        return fileSize.isFailure || maxSize.isFailure || fileSize.getOrThrow() <= maxSize.getOrThrow()
    }

    /**
     * Determines the effective maximum allowed size (min of device memory and chat configuration limit).
     */
    private fun maxSize(context: Context): Result<Long> {
        val allowedFileSizeResult = allowedFileSize()
        val maxSize = if (allowedFileSizeResult.isFailure) {
            // If config fails, use only the available device memory as the limit.
            availableMemory(context)
        } else {
            // Otherwise, use the smaller of the two limits.
            runCatching {
                min(availableMemory(context).getOrThrow(), allowedFileSizeResult.getOrThrow())
            }
        }
        return maxSize
    }

    /**
     * Retrieves the allowed file size from the Chat SDK configuration, converted to bytes.
     */
    private fun allowedFileSize(): Result<Long> {

        return runCatching {
            requireNotNull(
                ChatInstanceProvider.get().chat
                    ?.configuration
                    ?.fileRestrictions
                    ?.allowedFileSize
                    ?.times(1024L * 1024L) // Convert MB to bytes
            )
        }
    }

    /**
     * Retrieves the current available memory on the device.
     */
    private fun availableMemory(context: Context): Result<Long> {
        return runCatching {
            val activityManager = context.getSystemService(ActivityManager::class.java)
            val memoryInfo = MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            memoryInfo.availMem
        }
    }

    // --- Private Image Resolution Logic (Mirrors ImageContentDataSource) ---

    private fun resolveImage(context: Context, imageUri: Uri): ContentDescriptor? {
        return try {
            val contentBytes = ImageContentHelper.getJpegContent(context, imageUri)
            if (contentBytes == null) {
                Log.e(TAG, "Image content could not be read for $imageUri.")
                return null
            }

            ContentDescriptor(
                // Content is the compressed JPEG byte array
                content = contentBytes,
                mimeType = "image/jpeg",
                fileName = "${UUID.randomUUID()}.jpg",
                friendlyName = imageUri.lastPathSegment // Default friendly name
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve image attachment $imageUri.", e)
            null
        }
    }

    // --- Private Document/Media Resolution Logic (Mirrors DocumentContentDataSource) ---

    private fun resolveDocument(context: Context, uri: Uri, mimeType: String): ContentDescriptor? {
        var friendlyName: String

        // 1. Determine friendly name and file extension
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                friendlyName = if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                } else {
                    uri.lastPathSegment ?: UUID.randomUUID().toString()
                }
            } ?: run {
            friendlyName = uri.lastPathSegment ?: UUID.randomUUID().toString()
        }

        val fileExtension: String = MimeTypeMap
            .getFileExtensionFromUrl(uri.toString())
            .ifEmpty { MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) }
            ?: "dat"

        val fileName = "${UUID.randomUUID()}.$fileExtension"

        // 2. Create ContentDescriptor with Uri and Context for lazy/streamed reading
        return ContentDescriptor(
            // Content is the URI object (for streamable content)
            content = uri,
            context = context,
            mimeType = mimeType,
            fileName = fileName,
            friendlyName = friendlyName
        )
    }

    // --- Helper Object for Image Content (Mirrors logic from ImageContentDataSource) ---

    private object ImageContentHelper {

        /** Reads an image URI, compresses it to JPEG (quality 90), and returns the content bytes. */
        @Throws(FileNotFoundException::class, IOException::class)
        fun getJpegContent(context: Context, imageUri: Uri): ByteArray? =
            context.bitmapForUri(imageUri)?.let { bitmap ->
                ByteArrayOutputStream().use { outputStream ->
                    // Compress to JPEG with 90% quality
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                    outputStream.toByteArray()
                }
            }

        /** Reads data from [uri] and creates a [Bitmap], handling API differences. */
        @Throws(FileNotFoundException::class, IOException::class)
        private fun Context.bitmapForUri(uri: Uri): Bitmap? =
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                @Suppress("Deprecation")
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            } else {
                // Use modern ImageDecoder for Android P and above
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri))
            }
    }
}