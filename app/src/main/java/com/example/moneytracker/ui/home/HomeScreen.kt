package com.example.moneytracker.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.moneytracker.R
import com.example.moneytracker.domain.DaySummary
import com.example.moneytracker.domain.Transaction
import com.example.moneytracker.ui.theme.DebitColor
import com.example.moneytracker.ui.theme.MoneyIn
import com.example.moneytracker.util.BankUtils
import com.example.moneytracker.util.MoneyFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToLong

/**
 * Modern Home screen with animated interactions:
 *  - Animated rolling money counters for Spent, Received, and Net amounts
 *  - Ambient glowing gradient border on the Hero Spending Card
 *  - Tactile spring bounce on buttons, avatar, and cards
 *  - Sticky Total Spent card with dynamic elevation & size scaling while scrolling
 *  - Fluid transaction row reorder and placement animations (`animateItem()`)
 *  - Floating celebratory micro-banner when adding transactions
 *  - Floating Action Button for manual/cash transactions
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    userName: String = "Buddy",
    todaySummary: DaySummary?,
    monthSummary: DaySummary?,
    customDateSummary: DaySummary? = null,
    customDateMs: Long? = null,
    onSelectCustomDate: (Long?) -> Unit = {},
    onProfileClicked: (() -> Unit)? = null,
    onAddTransaction: (amountPaise: Long, isDebit: Boolean, party: String?, paymentMode: String) -> Unit = { _, _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedBank by rememberSaveable { mutableStateOf<String?>(null) }
    var showBankFilterDialog by rememberSaveable { mutableStateOf(false) }
    var showDatePickerDialog by rememberSaveable { mutableStateOf(false) }
    var showAddTxnDialog by rememberSaveable { mutableStateOf(false) }
    var selectedDetailTxn by remember { mutableStateOf<Transaction?>(null) }

    // Celebratory confirmation banner animation state
    var showCelebrationBanner by remember { mutableStateOf(false) }
    var celebrationText by remember { mutableStateOf("") }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-dismiss celebration banner
    LaunchedEffect(showCelebrationBanner) {
        if (showCelebrationBanner) {
            delay(2600)
            showCelebrationBanner = false
        }
    }

    // null means still loading
    val summary = when (selectedTab) {
        0 -> todaySummary
        1 -> monthSummary
        else -> customDateSummary
    }

    // Determine available banks and payment sources (e.g. Cash, BOI, Axis, etc.)
    val availableBanks = remember(summary?.transactions) {
        summary?.transactions
            ?.map { BankUtils.getBankName(it.senderId) }
            ?.distinct()
            ?.sorted() ?: emptyList()
    }

    // Bank transaction counts for badge display
    val bankTxnCounts = remember(summary?.transactions) {
        summary?.transactions
            ?.groupingBy { BankUtils.getBankName(it.senderId) }
            ?.eachCount() ?: emptyMap()
    }

    // Reset bank filter if selected bank is no longer in the active period
    LaunchedEffect(availableBanks) {
        if (selectedBank != null && !availableBanks.contains(selectedBank)) {
            selectedBank = null
        }
    }

    // Filter transactions and compute totals for the selected bank
    val filteredSummary = remember(summary, selectedBank) {
        if (summary == null) null
        else if (selectedBank == null) summary
        else {
            val txns = summary.transactions.filter { BankUtils.getBankName(it.senderId) == selectedBank }
            val debit = txns.filter { it.isDebit }.sumOf { it.amountPaise }
            val credit = txns.filter { !it.isDebit }.sumOf { it.amountPaise }
            DaySummary(
                totalDebitPaise = debit,
                totalCreditPaise = credit,
                transactions = txns,
            )
        }
    }

    val activeSummary = filteredSummary ?: summary

    // Index of the sticky total spent card in LazyColumn
    val stickyItemIndex = if (availableBanks.size > 1) 3 else 2

    // Tracks when the total spent card is sticking at the top
    val isSticky by remember(stickyItemIndex) {
        derivedStateOf {
            listState.firstVisibleItemIndex >= stickyItemIndex
        }
    }

    // ── Bank Selection Dialog ─────────────────────────────────────────────────
    if (showBankFilterDialog) {
        AlertDialog(
            onDismissRequest = { showBankFilterDialog = false },
            title = {
                Text(
                    text = "Filter by Source / Bank",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Option 1: All Sources
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedBank = null
                                showBankFilterDialog = false
                            }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selectedBank == null,
                                onClick = {
                                    selectedBank = null
                                    showBankFilterDialog = false
                                },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "All Sources",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (selectedBank == null) FontWeight.SemiBold else FontWeight.Normal,
                                ),
                            )
                        }
                        Text(
                            text = "${summary?.transactions?.size ?: 0}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    availableBanks.forEach { bank ->
                        val count = bankTxnCounts[bank] ?: 0
                        val isSelected = selectedBank == bank
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedBank = bank
                                    showBankFilterDialog = false
                                }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        selectedBank = bank
                                        showBankFilterDialog = false
                                    },
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = bank,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    ),
                                )
                            }
                            Text(
                                text = "$count",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBankFilterDialog = false }) {
                    Text("Close")
                }
            },
            dismissButton = if (selectedBank != null) {
                {
                    TextButton(onClick = {
                        selectedBank = null
                        showBankFilterDialog = false
                    }) {
                        Text("Clear Filter")
                    }
                }
            } else null
        )
    }

    // ── Material 3 Specific Date Picker Dialog ────────────────────────────────
    if (showDatePickerDialog) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = customDateMs ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { utcMs ->
                            val calUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMs }
                            val localCal = Calendar.getInstance().apply {
                                set(Calendar.YEAR, calUtc.get(Calendar.YEAR))
                                set(Calendar.MONTH, calUtc.get(Calendar.MONTH))
                                set(Calendar.DAY_OF_MONTH, calUtc.get(Calendar.DAY_OF_MONTH))
                                set(Calendar.HOUR_OF_DAY, 12)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            onSelectCustomDate(localCal.timeInMillis)
                            selectedTab = 2
                        }
                        showDatePickerDialog = false
                    }
                ) {
                    Text("Select Date")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                title = {
                    Text(
                        text = "Pick a Specific Date",
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
            )
        }
    }

    // ── Explicit Add Transaction Dialog (Cash / Manual) ───────────────────────
    if (showAddTxnDialog) {
        AddTransactionDialog(
            availableBanks = availableBanks,
            onDismiss = { showAddTxnDialog = false },
            onConfirm = { amountPaise, isDebit, party, paymentMode ->
                onAddTransaction(amountPaise, isDebit, party, paymentMode)
                showAddTxnDialog = false
                val formattedAmount = MoneyFormat.format(amountPaise)
                celebrationText = if (!isDebit) "✓ Received $formattedAmount in $paymentMode"
                                  else "✓ Paid $formattedAmount via $paymentMode"
                showCelebrationBanner = true
            }
        )
    }

    // ── Transaction Details Bottom Sheet ──────────────────────────────────────
    selectedDetailTxn?.let { txn ->
        TransactionDetailBottomSheet(
            transaction = txn,
            onDismiss = { selectedDetailTxn = null },
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when {
            summary == null -> LoadingState()
            summary.transactions.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                ) {
                    GreetingHeader(
                        userName = userName,
                        customDateMs = if (selectedTab == 2) customDateMs else null,
                        onProfileClicked = onProfileClicked,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    val customDateLabel = remember(customDateMs) {
                        if (customDateMs != null) SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(customDateMs))
                        else "Date 📅"
                    }
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = selectedTab == 0,
                            onClick = {
                                selectedTab = 0
                                onSelectCustomDate(null)
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                            label = { Text(stringResource(R.string.tab_today)) }
                        )
                        SegmentedButton(
                            selected = selectedTab == 1,
                            onClick = {
                                selectedTab = 1
                                onSelectCustomDate(null)
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                            label = { Text(stringResource(R.string.tab_this_month)) }
                        )
                        SegmentedButton(
                            selected = selectedTab == 2,
                            onClick = { showDatePickerDialog = true },
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                            label = { Text(customDateLabel) }
                        )
                    }
                    EmptyState(
                        isToday = selectedTab == 0,
                        isCustomDate = selectedTab == 2,
                        customDateLabel = customDateMs?.let { SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(it)) } ?: "",
                        monthTxnCount = monthSummary?.transactions?.size ?: 0,
                        onViewMonthClicked = {
                            selectedTab = 1
                            onSelectCustomDate(null)
                        },
                        onViewTodayClicked = {
                            selectedTab = 0
                            onSelectCustomDate(null)
                        },
                    )
                }
            }
            activeSummary != null && activeSummary.transactions.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                ) {
                    GreetingHeader(
                        userName = userName,
                        onProfileClicked = onProfileClicked,
                    )
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp),
                        ) {
                            Text(
                                text = "No transactions found for $selectedBank.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedButton(onClick = { selectedBank = null }) {
                                Text("Clear filter")
                            }
                        }
                    }
                }
            }
            activeSummary != null -> {
                // Group transactions by date for clean, clear hierarchy
                val groupedTransactions = remember(activeSummary.transactions) {
                    activeSummary.transactions.groupBy { txn ->
                        val cal = Calendar.getInstance().apply { timeInMillis = txn.timestamp }
                        val now = Calendar.getInstance()
                        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                        when {
                            cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                            cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) -> "Today"

                            cal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
                            cal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) -> "Yesterday"

                            cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) -> {
                                SimpleDateFormat("d MMMM", Locale.getDefault()).format(Date(txn.timestamp))
                            }
                            else -> {
                                SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(txn.timestamp))
                            }
                        }
                    }
                }

                // ── Main Scrollable Feed with STICKY Total Spent Card ─────────
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Item 0: Greeting Header
                    item(key = "greeting_header") {
                        GreetingHeader(
                            userName = userName,
                            customDateMs = if (selectedTab == 2) customDateMs else null,
                            onProfileClicked = onProfileClicked,
                        )
                    }

                    // Item 1: Period Segment Switch (Today / This month / Custom Date)
                    item(key = "period_switch") {
                        Spacer(modifier = Modifier.height(4.dp))
                        val customDateLabel = remember(customDateMs) {
                            if (customDateMs != null) SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(customDateMs))
                            else "Date 📅"
                        }
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            // Tab 0: Today
                            SegmentedButton(
                                selected = selectedTab == 0,
                                onClick = {
                                    selectedTab = 0
                                    onSelectCustomDate(null)
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                                label = {
                                    Text(
                                        text = stringResource(R.string.tab_today),
                                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                            )
                            // Tab 1: This month
                            SegmentedButton(
                                selected = selectedTab == 1,
                                onClick = {
                                    selectedTab = 1
                                    onSelectCustomDate(null)
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                                label = {
                                    Text(
                                        text = stringResource(R.string.tab_this_month),
                                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                            )
                            // Tab 2: Custom Specific Date
                            SegmentedButton(
                                selected = selectedTab == 2,
                                onClick = {
                                    showDatePickerDialog = true
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                                label = {
                                    Text(
                                        text = customDateLabel,
                                        fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Item 2: Bank and Cash Filter Row
                    if (availableBanks.size > 1) {
                        item(key = "filter_row") {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                item {
                                    OutlinedButton(
                                        onClick = { showBankFilterDialog = true },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(20.dp),
                                        colors = if (selectedBank != null) {
                                            ButtonDefaults.outlinedButtonColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                            )
                                        } else {
                                            ButtonDefaults.outlinedButtonColors()
                                        },
                                        modifier = Modifier.height(34.dp),
                                    ) {
                                        Text(
                                            text = if (selectedBank == null) "Filter ▾" else "$selectedBank ▾",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        )
                                        if (selectedBank != null) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "✕",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                modifier = Modifier.clickable { selectedBank = null },
                                            )
                                        }
                                    }
                                }

                                item {
                                    FilterChip(
                                        selected = selectedBank == null,
                                        onClick = { selectedBank = null },
                                        label = { Text("All (${summary.transactions.size})") },
                                    )
                                }

                                items(availableBanks) { bank ->
                                    val count = bankTxnCounts[bank] ?: 0
                                    FilterChip(
                                        selected = selectedBank == bank,
                                        onClick = {
                                            selectedBank = if (selectedBank == bank) null else bank
                                        },
                                        label = { Text("$bank ($count)") },
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }

                    // ── STICKY HEADER: TOTAL SPENT CARD ───────────────────────
                    // Pinned firmly at the top while scrolling transactions!
                    stickyHeader(key = "sticky_total_spent_card") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.background,
                        ) {
                            Box(modifier = Modifier.padding(vertical = 4.dp)) {
                                HeroSpendingCard(
                                    summary = activeSummary,
                                    selectedTab = selectedTab,
                                    customDateMs = customDateMs,
                                    selectedBank = selectedBank,
                                    isSticky = isSticky,
                                    onClickWhenSticky = if (isSticky) {
                                        {
                                            coroutineScope.launch {
                                                listState.animateScrollToItem(0)
                                            }
                                        }
                                    } else null
                                )
                            }
                        }
                    }

                    groupedTransactions.forEach { (dateHeader, txnsInDay) ->
                        item(key = "header_$dateHeader") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bouncyClick {
                                        if (selectedTab != 2) {
                                            txnsInDay.firstOrNull()?.let { txn ->
                                                onSelectCustomDate(txn.timestamp)
                                                selectedTab = 2
                                            }
                                        }
                                    }
                                    .padding(top = 12.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = dateHeader.uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.6.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                )
                                val dayDebit = txnsInDay.filter { it.isDebit }.sumOf { it.amountPaise }
                                val dayCredit = txnsInDay.filter { !it.isDebit }.sumOf { it.amountPaise }
                                val summaryLabel = when {
                                    dayDebit > 0 && dayCredit > 0 -> "Debited ${MoneyFormat.format(dayDebit)} • Received ${MoneyFormat.format(dayCredit)}"
                                    dayDebit > 0 -> "Debited ${MoneyFormat.format(dayDebit)}"
                                    dayCredit > 0 -> "Received ${MoneyFormat.format(dayCredit)}"
                                    else -> ""
                                }
                                if (summaryLabel.isNotEmpty()) {
                                    Text(
                                        text = summaryLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                    )
                                }
                            }
                        }

                        items(items = txnsInDay, key = { it.id }) { txn ->
                            TransactionRow(
                                transaction = txn,
                                onClick = { selectedDetailTxn = txn },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }

                    // FAB Clearance Spacer at bottom
                    item(key = "bottom_spacer") {
                        Spacer(modifier = Modifier.height(84.dp))
                    }
                }
            }
        }

        // ── Celebratory Floating Toast / Banner Animation ────────────────────
        AnimatedVisibility(
            visible = showCelebrationBanner,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
            ) + fadeIn(animationSpec = tween(200)),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
            ) + fadeOut(animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MoneyIn,
                shadowElevation = 6.dp,
            ) {
                Text(
                    text = celebrationText,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }

        // ── Floating Action Button for Adding Cash / Manual Transaction ───────
        FloatingActionButton(
            onClick = { showAddTxnDialog = true },
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp, end = 16.dp)
                .bouncyClick { showAddTxnDialog = true },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "+",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Add Entry",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

/**
 * Top header greeting: "Hi, Buddy 👋" with current day/date and tactile profile avatar badge.
 */
