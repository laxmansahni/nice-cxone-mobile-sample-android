package com.isos.cxone

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.isos.cxone.ui.theme.CxoneSampleTheme
import com.nice.cxonechat.ChatInstanceProvider
import com.nice.cxonechat.Chat
import com.nice.cxonechat.ChatState
import com.nice.cxonechat.ChatState.Connected
import com.nice.cxonechat.ChatState.Connecting
import com.nice.cxonechat.ChatState.ConnectionLost
import com.nice.cxonechat.ChatState.Initial
import com.nice.cxonechat.ChatState.Offline
import com.nice.cxonechat.ChatState.SdkNotSupported
import com.nice.cxonechat.ChatState.Prepared
import com.nice.cxonechat.ChatState.Preparing
import com.nice.cxonechat.ChatState.Ready
import com.nice.cxonechat.exceptions.RuntimeChatException
import androidx.compose.runtime.mutableStateOf
import androidx.navigation.compose.rememberNavController
import com.isos.cxone.navigation.Navigation

class MainActivity : ComponentActivity(), ChatInstanceProvider.Listener {

    companion object {
        private const val TAG = "MainActivity"
    }

    // Renamed to liveChatState to clearly indicate it is the mutable, live source of the Compose state.
    private val liveChatState = mutableStateOf<ChatState>(Initial)

