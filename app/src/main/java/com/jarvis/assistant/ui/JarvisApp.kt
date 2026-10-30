package com.jarvis.assistant.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jarvis.assistant.ui.components.JarvisOrb
import com.jarvis.assistant.ui.components.OrbState

@Composable
fun JarvisApp(vm: JarvisViewModel = viewModel()) {
    val state by vm.ui.collectAsStateWithLifecycle()

    BackHandler(enabled = state.showSettings) {
        vm.closeSettings()
    }

    when {
        state.loading -> LoadingScreen()
        !state.onboardingComplete -> OnboardingScreen(
            state = state,
            onSave = vm::saveProfile,
            onDismissError = vm::dismissError,
        )
        state.showSettings -> SettingsScreen(
            state = state,
            onBack = vm::closeSettings,
            viewModel = vm,
        )
        else -> ChatScreen(
            state = state,
            onInputChange = vm::onInputChange,
            onSend = vm::send,
            onStop = vm::stop,
            onOpenSettings = vm::openSettings,
            onDismissError = vm::dismissError,
            onRetry = vm::retryLast,
        )
    }
}

@Composable
private fun LoadingScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            JarvisOrb(
                state = OrbState.IDLE,
                modifier = Modifier.size(128.dp),
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = "J A R V I S",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 5.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Initializing",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
