package com.isos.cxone.compose

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.isos.cxone.viewmodel.ChatAllConversationsViewModel
import androidx.compose.runtime.*
import com.isos.cxone.models.ThreadDisplayItem
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadListScreen(
    viewModel: ChatAllConversationsViewModel = viewModel(),
    onThreadSelected: (String) -> Unit,
    navigateUp: () -> Unit = {}
) {
    var showArchived by remember { mutableStateOf(false) }

    // 2. Map the raw SDK threads to the display model list (ThreadDisplayItem)
    val allThreads by viewModel.threads.collectAsState()
    val dialogState by viewModel.showDialog.collectAsState()

    val activeThreads = remember(allThreads) { allThreads.filter { it.isActive } }
    val archivedThreads = remember(allThreads) { allThreads.filter { !it.isActive } }

    val threadsToShow = if (showArchived) archivedThreads else activeThreads

    // Function to simulate archiving a thread (real implementation would call ViewModel logic)
    val onArchiveThread: (ThreadDisplayItem) -> Unit = { threadToArchive ->
        Log.d("ThreadListScreen", "Simulating archive call for thread: ${threadToArchive.displayName}")
        // In a real app, calling viewModel.archiveThread() would update the rawChatThreads StateFlow,
        // which automatically triggers a recomposition here.
    }

    // LaunchedEffect to observe the refresh signal and trigger a data refresh
    LaunchedEffect(Unit) {
        viewModel.refreshEvent.collect {
            Log.d("ThreadListScreen", "Refresh event received. Forcing threads refresh.")
            viewModel.refreshThreads()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Conversations") },
                navigationIcon = {
                    IconButton(onClick = navigateUp) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            // Thread Toggle (Active vs. Archived)
            ThreadStateToggle(showArchived = showArchived) { isArchivedSelected ->
                showArchived = isArchivedSelected
            }

            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // Thread List Content
            if (threadsToShow.isEmpty()) {
                EmptyThreadsView(isArchived = showArchived)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(threadsToShow, key = { it.id }) { thread ->
                        val onEditName = { viewModel.editThreadName(thread) }
                        // Only allow swipe to archive for ACTIVE threads
                        if (thread.isActive) {
                            SwipeableThreadWrapper(
                                thread = thread,
                                onArchiveThread = onArchiveThread,
                                onThreadSelected = onThreadSelected,
                                onEditName = onEditName
                            )
                        } else {
                            // Non-swipeable view for archived threads
                            ThreadListItem(thread = thread, onThreadSelected = onThreadSelected, onEditName = onEditName )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
    // Dialog Rendering (Observing ViewModel State)
    when (val dialog = dialogState) {
        is ChatAllConversationsViewModel.Dialog.EditThreadName -> {
            EditThreadNameDialog(
                initialThreadName = dialog.thread.displayName,
                onCancel = viewModel::dismissDialog,
                onAccept = { newName ->
                    viewModel.confirmEditThreadName(dialog.thread, newName)
                }
            )
        }
        ChatAllConversationsViewModel.Dialog.None -> {
            // Do nothing
        }
    }
}

/** Segmented button to toggle between Active and Archived threads. */
@Composable
private fun ThreadStateToggle(showArchived: Boolean, onValueChanged: (Boolean) -> Unit) {
    val options = listOf("Active", "Closed")
    val selectedIndex = if (showArchived) 1 else 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        SingleChoiceSegmentedButtonRow {
            options.forEachIndexed { index, label ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.baseShape,
                    onClick = { onValueChanged(index == 1) },
                    selected = index == selectedIndex
                ) {
                    Text(label)
                }
            }
        }
    }
}

/** Wraps a thread list item with the swipe-to-dismiss behavior. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableThreadWrapper(
    thread: ThreadDisplayItem,
    onArchiveThread: (ThreadDisplayItem) -> Unit,
    onThreadSelected: (String) -> Unit,
    onEditName: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onArchiveThread(thread)
                return@rememberSwipeToDismissBoxState true
            }
            return@rememberSwipeToDismissBoxState false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            SwipeBackground(dismissState.targetValue)
        },
        content = {
            ThreadListItem(thread = thread,
                onThreadSelected = onThreadSelected,
                onEditName = onEditName )
        }
    )
}

/** The visual component for the swipe action background (Archive icon). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeBackground(targetValue: SwipeToDismissBoxValue) {
    val alignment = Alignment.CenterEnd
    val icon = Icons.Default.Archive

    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentAlignment = alignment
    ) {
        if (targetValue != SwipeToDismissBoxValue.Settled) {
            Icon(
                icon,
                contentDescription = "Archive",
                tint = Color.White
            )
        }
    }
}

/** Displays the content of a single thread item. */
@Composable
private fun ThreadListItem(thread: ThreadDisplayItem, onThreadSelected: (String) -> Unit, onEditName: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onThreadSelected(thread.id) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Leading Content: Icon
            Icon(
                imageVector = Icons.Filled.ChevronRight, // Placeholder icon
                contentDescription = "Thread Link",
                modifier = Modifier.padding(end = 16.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Column(modifier = Modifier.weight(1f)) {
                // Headline Content: Thread Name and Last Message Time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = thread.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = thread.lastMessageTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Supporting Content: Last Message Snippet
                Text(
                    text = thread.lastMessage,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Trailing Content: Edit Button
            IconButton(onClick = onEditName) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Thread Name",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** View displayed when the list is empty. */
@Composable
private fun EmptyThreadsView(isArchived: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (isArchived) "No archived threads found." else "No active conversations.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isArchived) "Archived chats will appear here." else "Start a new chat from the previous screen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
internal fun EditThreadNameDialog(
    initialThreadName: String,
    onCancel: () -> Unit,
    onAccept: (String) -> Unit,
) {
    var nameState by remember { mutableStateOf(initialThreadName) }
    // Enable OK button only if name has changed and is not blank
    val isEnabled = nameState.isNotBlank() && nameState != initialThreadName

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(text = "Update Thread Name") },
        text = {
            OutlinedTextField(
                value = nameState,
                onValueChange = { nameState = it },
                label = { Text("Thread Name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onAccept(nameState) },
                enabled = isEnabled,
            ) {
                Text(text = "OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(text = "Cancel")
            }
        }
    )
}