@Composable
private fun GreetingHeader(
    userName: String,
    customDateMs: Long? = null,
    onProfileClicked: (() -> Unit)? = null,
) {
    val formattedDate = remember(customDateMs) {
        if (customDateMs != null) {
            SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date(customDateMs))
        } else {
            SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date())
        }
    }
    val subtitle = remember(customDateMs, formattedDate) {
        if (customDateMs != null) "$formattedDate • Specific Date View"
        else "$formattedDate • Financial Overview"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Hi, $userName 👋",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                ),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            )
        }

        // Tactile Spring Profile Avatar badge
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .size(46.dp)
                .bouncyClick { onProfileClicked?.invoke() },
            shadowElevation = 2.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = (userName.trim().firstOrNull() ?: 'T').uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

/**
 * Animated rolling money counter that rolls smoothly between numbers when switching periods or filtering.
 */
@Composable
private fun AnimatedMoneyCounter(
    targetPaise: Long,
    textStyle: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    isSigned: Boolean = false,
    isDebit: Boolean = false,
) {
    val animatable = remember { Animatable(targetPaise.toFloat()) }
    LaunchedEffect(targetPaise) {
        animatable.animateTo(
            targetValue = targetPaise.toFloat(),
            animationSpec = tween(
                durationMillis = 550,
                easing = FastOutSlowInEasing,
            )
        )
    }
    val currentPaise = animatable.value.roundToLong()
    val formatted = if (isSigned) {
        MoneyFormat.formatSigned(paise = kotlin.math.abs(currentPaise), isDebit = isDebit)
    } else {
        MoneyFormat.format(currentPaise)
    }

    Text(
        text = formatted,
        style = textStyle,
        color = color,
        modifier = modifier,
    )
}

