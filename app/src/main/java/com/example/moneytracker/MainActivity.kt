package com.example.moneytracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.moneytracker.ui.home.HomeScreen
import com.example.moneytracker.ui.home.HomeViewModel
import com.example.moneytracker.ui.lock.LockScreen
import com.example.moneytracker.ui.permission.PermissionScreen
import com.example.moneytracker.ui.settings.SettingsScreen
import com.example.moneytracker.ui.settings.SettingsViewModel
import com.example.moneytracker.ui.theme.MoneyTrackerTheme
import com.example.moneytracker.work.ImportInboxWorker

/**
 * Single-activity host for the entire app.
 *
 * Responsibilities:
 *  1. Apply FLAG_SECURE so the recents thumbnail hides amounts (PROJECT_SPEC §9).
 *  2. Gate access behind the lock screen (if lock is enabled).
 *  3. Handle the SMS runtime permission request.
 *  4. Host the bottom-navigation between Home and Settings.
 */
class MainActivity : FragmentActivity() {

    private val app by lazy { application as MoneyTrackerApp }

    private val homeViewModel: HomeViewModel by viewModels {
        HomeViewModel.Factory(app.container.repository, app.container.userPreferences)
    }
    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModel.Factory(app.container)
    }

    // Track whether the user has seen a denial (for "permanently denied" UX)
    private var permissionDeniedOnce = false

    // ── Permission launcher ───────────────────────────────────────────────────

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[Manifest.permission.READ_SMS] == true
        if (granted) {
            val app = application as MoneyTrackerApp
            enqueueInboxImport()
            app.container.userPreferences.setInitialImportDone(true)
        } else {
            permissionDeniedOnce = true
        }
    }

    // ── Biometric prompt ──────────────────────────────────────────────────────

    private lateinit var biometricPrompt: BiometricPrompt
    private var onUnlockSuccess: (() -> Unit)? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // FLAG_SECURE: hides content in the recents screen and blocks screenshots
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        setupBiometricPrompt()

        val app = application as MoneyTrackerApp
        lifecycleScope.launch(Dispatchers.IO) {
            app.container.repository.cleanupExistingDuplicates()
        }

        if (hasSmsPermission() && !app.container.userPreferences.hasInitialImportDone()) {
            enqueueInboxImport()
            app.container.userPreferences.setInitialImportDone(true)
        }

        setContent {
            val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
            val isDark = when (themeMode) {
                com.example.moneytracker.data.local.UserPreferences.THEME_LIGHT -> false
                com.example.moneytracker.data.local.UserPreferences.THEME_DARK -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            MoneyTrackerTheme(darkTheme = isDark) {
                AppContent(themeMode = themeMode)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val app = application as MoneyTrackerApp
        if (hasSmsPermission() && !app.container.userPreferences.hasInitialImportDone()) {
            enqueueInboxImport()
            app.container.userPreferences.setInitialImportDone(true)
        }
    }

    // ── Compose UI ────────────────────────────────────────────────────────────

    @Composable
    private fun AppContent(
        themeMode: String,
    ) {
        // Lock state: starts locked if lock is enabled
        val isLockEnabled by settingsViewModel.isLockEnabled.collectAsStateWithLifecycle()
        var isUnlocked by rememberSaveable { mutableStateOf(false) }

        // Show lock screen until authenticated
        if (isLockEnabled && !isUnlocked) {
            LockScreen(
                onUnlockClicked = {
                    showBiometricPrompt { isUnlocked = true }
                }
            )
            androidx.compose.runtime.LaunchedEffect(Unit) {
                showBiometricPrompt { isUnlocked = true }
            }
            return
        }

        // Check SMS permission
        val hasSmsPermission = remember(permissionDeniedOnce) {
            mutableStateOf(hasSmsPermission())
        }

        // Simple two-tab bottom navigation
        var selectedTab by rememberSaveable { mutableStateOf(0) }

        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                    )
                }
            }
        ) { innerPadding ->
            when (selectedTab) {
                0 -> {
                    if (!hasSmsPermission.value) {
                        PermissionScreen(
                            isPermanentlyDenied = permissionDeniedOnce,
                            onGrantClicked = { requestSmsPermission() },
                            onOpenSettingsClicked = { openAppSettings() },
                            modifier = Modifier.padding(innerPadding),
                        )
                    } else {
                        val userName by homeViewModel.userName.collectAsStateWithLifecycle()
                        val todaySummary by homeViewModel.todaySummary.collectAsStateWithLifecycle()
                        val monthSummary by homeViewModel.monthSummary.collectAsStateWithLifecycle()
                        val customDateSummary by homeViewModel.customDateSummary.collectAsStateWithLifecycle()
                        val customDateMs by homeViewModel.customDateMs.collectAsStateWithLifecycle()
                        HomeScreen(
                            userName = userName,
                            todaySummary = todaySummary,
                            monthSummary = monthSummary,
                            customDateSummary = customDateSummary,
                            customDateMs = customDateMs,
                            onSelectCustomDate = { homeViewModel.selectCustomDate(it) },
                            onProfileClicked = { selectedTab = 1 },
                            onAddTransaction = { amountPaise, isDebit, party, paymentMode ->
                                homeViewModel.addManualTransaction(amountPaise, isDebit, party, paymentMode)
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                        )
                    }
                }
                1 -> {
                    val userName by settingsViewModel.userName.collectAsStateWithLifecycle()
                    val isLock by settingsViewModel.isLockEnabled.collectAsStateWithLifecycle()
                    val showConfirm by settingsViewModel.showDeleteConfirm.collectAsStateWithLifecycle()
                    SettingsScreen(
                        userName = userName,
                        themeMode = themeMode,
                        isSmsPermissionGranted = hasSmsPermission.value,
                        isLockEnabled = isLock,
                        showDeleteConfirm = showConfirm,
                        onUserNameChanged = { settingsViewModel.setUserName(it) },
                        onThemeModeChanged = { settingsViewModel.setThemeMode(it) },
                        onImportClicked = { enqueueInboxImport() },
                        onLockToggled = { settingsViewModel.setLockEnabled(it) },
                        onDeleteClicked = { settingsViewModel.requestDeleteAll() },
                        onDeleteConfirmed = {
                            settingsViewModel.confirmDeleteAll {
                                // After wipe, recreate so the app starts fresh
                                recreate()
                            }
                        },
                        onDeleteDismissed = { settingsViewModel.dismissDeleteConfirm() },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    )
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun hasSmsPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) ==
                PackageManager.PERMISSION_GRANTED

    private fun requestSmsPermission() {
        requestPermissionLauncher.launch(
            arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
        )
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
        )
    }

    private fun enqueueInboxImport() {
        val request = OneTimeWorkRequestBuilder<ImportInboxWorker>().build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            ImportInboxWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun setupBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onUnlockSuccess?.invoke()
                    onUnlockSuccess = null
                }
                // On error or failure we just stay on the lock screen
            }
        )
    }

    private fun showBiometricPrompt(onSuccess: () -> Unit) {
        onUnlockSuccess = onSuccess
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock MoneyTracker")
            .setSubtitle("Use biometric or PIN to access your transactions")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        biometricPrompt.authenticate(promptInfo)
    }
}
