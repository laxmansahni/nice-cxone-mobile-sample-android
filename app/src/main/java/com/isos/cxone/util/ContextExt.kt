package com.isos.cxone.util

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/**
 * Ask android to open a URL if possible.
 *
 * @param url URL to open
 * @param mimeType mime type of [url] if known
 * @return true iff android could open the URL
 */
internal fun Context.openWithAndroid(url: String, mimeType: String?): Boolean {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(url.toUri(), mimeType)
    }

    return if (intent.resolveActivity(packageManager) == null) {
        false
    } else {
        startActivity(intent)
        true
    }
}

