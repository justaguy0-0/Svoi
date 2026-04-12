package com.example.svoi.ui.newchat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.svoi.ui.components.Avatar
import com.example.svoi.ui.theme.GroupAvatarColors
import com.example.svoi.ui.theme.SvoiDimens
import com.example.svoi.ui.theme.SvoiShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    onGroupCreated: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: NewChatViewModel = viewModel()
) {
    val query by viewModel.searchQuery.collectAsState()
    val results by viewModel.filteredContacts.collectAsState()
    val selected by viewModel.selectedUsers.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val contactsLoading by viewModel.contactsLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    var groupName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Создать группу") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.createGroup(groupName, onGroupCreated)
                        },
                        enabled = selected.isNotEmpty() && groupName.isNotBlank() && !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Создать")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            // Group name input
            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it; viewModel.clearError() },
                label = { Text("Название группы") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SvoiDimens.ScreenHorizontalPadding, vertical = 8.dp),
                singleLine = true,
                leadingIcon = {
                    if (groupName.isNotBlank()) {
                        Avatar(
                            emoji = "",
                            bgColor = GroupAvatarColors[Math.abs(groupName.hashCode()) % GroupAvatarColors.size],
                            isGroup = true,
                            letter = groupName,
                            size = 32.dp,
                            fontSize = 14.sp
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                shape = SvoiShapes.TextField,
                isError = error != null
            )

            error?.let { msg ->
                Text(
                    text = msg,
                    modifier = Modifier.padding(horizontal = SvoiDimens.ScreenHorizontalPadding),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Selected users chips
            if (selected.isNotEmpty()) {
                LazyRow(modifier = Modifier.padding(horizontal = SvoiDimens.ScreenHorizontalPadding, vertical = 4.dp)) {
                    items(selected, key = { it.id }) { profile ->
                        InputChip(
                            selected = true,
                            onClick = { viewModel.toggleUserSelection(profile) },
                            label = { Text(profile.displayName) },
                            avatar = {
                                Avatar(
                                    emoji = profile.emoji,
                                    bgColor = profile.bgColor,
                                    size = 24.dp,
                                    fontSize = 12.sp
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Убрать",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }
                HorizontalDivider()
            }

            // Contact filter (local — no network request)
            SearchBar(
                query = query,
                onQueryChange = viewModel::onQueryChange,
                onSearch = {},
                active = false,
                onActiveChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SvoiDimens.ScreenHorizontalPadding, vertical = 8.dp),
                windowInsets = WindowInsets(0),
                placeholder = { Text("Поиск среди контактов") },
                leadingIcon = { Icon(Icons.Default.Search, null) }
            ) {}

            when {
                contactsLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                results.isEmpty() && !contactsLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (query.isBlank())
                                "Нет контактов. Сначала начните переписку с кем-нибудь."
                            else
                                "Ничего не найдено",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(results, key = { it.id }) { profile ->
                            val isSelected = viewModel.isSelected(profile.id)
                            UserItem(
                                profile = profile,
                                onClick = { viewModel.toggleUserSelection(profile) },
                                trailing = {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { viewModel.toggleUserSelection(profile) }
                                    )
                                }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 72.dp),
                                thickness = 0.4.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }
    }
}
