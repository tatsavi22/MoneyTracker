package com.example.moneytracker

import android.app.Application

/**
 * Custom [Application] class. Creates the [AppContainer] (manual DI root)
 * so all components share the same database and repository instances.
 *
 * Registered in AndroidManifest.xml as android:name=".MoneyTrackerApp".
 */
class MoneyTrackerApp : Application() {

    /** Accessed by Workers, ViewModels (via the factory), and the wipe flow. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        try {
            System.loadLibrary("sqlcipher")
        } catch (_: UnsatisfiedLinkError) {
            // Handled for JVM testing
        }
        container = AppContainer(this)
    }
}
