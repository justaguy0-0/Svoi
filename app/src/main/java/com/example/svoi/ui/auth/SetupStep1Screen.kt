package com.example.svoi.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupStep1Screen(
    inviteKey: String,
    onNext: () -> Unit,
    onBack: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    var displayName by remember { mutableStateOf(viewModel.regDisplayName) }
    var about by remember { mutableStateOf(viewModel.regAbout) }
    var email by remember { mutableStateOf(viewModel.regEmail) }
    val error by viewModel.error.collectAsState()

    LaunchedEffect(inviteKey) {
        viewModel.regInviteKey = inviteKey
        viewModel.clearError()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Шаг 1 из 3") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
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

            Text(
                "Расскажите о себе",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Это увидят другие участники",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it; viewModel.clearError() },
                label = { Text("Ваше имя") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                shape = MaterialTheme.shapes.medium
            )

            OutlinedTextField(
                value = about,
                onValueChange = { about = it; viewModel.clearError() },
                label = { Text("О себе") },
                placeholder = { Text("Напишите что-нибудь о себе") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                shape = MaterialTheme.shapes.medium
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; viewModel.clearError() },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done
                ),
                shape = MaterialTheme.shapes.medium
            )

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = { viewModel.validateStep1(displayName, about, email, onNext) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Далее", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
