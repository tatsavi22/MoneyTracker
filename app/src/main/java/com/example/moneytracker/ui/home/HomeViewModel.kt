package com.example.moneytracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.moneytracker.data.local.UserPreferences
import com.example.moneytracker.data.repository.TransactionRepository
import com.example.moneytracker.domain.DaySummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Home screen. Collects [DaySummary] flows from the repository
 * and exposes them as [StateFlow]s for the Compose UI to collect.
 *
 * Uses [SharingStarted.WhileSubscribed] so the upstream Room flow is only active
 * while the UI is visible — saves battery when the app is in the background.
 */
class HomeViewModel(
    private val repository: TransactionRepository,
    private val userPreferences: UserPreferences? = null,
) : ViewModel() {

    /** User's display name for personalized greeting (defaults to "Buddy"). */
    val userName: StateFlow<String> = userPreferences?.userName
        ?: MutableStateFlow(UserPreferences.DEFAULT_USER_NAME)

    /**
     * Explicitly inserts a manual transaction (e.g. Cash received from a customer or cash spent).
     */
    fun addManualTransaction(
        amountPaise: Long,
        isDebit: Boolean,
        party: String?,
        paymentMode: String = "Cash",
        timestamp: Long = System.currentTimeMillis(),
        onComplete: (() -> Unit)? = null,
    ) {
        viewModelScope.launch {
            repository.addManualTransaction(
                amountPaise = amountPaise,
                isDebit = isDebit,
                party = party,
                paymentMode = paymentMode,
                timestamp = timestamp,
            )
            onComplete?.invoke()
        }
    }

    /** Summary (totals + transaction list) for today, updated live. */
    val todaySummary: StateFlow<DaySummary?> = repository.todaySummary()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /** Summary for the current calendar month, updated live. */
    val monthSummary: StateFlow<DaySummary?> = repository.thisMonthSummary()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    private val _customDateMs = MutableStateFlow<Long?>(null)
    val customDateMs: StateFlow<Long?> = _customDateMs.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val customDateSummary: StateFlow<DaySummary?> = _customDateMs
        .flatMapLatest { dateMs ->
            if (dateMs == null) flowOf(null)
            else repository.specificDateSummary(dateMs)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    fun selectCustomDate(epochMs: Long?) {
        _customDateMs.value = epochMs
    }

    // ── Factory (manual DI — no Hilt) ─────────────────────────────────────────

    class Factory(
        private val repository: TransactionRepository,
        private val userPreferences: UserPreferences? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                return HomeViewModel(repository, userPreferences) as T
            }
            throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
