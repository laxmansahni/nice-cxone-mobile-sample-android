package com.isos.cxone.compose

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nice.cxonechat.ChatState
import com.nice.cxonechat.ChatState.Connected
import com.nice.cxonechat.ChatState.Connecting
import com.nice.cxonechat.ChatState.ConnectionLost
import com.nice.cxonechat.ChatState.Initial
import com.nice.cxonechat.ChatState.Offline
import com.nice.cxonechat.ChatState.Prepared
import com.nice.cxonechat.ChatState.Preparing
import com.nice.cxonechat.ChatState.Ready
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
/**
 * The primary landing screen responsible for displaying connection status
 * and providing navigation options for starting chat sessions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherScreen(
    chatState: ChatState,
    onConnectionActionClick: () -> Unit,
    onThreadListClicked: () -> Unit
) {
    val statusText = "Current State: $chatState"

    val statusColor = when (chatState) {
        Ready, Connected -> MaterialTheme.colorScheme.primary
        Connecting, Preparing -> Color.Blue
        ConnectionLost, Offline -> MaterialTheme.colorScheme.error
        else -> Color.Gray // Initial, Prepared
    }
    var showPreChatSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // --- Connection Status Display ---
        Text(
            text = statusText,
            style = MaterialTheme.typography.titleLarge,
            color = statusColor,
            modifier = Modifier.height(60.dp) // Maintain space to prevent layout shift
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Button to manually check state and try to connect/prepare
        Button(onClick = onConnectionActionClick) {
            Text("Check & Manage Connection")
        }

        // --- Action Buttons (Only visible when Ready) ---
        if (chatState == Ready) {
            Spacer(modifier = Modifier.height(24.dp))

            // 1. Thread List (For multi-thread management)
            Button(onClick = onThreadListClicked) {
                Text("View All Threads")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Direct Single Thread Creation (no survey)
            Button(onClick = {}) {
                Text("Create a Direct Thread")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Pre-Chat Form (Open the local Modal Bottom Sheet)
            Button(onClick = {
                showPreChatSheet = true
            }) {
                Text("Fill Pre-Chat Form")
            }
        }
    }

    // --- Modal Bottom Sheet for Pre-Chat Survey (Local UI Overlay) ---
    if (showPreChatSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showPreChatSheet = false
            },
            sheetState = sheetState,
        ) {
            SimplePreChatSurveyInput(
                onCancel = {
                    // User clicked cancel button, hide the sheet
                    coroutineScope.launch {
                        sheetState.hide()
                    }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            showPreChatSheet = false
                        }
                    }
                },
                onPreChatSurveyCompleted = {
                    // Survey completed, hide the sheet
                    coroutineScope.launch {
                        sheetState.hide()
                    }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            showPreChatSheet = false
                            // Navigate to the thread list screen upon successful thread creation
                            onThreadListClicked()
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.height(32.dp)) // Add bottom padding for better sheet aesthetics
        }
    }
}

