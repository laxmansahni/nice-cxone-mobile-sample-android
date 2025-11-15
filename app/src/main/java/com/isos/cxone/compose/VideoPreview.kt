package com.isos.cxone.compose

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.ImageLoader
import coil3.video.VideoFrameDecoder
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import android.util.Log
import com.nice.cxonechat.message.Attachment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Downloading
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.ui.graphics.vector.rememberVectorPainter

/**
 * Displays a video thumbnail with an animated play icon indicator.
 * It uses Coil's VideoFrameDecoder to extract the first frame of the video
 * as the thumbnail.
 *
 * @param attachment The video attachment containing the URL and friendly name.
 * @param modifier Modifier to apply to the composable.
 */
@Composable
fun VideoPreview(
    attachment: Attachment,
    modifier: Modifier
) {
    // State to track whether the image has been successfully loaded
    var imageLoaded by remember { mutableStateOf(false) }

    val imageLoader = ImageLoader.Builder(LocalContext.current)
        .components {
            add(VideoFrameDecoder.Factory())
        }
        .crossfade(true)
        .build()

    val placeholderPainter = rememberVectorPainter(image = Icons.Outlined.Downloading)
    val fallbackPainter = rememberVectorPainter(image = Icons.Outlined.Description)
    val errorPainter = rememberVectorPainter(image = Icons.Outlined.ErrorOutline)

    // Build the request for video thumbnail extraction.
    val request = ImageRequest.Builder(LocalContext.current)
        .data(attachment.url)
        .crossfade(true)
        .listener(
            onError = { _, result ->
                // Log the error details to help debug network or token issues
                Log.e(
                    "VideoPreview",
                    "Coil failed to load video thumbnail: ${attachment.url}. Reason: ${result.throwable.message}",
                    result.throwable
                )
            }
        )
        .build()

    // Display the video thumbnail and centered play icon
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        // AsyncImage to load and display the video thumbnail (or error/placeholder icon)
        AsyncImage(
            imageLoader = imageLoader,
            model = request,
            contentDescription = attachment.friendlyName,
            // Use ContentScale.Fit to prevent clipping of error/placeholder icons.
            contentScale = ContentScale.Fit,
            placeholder = placeholderPainter,
            fallback = fallbackPainter,
            error = errorPainter,
            onSuccess = { imageLoaded = true },
            modifier = Modifier.fillMaxSize(),
        )

        // Animated visibility for the play icon, only visible after success
        AnimatedVisibility(
            visible = imageLoaded,
            enter = fadeIn(),
        ) {
            // Standard Play Icon (using Material Icon replacement for custom ChatIcon)
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Play video",
                tint = Color.White.copy(alpha = 0.8f),
                // Use a noticeable, large size for the play button
                modifier = Modifier.size(48.dp)
            )
        }
    }
}