/**
 * Tactile bouncy press effect modifier using spring physics.
 */
fun Modifier.bouncyClick(onClick: () -> Unit): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "bouncyScale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    isPressed = true
                    tryAwaitRelease()
                    isPressed = false
                },
                onTap = { onClick() }
            )
        }
}

/**
 * Cash Flow overview hero card with dual Debited (Money Out) and Received (Money In)
 * metrics, animated counters, ambient glow border, and sticky transition.
 */
@Composable
private fun HeroSpendingCard(
    summary: DaySummary,
    selectedTab: Int = 0,
    customDateMs: Long? = null,
    selectedBank: String? = null,
    isSticky: Boolean = false,
    onClickWhenSticky: (() -> Unit)? = null,
) {
    val cardElevation by animateDpAsState(
        targetValue = if (isSticky) 6.dp else 2.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "cardElevation",
    )
    val cardPaddingVertical by animateDpAsState(
        targetValue = if (isSticky) 10.dp else 16.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "cardPaddingVertical",
    )

    // Ambient shimmering gradient border
    val infiniteTransition = rememberInfiniteTransition(label = "heroGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowAlpha"
    )

    val borderBrush = Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha),
            MoneyIn.copy(alpha = glowAlpha * 0.75f),
            MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha * 0.35f),
        )
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClickWhenSticky != null) { onClickWhenSticky?.invoke() },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
        border = BorderStroke(1.2.dp, borderBrush),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = cardPaddingVertical,
            )
        ) {
            // Header Row: Period Title + optional Bank Filter Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val headerTitle = when {
                    selectedBank != null && selectedTab == 0 -> "TODAY • $selectedBank"
                    selectedBank != null && selectedTab == 2 -> "CASH FLOW • $selectedBank"
                    selectedBank != null -> "THIS MONTH • $selectedBank"
                    selectedTab == 0 -> "TODAY'S CASH FLOW"
                    selectedTab == 2 && customDateMs != null -> {
                        val dStr = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(customDateMs)).uppercase()
                        "$dStr CASH FLOW"
                    }
                    else -> "THIS MONTH'S CASH FLOW"
                }
                Text(
                    text = headerTitle,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
                if (selectedBank != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = selectedBank,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Two equal, clean financial metrics: DEBITED vs RECEIVED
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 1. Debited (Money Out) Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.weight(1f),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = DebitColor.copy(alpha = 0.12f),
                                modifier = Modifier.size(20.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "↗",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Black,
                                            fontSize = 11.sp
                                        ),
                                        color = DebitColor,
                                    )
                                }
                            }
                            Text(
                                text = "Debited",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        AnimatedMoneyCounter(
                            targetPaise = summary.totalDebitPaise,
                            textStyle = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = if (isSticky) 18.sp else 22.sp,
                                letterSpacing = (-0.5).sp,
                            ),
                            color = DebitColor,
                        )

                        val debitCount = summary.transactions.count { it.isDebit }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (debitCount == 1) "1 payment" else "$debitCount payments",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Normal,
                                fontSize = 10.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        )
                    }
                }

                // 2. Received (Money In) Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MoneyIn.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, MoneyIn.copy(alpha = 0.25f)),
                    modifier = Modifier.weight(1f),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MoneyIn.copy(alpha = 0.15f),
                                modifier = Modifier.size(20.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "↙",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Black,
                                            fontSize = 11.sp
                                        ),
                                        color = MoneyIn,
                                    )
                                }
                            }
                            Text(
                                text = "Received",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MoneyIn,
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        AnimatedMoneyCounter(
                            targetPaise = summary.totalCreditPaise,
                            textStyle = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = if (isSticky) 18.sp else 22.sp,
                                letterSpacing = (-0.5).sp,
                            ),
                            color = MoneyIn,
                        )

                        val creditCount = summary.transactions.count { !it.isDebit }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (creditCount == 1) "1 receipt" else "$creditCount receipts",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Normal,
                                fontSize = 10.sp,
                            ),
                            color = MoneyIn.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Modern, elevated transaction row with high-definition styling:
 *  - Bank monogram avatar
 *  - Clean, human-readable merchant title via BankUtils.formatPartyTitle
 *  - Category pills for UPI / Cash
 *  - Clean right-hand column: Amount on top, time pill directly below
 *  - Tactile bouncy click physics opening full transaction details
 */
