package com.example.svoi.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.svoi.ui.components.Avatar
import com.example.svoi.ui.components.EmojiPicker
import com.example.svoi.ui.theme.AvatarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupProfileScreen(
    inviteKey: String,
    onSetupComplete: () -> Unit,
    onBack: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var selectedEmoji by remember { mutableStateOf("😊") }
    var selectedColor by remember { mutableStateOf(AvatarColors[4]) } // Indigo

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройка профиля") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
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
                .padding(horizontal = 24.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // Preview avatar
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Avatar(
                    emoji = selectedEmoji,
                    bgColor = selectedColor,
                    size = 80.dp,
                    fontSize = 38.sp
                )
            }

            // Name
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it; viewModel.clearError() },
                label = { Text("Ваше имя") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                shape = MaterialTheme.shapes.medium
            )

            // Email
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; viewModel.clearError() },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                shape = MaterialTheme.shapes.medium
            )

            // Password
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; viewModel.clearError() },
                label = { Text("Пароль (мин. 6 символов)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                shape = MaterialTheme.shapes.medium
            )

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; viewModel.clearError() },
                label = { Text("Повторите пароль") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                shape = MaterialTheme.shapes.medium
            )

            // Emoji picker (same keyboard as in chat)
            Text("Выберите эмодзи", style = MaterialTheme.typography.titleMedium)
            EmojiPicker(
                onEmojiSelected = { selectedEmoji = it },
                modifier = Modifier.fillMaxWidth()
            )

            // Color picker — 2 symmetric rows (chunked by 6)
            Text("Цвет аватара", style = MaterialTheme.typography.titleMedium)
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                AvatarColors.chunked(6).forEach { rowColors ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        rowColors.forEach { hex ->
                            val color = runCatching {
                                Color(android.graphics.Color.parseColor(hex))
                            }.getOrDefault(Color.Gray)
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .then(
                                        if (hex == selectedColor)
                                            Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        else Modifier
                                    )
                                    .clickable { selectedColor = hex }
                            )
                        }
                    }
                }
            }

            error?.let { msg ->
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                onClick = {
                    viewModel.signUp(
                        inviteKey = inviteKey,
                        email = email,
                        password = password,
                        confirmPassword = confirmPassword,
                        displayName = displayName,
                        emoji = selectedEmoji,
                        bgColor = selectedColor,
                        onSuccess = onSetupComplete
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !isLoading,
                shape = MaterialTheme.shapes.medium
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Создать аккаунт", style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
