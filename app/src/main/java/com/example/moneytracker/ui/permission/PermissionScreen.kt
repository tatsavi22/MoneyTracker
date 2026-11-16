package com.example.moneytracker.ui.permission

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.moneytracker.R

/**
 * Shown when the SMS permission has not been granted.
 *
 * Plain wording explains why the app needs the permission, in line with
 * PROJECT_SPEC section 9: "plain wording, no jargon".
 *
 * Also surfaces the Android 13+ restricted-settings hint when the app is
 * installed from an APK (PROJECT_SPEC section 13).
 *
 * @param isPermanentlyDenied  true if the user has checked "Don't ask again",
 *                             so we should send them to system settings instead.
 * @param onGrantClicked       Called to trigger the runtime permission request.
 * @param onOpenSettingsClicked Called to open the system App Info screen.
 */
@Composable
fun PermissionScreen(
    isPermanentlyDenied: Boolean,
    onGrantClicked: () -> Unit,
    onOpenSettingsClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "SMS permission needed",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.permission_rationale),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.permission_denied_hint),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        )
        Spacer(modifier = Modifier.height(32.dp))
        if (isPermanentlyDenied) {
            Button(onClick = onOpenSettingsClicked) {
                Text(stringResource(R.string.open_settings))
            }
        } else {
            Button(onClick = onGrantClicked) {
                Text(stringResource(R.string.grant_permission))
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onOpenSettingsClicked) {
                Text(stringResource(R.string.open_settings))
            }
        }
    }
}
