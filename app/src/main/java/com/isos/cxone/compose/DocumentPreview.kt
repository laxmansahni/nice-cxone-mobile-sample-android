package com.isos.cxone.compose

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import android.util.Log
import androidx.core.net.toUri
import androidx.compose.material.icons.filled.Description
import com.nice.cxonechat.message.Attachment
import android.net.Uri
import androidx.compose.material.icons.outlined.ErrorOutline
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import java.io.File
import java.io.FileNotFoundException

/**
 * Acts as the structural wrapper for all document types, currently only routing to PdfThumbnail.
 */
@Composable
fun DocumentPreview(
    attachment: Attachment,
    modifier: Modifier
) {
    // For now, only PDF is supported and routed to PdfThumbnail
    PdfThumbnail(attachment = attachment, modifier = modifier)
}


/**
 * Loads the first page of a PDF document from its URI, handling both remote (download and cache)
 * and local (content resolver) sources..
 * This is necessary because PdfRenderer requires a ParcelFileDescriptor from a local source.
 */
private suspend fun loadPdfThumbnail(context: Context, uri: Uri): ImageBitmap? = withContext(Dispatchers.IO) {

    var parcelFileDescriptor: ParcelFileDescriptor? = null
    var pdfRenderer: PdfRenderer? = null
    var page: PdfRenderer.Page? = null
    var client: HttpClient? = null
    var tempFile: File? = null

    try {
        val scheme = uri.scheme?.lowercase()

        parcelFileDescriptor = when (scheme) {
            "http", "https" -> {
                // --- SCENARIO 1: Remote File (HTTP/HTTPS) ---

                // 1. Initialize client and create temporary file
                client = HttpClient(Android) {
                    engine {
                        connectTimeout = 30_000
                        socketTimeout = 30_000
                    }
                }
                tempFile = File(context.cacheDir, "pdf_temp_${uri.hashCode()}.pdf")

                // 2. Download the PDF using Ktor and save it locally
                val response = client.get(uri.toString())
                val pdfBytes = response.readRawBytes()
                tempFile.writeBytes(pdfBytes)

                // 3. Open ParcelFileDescriptor from the local file
                ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            }
            "file", "content" -> {
                // --- SCENARIO 2: Local File (Content/File Resolver) ---

                // Use the standard Android ContentResolver to get the FileDescriptor
                context.contentResolver.openFileDescriptor(uri, "r")
            }
            else -> {
                // Unknown or unsupported scheme
                Log.e("PdfThumbnail", "Unsupported URI scheme for PDF: $scheme")
                null
            }
        }

        if (parcelFileDescriptor == null) {
            Log.e("PdfThumbnail", "Could not obtain ParcelFileDescriptor for URI: $uri")
            return@withContext null
        }

        // --- STEP 3: Render the PDF from the ParcelFileDescriptor (Common Logic) ---

        // 1. Create PdfRenderer
        pdfRenderer = PdfRenderer(parcelFileDescriptor)
        if (pdfRenderer.pageCount == 0) return@withContext null

        // 2. Open the first page (index 0)
        page = pdfRenderer.openPage(0)

        // 3. Create a white background bitmap
        val width = page.width
        val height = page.height
        val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            val canvas = Canvas(this)
            canvas.drawColor(Color.WHITE)
        }

        // 4. Render the page onto the bitmap
        page.render(
            bitmap,
            null,
            null,
            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
        )

        // 5. Convert Android Bitmap to Compose ImageBitmap
        return@withContext bitmap.asImageBitmap()

    } catch (e: FileNotFoundException) {
        // Specific error for when the content provider fails (useful for debugging permissions/access)
        Log.e("PdfThumbnail", "FileNotFoundException for URI $uri: ${e.message}", e)
        return@withContext null
    } catch (e: Exception) {
        Log.e("PdfThumbnail", "Error rendering PDF thumbnail for $uri", e)
        return@withContext null
    } finally {
        // 6. Clean up resources
        page?.close()
        pdfRenderer?.close()
        parcelFileDescriptor?.close()

        // --- IMPORTANT: Delete the temporary file if one was created ---
        tempFile?.delete()
        client?.close()
    }
}
/**
 * Implements the PDF thumbnail loading and display using the logic inspired by PdfRender.
 */
@Composable
private fun PdfThumbnail(
    attachment: Attachment,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val uri = remember(attachment.url) { attachment.url.toUri() }

    // State to hold the loaded bitmap
    var thumbnailBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // LaunchedEffect to handle the suspending PDF loading
    LaunchedEffect(uri) {
        isLoading = true
        thumbnailBitmap = loadPdfThumbnail(context, uri)
        isLoading = false
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when {
            isLoading -> {
                CircularProgressIndicator(Modifier.size(24.dp))
            }
            thumbnailBitmap != null -> {
                // Display the rendered PDF page (ImageBitmap)
                Image(
                    bitmap = thumbnailBitmap!!,
                    contentDescription = "PDF page preview",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            else -> {
                // Error or Failed to load fallback
                Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = "Failed to load PDF thumbnail",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * Generic fallback for all other attachment MIME types (doc, xls, unknown, etc.).
 * Mirrors the `chat-sdk-ui`'s final fallback logic.
 */
@Composable
fun FallbackThumbnail(
    attachment: Attachment,
    modifier: Modifier,
) {
    // Generic document icon fallback
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Icon(
            Icons.Default.Description,
            contentDescription = "File attachment preview for ${attachment.friendlyName}",
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(32.dp)
        )
    }
}
