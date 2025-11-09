package com.isos.cxone.navigation

import kotlinx.serialization.Serializable
import java.util.UUID

// Define the routes
// Define a Launch route that doesn't take any arguments
@Serializable
object Launch

@Serializable
object ThreadList

@Serializable
data class SingleThread(
    val threadId: String
)
