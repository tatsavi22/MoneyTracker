package com.example.moneytracker

import com.example.moneytracker.data.local.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class UserPreferencesTest {

    @Test
    fun `default values and updates work properly`() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = UserPreferences(context)

        // Default name is Buddy
        assertEquals("Buddy", prefs.userName.value)
        assertFalse(prefs.isLockEnabled.value)
        assertEquals(UserPreferences.THEME_SYSTEM, prefs.themeMode.value)
        assertFalse(prefs.hasInitialImportDone())

        // Initial import flag
        prefs.setInitialImportDone(true)
        assertTrue(prefs.hasInitialImportDone())

        // Update name
        prefs.setUserName("Tatsavi Tajne")
        assertEquals("Tatsavi Tajne", prefs.userName.value)

        // Toggle lock
        prefs.setLockEnabled(true)
        assertTrue(prefs.isLockEnabled.value)

        // Update theme mode
        prefs.setThemeMode(UserPreferences.THEME_DARK)
        assertEquals(UserPreferences.THEME_DARK, prefs.themeMode.value)

        prefs.setThemeMode(UserPreferences.THEME_LIGHT)
        assertEquals(UserPreferences.THEME_LIGHT, prefs.themeMode.value)

        // Setting blank reverts to default
        prefs.setUserName("   ")
        assertEquals("Buddy", prefs.userName.value)

        // Clear resets to default
        prefs.setUserName("Alex")
        prefs.setLockEnabled(true)
        prefs.setThemeMode(UserPreferences.THEME_DARK)
        prefs.clear()
        assertEquals("Buddy", prefs.userName.value)
        assertFalse(prefs.isLockEnabled.value)
        assertEquals(UserPreferences.THEME_SYSTEM, prefs.themeMode.value)
    }
}