@Composable
private fun TransactionRow(
    transaction: Transaction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val timeStr = remember(transaction.timestamp) { timeFormat.format(Date(transaction.timestamp)) }

    val cleanTitle = remember(transaction.party, transaction.isDebit) {
        BankUtils.formatPartyTitle(transaction.party, transaction.isDebit)
    }
    val bankAndAccount = remember(transaction.senderId, transaction.accountLast4) {
        BankUtils.formatBankAndAccount(transaction.senderId, transaction.accountLast4)
    }

    val isUpi = transaction.party?.contains("@") == true

    Card(
        modifier = modifier
            .fillMaxWidth()
            .bouncyClick { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ── Left: Avatar (Bank Initial + Directional Tint) ──
            Box(
                modifier = Modifier.size(44.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(13.dp),
                    color = if (transaction.isDebit) {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
                    } else {
                        MoneyIn.copy(alpha = 0.14f)
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = BankUtils.getBankInitial(transaction.senderId),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (transaction.isDebit) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MoneyIn
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // ── Middle: Payee Title & Bank / Source Info ──
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = cleanTitle,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isUpi) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        ) {
                            Text(
                                text = "UPI",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    } else if (transaction.senderId.contains("Cash", ignoreCase = true)) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MoneyIn.copy(alpha = 0.12f),
                        ) {
                            Text(
                                text = "Cash",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                ),
                                color = MoneyIn,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = bankAndAccount,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Normal
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // ── Right: Amount on Top & Time Pill Directly Below ──
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center,
            ) {
                if (!transaction.isDebit) {
                    // Credit / Income: bold emerald green
                    Text(
                        text = MoneyFormat.formatSigned(transaction.amountPaise, false),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MoneyIn,
                    )
                } else {
                    // Debit / Expense: clean bold dark slate
                    Text(
                        text = MoneyFormat.formatSigned(transaction.amountPaise, true),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = DebitColor,
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                // Time displayed cleanly in a subtle pill right below the amount
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                ) {
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            letterSpacing = 0.2.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/**
 * Bottom Sheet displaying full transaction details with copy actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionDetailBottomSheet(
    transaction: Transaction,
    onDismiss: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    val fullDateFormat = remember {
        SimpleDateFormat("EEEE, d MMMM yyyy 'at' h:mm:ss a", Locale.getDefault())
    }
    val fullDateStr = remember(transaction.timestamp) {
        fullDateFormat.format(Date(transaction.timestamp))
    }

    val cleanParty = remember(transaction.party, transaction.isDebit) {
        BankUtils.formatPartyTitle(transaction.party, transaction.isDebit)
    }
    val bankName = remember(transaction.senderId) {
        BankUtils.getBankName(transaction.senderId)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Direction Icon & Amount Header
            Surface(
                shape = CircleShape,
                color = if (transaction.isDebit) MaterialTheme.colorScheme.surfaceVariant
                        else MoneyIn.copy(alpha = 0.15f),
                modifier = Modifier.size(64.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (transaction.isDebit) "↗" else "↙",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (transaction.isDebit) MaterialTheme.colorScheme.onSurfaceVariant else MoneyIn,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = MoneyFormat.formatSigned(transaction.amountPaise, transaction.isDebit),
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = if (transaction.isDebit) DebitColor else MoneyIn,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (transaction.isDebit) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        else MoneyIn.copy(alpha = 0.12f),
            ) {
                Text(
                    text = if (transaction.isDebit) "Money Sent / Debited" else "Money Received / Credited",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (transaction.isDebit) MaterialTheme.colorScheme.onSurfaceVariant else MoneyIn,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            Spacer(modifier = Modifier.height(18.dp))

            // Detail items
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DetailRow(
                    label = if (transaction.isDebit) "Paid to" else "Received from",
                    value = cleanParty,
                    subValue = if (!transaction.party.isNullOrBlank() && transaction.party.contains("@")) transaction.party else null,
                    showCopy = !transaction.party.isNullOrBlank(),
                    onCopy = {
                        transaction.party?.let {
                            clipboardManager.setText(AnnotatedString(it))
                            copied = true
                        }
                    },
                    copied = copied,
                )

                DetailRow(
                    label = "Bank / Account",
                    value = if (!transaction.accountLast4.isNullOrBlank()) {
                        "$bankName (A/c ••${transaction.accountLast4})"
                    } else {
                        bankName
                    },
                )

                DetailRow(
                    label = "Date & Time",
                    value = fullDateStr,
                )

                DetailRow(
                    label = "Source / Alert",
                    value = transaction.senderId,
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    subValue: String? = null,
    showCopy: Boolean = false,
    onCopy: (() -> Unit)? = null,
    copied: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subValue != null) {
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = subValue,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
        if (showCopy && onCopy != null) {
            OutlinedButton(
                onClick = onCopy,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(30.dp),
            ) {
                Text(
                    text = if (copied) "Copied ✓" else "Copy",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

/**
 * Dialog for explicitly recording cash or manual transactions (e.g. cash received from a customer or cash spent).
 */
@Composable
private fun AddTransactionDialog(
    availableBanks: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (amountPaise: Long, isDebit: Boolean, party: String?, paymentMode: String) -> Unit,
) {
    var isDebit by remember { mutableStateOf(false) } // Default to false (Customer paid / Income) per user's request
    var amountText by remember { mutableStateOf("") }
    var partyText by remember { mutableStateOf("") }
    var paymentMode by remember { mutableStateOf("Cash") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add Transaction",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // 1. Transaction Type Toggle: Income (Received) vs Expense (Spent)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !isDebit,
                        onClick = { isDebit = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MoneyIn.copy(alpha = 0.15f),
                            activeContentColor = MoneyIn,
                        ),
                        label = {
                            Text(
                                text = "↓ Income / Cash In",
                                fontWeight = if (!isDebit) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp,
                            )
                        },
                    )
                    SegmentedButton(
                        selected = isDebit,
                        onClick = { isDebit = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            activeContentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        label = {
                            Text(
                                text = "↑ Expense / Spent",
                                fontWeight = if (isDebit) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp,
                            )
                        },
                    )
                }

                // 2. Amount Input Field
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        errorMessage = null
                    },
                    label = { Text("Amount (₹)") },
                    placeholder = { Text("0.00") },
                    prefix = {
                        Text(
                            text = "₹ ",
                            fontWeight = FontWeight.Bold,
                            color = if (isDebit) DebitColor else MoneyIn,
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorMessage != null,
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                // 3. Payee / Description / Note Field
                OutlinedTextField(
                    value = partyText,
                    onValueChange = { partyText = it },
                    label = { Text("Description / Customer Note") },
                    placeholder = {
                        Text(if (!isDebit) "e.g. Customer paid in cash" else "e.g. Groceries / Tea / Petrol")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // 4. Mode / Source selection (Cash / Bank)
                Column {
                    Text(
                        text = "Payment Mode",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val modes = listOf("Cash") + availableBanks.filter { it != "Cash" }
                        items(modes) { mode ->
                            FilterChip(
                                selected = paymentMode == mode,
                                onClick = { paymentMode = mode },
                                label = { Text(mode) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val raw = amountText.trim()
                    val amountDouble = raw.toDoubleOrNull()
                    if (amountDouble == null || amountDouble <= 0.0) {
                        errorMessage = "Please enter a valid amount greater than 0"
                        return@Button
                    }
                    val amountPaise = (amountDouble * 100.0).roundToLong()
                    val party = partyText.trim().ifEmpty {
                        if (!isDebit) "Cash Received" else "Cash Payment"
                    }
                    onConfirm(amountPaise, isDebit, party, paymentMode)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!isDebit) MoneyIn else MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text("Save Entry", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyState(
    isToday: Boolean,
    isCustomDate: Boolean = false,
    customDateLabel: String = "",
    monthTxnCount: Int = 0,
    onViewMonthClicked: (() -> Unit)? = null,
    onViewTodayClicked: (() -> Unit)? = null,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            val emptyMsg = when {
                isCustomDate -> "No transactions recorded on $customDateLabel."
                isToday -> stringResource(R.string.empty_today)
                else -> stringResource(R.string.empty_month)
            }
            Text(
                text = emptyMsg,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )
            if (isToday && monthTxnCount > 0 && onViewMonthClicked != null) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = onViewMonthClicked) {
                    Text("View This Month ($monthTxnCount transactions)")
                }
            } else if (isCustomDate && onViewTodayClicked != null) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = onViewTodayClicked) {
                    Text("Back to Today")
                }
            }
        }
    }
}
