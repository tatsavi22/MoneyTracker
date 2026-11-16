package com.example.moneytracker.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stores user preferences such as display name, app lock state, and theme mode.
 * Fully offline, private, backed by SharedPreferences.
 */
class UserPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _userName = MutableStateFlow(
        prefs.getString(KEY_USER_NAME, DEFAULT_USER_NAME)?.ifBlank { DEFAULT_USER_NAME } ?: DEFAULT_USER_NAME
    )
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _isLockEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_LOCK_ENABLED, false)
    )
    val isLockEnabled: StateFlow<Boolean> = _isLockEnabled.asStateFlow()

    private val _themeMode = MutableStateFlow(
        prefs.getString(KEY_THEME_MODE, THEME_SYSTEM) ?: THEME_SYSTEM
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun setUserName(name: String) {
        val cleanName = name.trim().ifEmpty { DEFAULT_USER_NAME }
        prefs.edit().putString(KEY_USER_NAME, cleanName).apply()
        _userName.value = cleanName
    }

    fun setLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCK_ENABLED, enabled).apply()
        _isLockEnabled.value = enabled
    }

    fun setThemeMode(mode: String) {
        val validMode = when (mode) {
            THEME_LIGHT, THEME_DARK -> mode
            else -> THEME_SYSTEM
        }
        prefs.edit().putString(KEY_THEME_MODE, validMode).apply()
        _themeMode.value = validMode
    }

    fun hasInitialImportDone(): Boolean = prefs.getBoolean(KEY_INITIAL_IMPORT_DONE, false)

    fun setInitialImportDone(done: Boolean) {
        prefs.edit().putBoolean(KEY_INITIAL_IMPORT_DONE, done).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
        _userName.value = DEFAULT_USER_NAME
        _isLockEnabled.value = false
        _themeMode.value = THEME_SYSTEM
    }

    companion object {
        private const val PREFS_NAME = "moneytracker_user_prefs"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_LOCK_ENABLED = "lock_enabled"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_INITIAL_IMPORT_DONE = "initial_import_done"
        const val DEFAULT_USER_NAME = "Buddy"
        const val THEME_SYSTEM = "SYSTEM"
        const val THEME_LIGHT = "LIGHT"
        const val THEME_DARK = "DARK"
    }
}
