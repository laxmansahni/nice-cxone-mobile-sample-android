package com.isos.cxone.models

import androidx.compose.runtime.Immutable
import com.nice.cxonechat.message.MessageAuthor
import com.nice.cxonechat.thread.Agent
import java.util.Locale

/**
 * Represents a person (agent or user) in the chat system.
 * This UI model extends the SDK's MessageAuthor and provides display-friendly properties.
 *
 * @property id Unique identifier for the person.
 * @property firstName First name of the person.
 * @property lastName Last name of the person.
 * @property imageUrl Optional URL for the person's image.
 */
@Immutable
data class Person(
    override val id: String = "",
    override val firstName: String = "",
    override val lastName: String = "",
    override val imageUrl: String? = null,
) : MessageAuthor() {
    /** The person's initials (e.g., "J D" for "Jane Doe"). */
    val monogram: String? = listOf(firstName, lastName)
        .mapNotNull { it.firstOrNull()?.uppercase(Locale.getDefault()) }
        .joinToString(separator = "")
        .ifBlank { null }

    /** The person's full combined name. */
    val fullName: String? = listOf(firstName, lastName)
        .mapNotNull { it.ifBlank { null } }
        .joinToString(separator = " ")
        .ifBlank { null }
}

private const val DEFAULT_IMAGE_URL = "(.*/img/user.*\\.png)"

/**
 * Remove default server supplied images used for user avatars by checking if the URL matches
 * a known pattern for default placeholder images.
 *
 * @param url Image url which should be evaluated.
 * @return Either original [url] or `null` iff it matches expected pattern.
 */
internal fun removeDefaultImageUrl(url: String?): String? =
    if (url == null || DEFAULT_IMAGE_URL.toRegex().matches(url)) null else url

/**
 * Extension property to convert the SDK's Agent model to the UI's Person model.
 */
internal val Agent.asPerson: Person
    get() = Person(
        id = id.toString(),
        firstName = firstName,
        lastName = lastName,
        imageUrl = removeDefaultImageUrl(imageUrl)
    )

/**
 * Extension property to convert the SDK's general MessageAuthor model to the UI's Person model.
 */
internal val MessageAuthor.asPerson: Person
    get() = Person(
        id = id,
        firstName = firstName,
        lastName = lastName,
        imageUrl = removeDefaultImageUrl(imageUrl)
    )
