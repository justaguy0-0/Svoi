package com.example.svoi.ui.auth

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.svoi.ui.theme.SvoiDimens
import com.example.svoi.ui.theme.SvoiShapes

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
                title = { Text("Регистрация") },
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
            Spacer(Modifier.height(4.dp))

            StepIndicator(currentStep = 1, totalSteps = 3)

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
                shape = SvoiShapes.TextField
            )

            OutlinedTextField(
                value = about,
                onValueChange = { about = it; viewModel.clearError() },
                label = { Text("О себе") },
                placeholder = { Text("Напишите что-нибудь о себе") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                shape = SvoiShapes.TextField
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
                shape = SvoiShapes.TextField
            )

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = { viewModel.validateStep1(displayName, about, email, onNext) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SvoiDimens.ButtonHeight),
                shape = SvoiShapes.Button
            ) {
                Text("Далее", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun StepIndicator(currentStep: Int, totalSteps: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSteps) { index ->
            val step = index + 1
            val isActive = step <= currentStep
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        CircleShape
                    )
            )
            if (step < totalSteps) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(2.dp)
                        .background(
                            if (step < currentStep) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                )
            }
        }
    }
}
