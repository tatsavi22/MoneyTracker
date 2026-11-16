package com.example.moneytracker.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Lock screen shown before the user authenticates with biometric or PIN.
 *
 * Authentication itself is triggered imperatively from [MainActivity] using
 * the BiometricPrompt API (which must run on the Activity, not from a
 * Composable). This screen just shows the locked state and a button to
 * re-trigger the prompt if the user dismissed it.
 *
 * @param onUnlockClicked  Called when the user taps the unlock button.
 */
@Composable
fun LockScreen(onUnlockClicked: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "MoneyTracker",
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Unlock to view your transactions",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onUnlockClicked) {
            Text("Unlock")
        }
    }
}
