package com.example.moneytracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.moneytracker.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Settings screen.
 *
 * Keeps UI state for:
 *  - User display name
 *  - Lock on/off toggle (persisted)
 *  - Delete-all confirmation dialog visibility
 *  - Whether an import is in progress
 */
class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val userName: StateFlow<String> = container.userPreferences.userName

    val isLockEnabled: StateFlow<Boolean> = container.userPreferences.isLockEnabled

    val themeMode: StateFlow<String> = container.userPreferences.themeMode

    private val _showDeleteConfirm = MutableStateFlow(false)
    val showDeleteConfirm: StateFlow<Boolean> = _showDeleteConfirm.asStateFlow()

    fun setUserName(name: String) {
        container.userPreferences.setUserName(name)
    }

    fun setLockEnabled(enabled: Boolean) {
        container.userPreferences.setLockEnabled(enabled)
    }

    fun setThemeMode(mode: String) {
        container.userPreferences.setThemeMode(mode)
    }

    fun requestDeleteAll() {
        _showDeleteConfirm.value = true
    }

    fun dismissDeleteConfirm() {
        _showDeleteConfirm.value = false
    }

    /**
     * Permanently deletes all transaction data, user preferences, and resets the DB key.
     * After this the user is returned to the empty / first-launch state.
     */
    fun confirmDeleteAll(onComplete: () -> Unit) {
        viewModelScope.launch {
            container.repository.deleteAll()
            // Passphrase cleared; next DB open generates a new one
            container.wipeData()
            _showDeleteConfirm.value = false
            onComplete()
        }
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                return SettingsViewModel(container) as T
            }
            throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
