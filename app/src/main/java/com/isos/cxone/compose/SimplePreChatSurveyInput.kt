package com.isos.cxone.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.isos.cxone.viewmodel.ChatAllConversationsViewModel
import com.nice.cxonechat.exceptions.CXoneException
import android.util.Log
import android.widget.Toast
import com.nice.cxonechat.prechat.PreChatSurvey
import com.nice.cxonechat.prechat.PreChatSurveyResponse
import com.nice.cxonechat.state.FieldDefinition
import com.nice.cxonechat.state.FieldDefinition.Text
import com.nice.cxonechat.state.FieldDefinition.Selector
import com.nice.cxonechat.state.FieldDefinition.Hierarchy
import com.nice.cxonechat.state.SelectorNode
import com.nice.cxonechat.state.HierarchyNode
import kotlinx.coroutines.launch

// Helper function to find the SelectorNode object from the list of values using the selected label
fun findSelectorNode(definition: Selector, label: String): SelectorNode? {
    return definition.values.firstOrNull { it.label == label }
}

// Helper function to find the leaf HierarchyNode object from the tree using the selected label
fun findHierarchyNode(definition: Hierarchy, label: String): HierarchyNode<String>? {
    fun findLeafByLabel(nodes: Sequence<HierarchyNode<String>>): HierarchyNode<String>? {
        for (node in nodes) {
            if (node.isLeaf && node.label == label) return node
            val childResult = findLeafByLabel(node.children)
            if (childResult != null) return childResult
        }
        return null
    }
    return findLeafByLabel(definition.values)
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimplePreChatSurveyInput(
    onCancel: () -> Unit,
    onPreChatSurveyCompleted: () -> Unit
) {
    val context = LocalContext.current
    val chatAllConversationsViewModel: ChatAllConversationsViewModel = viewModel()
    val coroutineScope = rememberCoroutineScope() // Get a scope for launching async work

    // State to hold user responses (FieldId -> Value string/label)
    val responseMap = remember {
        mutableStateMapOf<String, String>()
    }

    // State to handle loading when creating the thread
    var isLoading by remember { mutableStateOf(false) }

    // Fetch the dynamic pre-chat survey definition from the ViewModel
    val preChatSurvey: PreChatSurvey? = chatAllConversationsViewModel.preChatSurvey

    // Determine the list of fields to render
    val fieldsToRender = remember(preChatSurvey) {
        preChatSurvey?.fields?.toList() ?: emptyList()
    }

    // The set of IDs for all required fields in the survey definition
    val requiredFieldIds = remember(fieldsToRender) {
        fieldsToRender.filter { it.isRequired }.map { it.fieldId }.toSet()
    }

    // By computing this directly, it ensures the set recalculates on every recomposition,
    // which is triggered when responseMap changes.
    // The set of IDs for all fields that have a non-empty response value from the user
    val answeredFieldIds = responseMap.filterValues { it.isNotEmpty() }.keys

    // This validation logic now correctly uses the
    // up-to-date answeredFieldIds set on every recomposition.
    // Validation logic: check if all required field IDs are present in the answered field IDs
    val areAllRequiredFieldsAnswered = answeredFieldIds.containsAll(requiredFieldIds)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = preChatSurvey?.name ?: "Pre-Chat Survey",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Dynamic Survey UI rendering
        if (fieldsToRender.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // Take up remaining vertical space
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(fieldsToRender) { field ->
                    DynamicSurveyField(
                        field = field,
                        currentValue = responseMap[field.fieldId] ?: "",
                        onValueChange = { newValue ->
                            // Update the response map.
                            responseMap[field.fieldId] = newValue
                        }
                    )
                }
            }
        } else {
            Text(
                text = "No pre-chat survey fields found. Starting chat immediately.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = onCancel,
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
            ) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    if (areAllRequiredFieldsAnswered) {
                        isLoading = true
                        coroutineScope.launch {
                            try {
                                // 1. Prepare Custom Fields (usually empty if all fields are handled by surveyResponse)
                                val customFields = emptyMap<String, String>()

                                // 2. Prepare Structured PreChatSurveyResponse using the SDK's factory methods
                                val surveyResponse: Sequence<PreChatSurveyResponse<out FieldDefinition, out Any>> =
                                    fieldsToRender.mapNotNull { field ->
                                        val value = responseMap[field.fieldId]

                                        // Only include fields that have a non-empty value
                                        if (value.isNullOrEmpty()) {
                                            return@mapNotNull null
                                        }

                                        when (field) {
                                            is Text -> PreChatSurveyResponse.Text.create(field, value)

                                            is Selector -> {
                                                val node = findSelectorNode(field, value)
                                                if (node != null) {
                                                    PreChatSurveyResponse.Selector.create(field, node)
                                                } else {
                                                    Log.e("PreChatSurvey", "Selector node not found for label: $value")
                                                    null
                                                }
                                            }

                                            is Hierarchy -> {
                                                val node = findHierarchyNode(field, value)
                                                // Hierarchy response must be a leaf node
                                                if (node != null && node.isLeaf) {
                                                    PreChatSurveyResponse.Hierarchy.create(field, node)
                                                } else {
                                                    Log.e("PreChatSurvey", "Hierarchy node not found or not a leaf for label: $value")
                                                    null
                                                }
                                            }

                                            else -> {
                                                Log.w("PreChatSurvey", "Unsupported field type: ${field::class.simpleName}")
                                                null
                                            }
                                        }
                                    }.asSequence()

                                // 3. Call the ViewModel's suspend function to create the thread
                                val threadHandler = chatAllConversationsViewModel.createThread(
                                    customFields = customFields,
                                    preChatSurveyResponse = surveyResponse
                                )
                                val thread = threadHandler.get()
                                Log.i("SimplePreChatSurveyInput", "New thread created with id=${thread.id}.")
                                onPreChatSurveyCompleted()
                            } catch (e: CXoneException) {
                                Log.e("PreChatSurvey", "CXone Error: ${e.message}", e)
                                Toast.makeText(
                                    context,
                                    "Chat Error: ${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            } catch (e: Exception) {
                                Log.e("PreChatSurvey", "General Error: ${e.message}", e)
                                Toast.makeText(
                                    context,
                                    "Failed to start chat: ${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            } finally {
                                isLoading = false
                            }
                        }
                    } else {
                        Toast.makeText(context, "Please answer all required fields.", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = areAllRequiredFieldsAnswered && !isLoading
            ) {
                Text("Start Chat")
            }
        }
    }
}
/**
 * A composable to dynamically render different FieldDefinition types.
 * Supports Text, Selector (Dropdown), and Hierarchy (Simplified Dropdown of Leaf Nodes).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DynamicSurveyField(
    field: FieldDefinition,
    currentValue: String, // Stores the selected label/text
    onValueChange: (String) -> Unit
) {
    // Add asterisk if required
    val label = if (field.isRequired) "${field.label} *" else field.label

    when (field) {
        is Text -> {
            OutlinedTextField(
                value = currentValue,
                onValueChange = onValueChange,
                label = { Text(label) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (field.isEMail) KeyboardType.Email else KeyboardType.Text
                ),
                // Show error state if required and empty
                isError = field.isRequired && currentValue.isEmpty(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        is Selector -> {
            var expanded by remember { mutableStateOf(false) }
            val selectedNode = remember(currentValue) { findSelectorNode(field, currentValue) }
            val displayLabel = selectedNode?.label ?: (if (field.isRequired) "Select Required Option" else "Select Option")

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    readOnly = true,
                    value = displayLabel,
                    onValueChange = {},
                    label = { Text(label) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.textFieldColors(),
                    isError = field.isRequired && selectedNode == null
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    field.values.forEach { selectionNode ->
                        DropdownMenuItem(
                            text = { Text(selectionNode.label) },
                            onClick = {
                                onValueChange(selectionNode.label) // Update central state with label
                                expanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }
        }

        is Hierarchy -> {
            var expanded by remember { mutableStateOf(false) }

            // Get all leaf nodes for simple selection UI
            val allLeafNodes: List<HierarchyNode<String>> = remember(field) {
                fun Sequence<HierarchyNode<String>>.getLeafs(): List<HierarchyNode<String>> {
                    return this.flatMap { node ->
                        if (node.isLeaf) listOf(node) else node.children.getLeafs()
                    }.toList()
                }
                field.values.getLeafs()
            }

            val selectedNode = remember(currentValue) { findHierarchyNode(field, currentValue) }
            val displayLabel = selectedNode?.label ?: (if (field.isRequired) "Select Required Leaf Node" else "Select Leaf Node")


            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    readOnly = true,
                    value = displayLabel,
                    onValueChange = {},
                    label = { Text(label) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.textFieldColors(),
                    isError = field.isRequired && selectedNode == null
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    allLeafNodes.forEach { hierarchyNode ->
                        DropdownMenuItem(
                            text = { Text(hierarchyNode.label) },
                            onClick = {
                                onValueChange(hierarchyNode.label) // Update central state with label
                                expanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }
        }

        else -> {
            // Placeholder for unsupported fields
            Text(
                text = "Unsupported field type: ${field::class.simpleName}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}