    // Track the last state that triggered a UI Toast/Action to prevent re-firing on Activity resume.
    private var lastProcessedChatState: ChatState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CxoneSampleTheme {
                // Pass the current value of the live state holder to the Composable
                MainScreen(currentChatState = liveChatState.value)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Register the listener when the activity becomes visible
        try {
            ChatInstanceProvider.get().addListener(this)
            Log.d(TAG, "ChatInstanceProvider listener registered.")
        } catch (e: IllegalStateException) {
            // This happens if the ChatInstanceProvider.create() call failed in the Application class
            Log.e(TAG, "SDK not initialized. Check application class.", e)
            Toast.makeText(this, "SDK not initialized.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onStop() {
        super.onStop()
        // Unregister the listener when the activity is no longer visible to prevent leaks
        try {
            ChatInstanceProvider.get().removeListener(this)
            Log.d(TAG, "ChatInstanceProvider listener unregistered.")
        } catch (e: IllegalStateException) {
            // Ignore if the instance was never created
        }
    }

    // --- ChatInstanceProvider.Listener Implementation (The Automatic State Machine) ---

    /**
     * The primary callback for all chat state changes.
     */
    override fun onChatStateChanged(chatState: ChatState) {
        Log.i(TAG, "Chat State Changed: $chatState")

        // 1. Check for duplicate state (FIX)
        if (lastProcessedChatState == chatState) {
            Log.d(TAG, "Ignoring duplicate ChatState change: $chatState")
            // Still update the live state so Compose reflects the current status on screen resume,
            // but skip the side-effects (Toasts, connect calls) that don't need to be repeated.
            liveChatState.value = chatState
            return
        }

        // 2. Update the Live State: Updating this MutableState triggers recomposition in MainScreen.
        liveChatState.value = chatState

        // Update the last processed state BEFORE running side effects (like Toasts/API calls)
        lastProcessedChatState = chatState

        // Ensure all UI operations run on the Main Thread
        runOnUiThread {
            try {
                val provider = ChatInstanceProvider.get()
                // This is where we handle the state machine logic
                when (chatState) {
                    Initial -> {
                        // ChatInstanceProvider wasn't initialized yet (or was explicitly reset).
                        Toast.makeText(
                            this,
                            "SDK Initial. Calling prepare()...",
                            Toast.LENGTH_SHORT
                        ).show()
                        provider.prepare(this@MainActivity)
                    }

                    Preparing -> {
                        // ChatInstanceProvider is being configured.
                        Toast.makeText(this, "Preparing SDK configuration...", Toast.LENGTH_SHORT)
                            .show()
                    }

                    Prepared -> {
                        // Key step: Once prepared, immediately initiate the socket connection automatically
                        Toast.makeText(
                            this,
                            "SDK Prepared. Calling connect()... (Automatic)",
                            Toast.LENGTH_SHORT
                        ).show()
                        try {
                            provider.connect()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error calling connect() from PREPARED state", e)
                        }
                    }

                    Connecting -> {
                        Toast.makeText(this, "Connecting...", Toast.LENGTH_SHORT).show()
                    }

                    Connected -> {
                        Toast.makeText(
                            this,
                            "Connected! Establishing session...",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    Ready -> {
                        val customerCustomValues = mapOf(
                            "amc" to "defaultAMC",
                            "city_country" to "India",
                            "client_name" to "Johnson & Johnson",
                            "email" to "itus@yopmail.com",
                            "first_name" to "IT",
                            "last_name" to "US",
                            "lat_long" to "28.4682|77.5090",
                            "membership_number" to "44977",
                            "phone_number" to "+917509029408",
                            "product_status" to "active",
                        )
                        val provider = ChatInstanceProvider.get()
                        provider.setCustomerValues(customerCustomValues)
                        Log.i(TAG, "ChatInstanceProvider customer values set: $customerCustomValues")

                        Toast.makeText(
                            this,
                            "Chat READY! You can now start the chat session.",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    ConnectionLost -> {
                        Toast.makeText(
                            this,
                            "Connection Lost (attempting to reconnect)",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    Offline -> {
                        Toast.makeText(this, "Chat Channel is Offline.", Toast.LENGTH_LONG).show()
                    }
                    SdkNotSupported -> {
                        Toast.makeText(this, "SDK version is not supported by the backend.", Toast.LENGTH_LONG).show()
                    }

                }
            } catch (e: IllegalStateException) {
                // If the provider fails to initialize in the Application class, this will catch the error.
                Log.e(TAG, "ChatInstanceProvider not available in onChatStateChanged", e)
            }
        }
    }

    /**
     * Invoked when the chat object changes. Not typically used for connection flow.
     */
    override fun onChatChanged(chat: Chat?) {
        Log.d(TAG, "Chat object changed: ${chat?.javaClass?.simpleName}")
    }

    /**
     * Invoked when chat reports runtime exception.
     */
    override fun onChatRuntimeException(exception: RuntimeChatException) {
        // Since this might also be called from a background thread, ensure UI work runs on main thread
        runOnUiThread {
            Log.e(TAG, "SDK Runtime Exception: ${exception.message}", exception)
            Toast.makeText(this, "Chat Error: ${exception.message}", Toast.LENGTH_LONG).show()
        }
    }
}

/**
 * Main Composable Screen: Now receives the current state as a parameter.
 */
@Composable
fun MainScreen(currentChatState: ChatState) {
    val navController = rememberNavController()
    val context = LocalContext.current
    // Callback for the "Check & Manage Connection" button in LauncherScreen
    val onConnectionActionClick: () -> Unit = {
        try {
            val provider = ChatInstanceProvider.get()
            val currentState = provider.chatState

            val message = when (currentState) {
                Initial -> {
                    provider.prepare(context)
                    "Initial. Attempting Prepare()..."
                }
                Prepared -> "Prepared. Waiting for automatic Connect()..."
                Ready -> {
                    // If already ready, start the chat session immediately
                    "Ready. Use the buttons below to start a chat."
                    }
                Connecting, Connected, Preparing -> "Connection/Preparation in progress ($currentState). Button action skipped."
                else -> "Current State: $currentState. Waiting for state change..."
            }
            Log.i("MainScreen", message)
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()

        } catch (e: IllegalStateException) {
            val message = "Error: SDK not initialized. Check logs."
            Log.e("MainScreen", message, e)
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            val message = "Connect error: ${e.message}"
            Log.e("MainScreen", message, e)
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Navigation(navController = navController,
                currentChatState = currentChatState,
                onConnectionActionClick = onConnectionActionClick)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    CxoneSampleTheme {
        // Must provide a default state for the preview
        MainScreen(currentChatState = Initial)
    }
}
