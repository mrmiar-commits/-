package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.*
import com.example.ui.components.MinimalCard
import com.example.ui.components.MinimalSectionHeader
import com.example.ui.components.ZarnegarEmptyState
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalZarnegarColors
import com.example.utils.Formatters
import com.example.utils.InvoicePrinter
import com.example.viewmodel.ZarnegarViewModel

@Composable
fun TransactionsAndAccountsScreen(
    viewModel: ZarnegarViewModel,
    initialSubTab: Int = 0,
    openAddTxTrigger: Int = 0,
    onConsumeAddTxTrigger: () -> Unit = {}
) {
    val context = LocalContext.current
    val ext = LocalZarnegarColors.current

    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val inventory by viewModel.inventory.collectAsStateWithLifecycle()
    val sellers by viewModel.sellers.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val marketState by viewModel.marketState.collectAsStateWithLifecycle()

    val snapshot = (marketState as? UiState.Success)?.data ?: MarketSnapshot()
    val gold18kRate = snapshot.gold18kGramToman.coerceAtLeast(1.0)
    val usdRate = snapshot.usdToToman

    var selectedTab by remember(initialSubTab) { mutableIntStateOf(initialSubTab.coerceIn(0, 2)) }
    var showAddTxDialog by remember { mutableStateOf(false) }
    var showAddAccountDialog by remember { mutableStateOf(false) }
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var txFilter by remember { mutableStateOf<TxType?>(null) }

    LaunchedEffect(openAddTxTrigger) {
        if (openAddTxTrigger > 0) {
            selectedTab = 0
            showAddTxDialog = true
            onConsumeAddTxTrigger()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    when (selectedTab) {
                        0 -> showAddTxDialog = true
                        1 -> showAddAccountDialog = true
                        else -> showAddExpenseDialog = true
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("accounts_screen_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    when (selectedTab) {
                        0 -> "تراکنش جدید"
                        1 -> "افزودن حساب / کارت"
                        else -> "ثبت هزینه مغازه"
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Minimal Segmented Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("تراکنش‌ها", "حساب و کارت‌ها", "مخارج مغازه").forEachIndexed { idx, title ->
                    val active = selectedTab == idx
                    Surface(
                        onClick = { selectedTab = idx },
                        shape = RoundedCornerShape(12.dp),
                        color = if (active) MaterialTheme.colorScheme.primary else ext.card,
                        border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary else ext.border),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .testTag("finance_tab_$idx")
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            when (selectedTab) {
                0 -> {
                    // Transactions Tab
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 10.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = txFilter == null,
                                onClick = { txFilter = null },
                                label = { Text("همه") }
                            )
                        }
                        items(TxType.entries) { type ->
                            FilterChip(
                                selected = txFilter == type,
                                onClick = { txFilter = if (txFilter == type) null else type },
                                label = { Text(type.labelFa) }
                            )
                        }
                    }

                    val filtered = remember(transactions, txFilter) {
                        if (txFilter == null) transactions else transactions.filter { it.txTypeEnum == txFilter }
                    }

                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 92.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (filtered.isEmpty()) {
                            item {
                                ZarnegarEmptyState(
                                    title = "تراکنشی یافت نشد",
                                    subtitle = "فروش روزانه، خرید طلا و انتقال میان حساب‌های مغازه را از دکمه پایین ثبت کنید.",
                                    icon = Icons.AutoMirrored.Outlined.ReceiptLong
                                )
                            }
                        } else {
                            items(filtered, key = { it.id }) { tx ->
                                MinimalTransactionRow(
                                    tx = tx,
                                    onPrint = {
                                        InvoicePrinter.printTransactionInvoice(context, settings.shopName, settings.ownerName, tx)
                                    }
                                )
                            }
                        }
                    }
                }

                1 -> {
                    // Bank Accounts, Personal Cards & Petty Cash with Gold Gram Equivalent
                    val totalToman = accounts.sumOf { it.balanceToman }
                    val totalUsd = accounts.sumOf { it.balanceUsd }
                    val totalGoldEquiv = accounts.sumOf { it.goldEquivalent18kGrams(gold18kRate, usdRate) }

                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 92.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            MinimalCard(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            ) {
                                Text(
                                    text = "جمع کل حساب‌ها، کارت شخصی و تنخواه",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Column {
                                        Text(
                                            text = Formatters.formatToman(totalToman),
                                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (totalUsd > 0.0) {
                                            Text(
                                                text = "موجودی دلاری: ${Formatters.formatUsd(totalUsd)}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = ext.info
                                            )
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "معادل گرمی طلای ۱۸ عیار",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ext.mutedText
                                        )
                                        Text(
                                            text = Formatters.formatGrams(totalGoldEquiv),
                                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        if (accounts.isEmpty()) {
                            item {
                                ZarnegarEmptyState(
                                    title = "حسابی تعریف نشده است",
                                    subtitle = "کارت بانکی مغازه، کارت شخصی، تنخواه و گاوصندوق را با معادل گرمی طلا تعریف کنید.",
                                    icon = Icons.Outlined.AccountBalance
                                )
                            }
                        } else {
                            items(accounts, key = { it.id }) { acc ->
                                val goldEquiv = acc.goldEquivalent18kGrams(gold18kRate, usdRate)
                                MinimalCard {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = acc.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Surface(
                                                    color = ext.elevated,
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = acc.typeEnum.labelFa,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = ext.mutedText,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }
                                                if (!acc.accountNumber.isNullOrBlank()) {
                                                    Text(
                                                        text = Formatters.toPersianDigits(acc.accountNumber),
                                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                                        color = ext.mutedText
                                                    )
                                                }
                                            }
                                        }
                                        IconButton(onClick = { viewModel.deleteShopAccount(acc.id) }) {
                                            Icon(
                                                Icons.Outlined.DeleteOutline,
                                                contentDescription = "حذف حساب",
                                                tint = ext.mutedText
                                            )
                                        }
                                    }

                                    HorizontalDivider(color = ext.border)

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("مانده نقدی", style = MaterialTheme.typography.labelSmall, color = ext.mutedText)
                                            Text(
                                                text = Formatters.formatToman(acc.balanceToman),
                                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("معادل طلای ۱۸ عیار", style = MaterialTheme.typography.labelSmall, color = ext.mutedText)
                                            Text(
                                                text = Formatters.formatGrams(goldEquiv),
                                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    if (acc.balanceUsd != 0.0 || acc.goldGrams18k != 0.0) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "طلای مستقیم حساب: ${Formatters.formatGrams(acc.goldGrams18k)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ext.amber
                                            )
                                            Text(
                                                text = "دلار: ${Formatters.formatUsd(acc.balanceUsd)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ext.info
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    // Shop Expenses Tab
                    val totalExpensesToman = expenses.sumOf { it.amountToman }
                    val totalExpensesGold = if (gold18kRate > 0) totalExpensesToman / gold18kRate else 0.0

                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 92.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            MinimalCard(
                                containerColor = ext.lossContainer,
                                borderColor = ext.loss.copy(alpha = 0.3f)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("مجموع مخارج مغازه", style = MaterialTheme.typography.labelMedium, color = ext.loss)
                                        Text(
                                            text = Formatters.formatToman(totalExpensesToman),
                                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                                            color = ext.loss,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("معادل گرمی طلا", style = MaterialTheme.typography.labelSmall, color = ext.mutedText)
                                        Text(
                                            text = Formatters.formatGrams(totalExpensesGold),
                                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        if (expenses.isEmpty()) {
                            item {
                                ZarnegarEmptyState(
                                    title = "هزینه‌ای برای مغازه ثبت نشده است",
                                    subtitle = "اجاره، قبوض، حقوق، پذیرایی، تعمیرات و مخارج تنخواه را ثبت کنید تا سود خالص واقعی محاسبه شود.",
                                    icon = Icons.Outlined.Payments
                                )
                            }
                        } else {
                            items(expenses, key = { it.id }) { exp ->
                                MinimalCard {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text(
                                                text = exp.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "${exp.categoryEnum.labelFa} • از ${exp.accountName}${if (!exp.sellerName.isNullOrBlank()) " • توسط ${exp.sellerName}" else ""}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = ext.mutedText
                                            )
                                            Text(
                                                text = Formatters.formatMillis(exp.timestampMillis),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ext.mutedText
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = Formatters.formatToman(exp.amountToman, false),
                                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                                color = ext.loss,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = Formatters.formatGrams(exp.goldEquivalentGrams),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            IconButton(
                                                onClick = { viewModel.deleteExpense(exp.id) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Outlined.DeleteOutline,
                                                    contentDescription = "حذف هزینه",
                                                    tint = ext.mutedText,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddTxDialog) {
        AddTransactionDialog(
            viewModel = viewModel,
            accounts = accounts,
            customers = customers,
            inventory = inventory,
            sellers = sellers,
            snapshot = snapshot,
            onDismiss = { showAddTxDialog = false },
            onSave = { tx ->
                viewModel.recordTransaction(tx)
                showAddTxDialog = false
            }
        )
    }

    if (showAddAccountDialog) {
        AddAccountDialog(
            gold18kRate = gold18kRate,
            usdRate = usdRate,
            onDismiss = { showAddAccountDialog = false },
            onSave = { name, type, toman, usd, grams, num ->
                viewModel.addShopAccount(name, type, toman, usd, grams, num)
                showAddAccountDialog = false
            }
        )
    }

    if (showAddExpenseDialog) {
        AddExpenseDialog(
            accounts = accounts,
            sellers = sellers,
            gold18kRate = gold18kRate,
            onDismiss = { showAddExpenseDialog = false },
            onSave = { title, cat, amount, acc, sellerName, notes ->
                viewModel.addExpense(title, cat, amount, acc, sellerName, notes)
                showAddExpenseDialog = false
            }
        )
    }
}

@Composable
private fun AddAccountDialog(
    gold18kRate: Double,
    usdRate: Double,
    onDismiss: () -> Unit,
    onSave: (String, AccountType, Double, Double, Double, String?) -> Unit
) {
    val ext = LocalZarnegarColors.current
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(AccountType.PERSONAL_CARD) }
    var tomanStr by remember { mutableStateOf("") }
    var usdStr by remember { mutableStateOf("0") }
    var gramsStr by remember { mutableStateOf("0") }
    var cardNumber by remember { mutableStateOf("") }

    val previewGoldEquiv = remember(tomanStr, usdStr, gramsStr, gold18kRate, usdRate) {
        val acc = ShopAccount(
            balanceToman = Formatters.parseDoubleSafe(tomanStr),
            balanceUsd = Formatters.parseDoubleSafe(usdStr),
            goldGrams18k = Formatters.parseDoubleSafe(gramsStr)
        )
        acc.goldEquivalent18kGrams(gold18kRate, usdRate)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ext.card,
        title = { Text("افزودن حساب / کارت بانکی / تنخواه", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("عنوان حساب یا کارت (مثلاً کارت ملت شخصی یا تنخواه)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("account_name_input")
                )
                Text("نوع حساب:", style = MaterialTheme.typography.labelMedium, color = ext.mutedText)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(AccountType.entries) { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.labelFa) }
                        )
                    }
                }
                OutlinedTextField(
                    value = cardNumber,
                    onValueChange = { cardNumber = it },
                    label = { Text("شماره کارت / حساب (اختیاری)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tomanStr,
                    onValueChange = { tomanStr = it },
                    label = { Text("موجودی نقدی (تومان)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("account_toman_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = usdStr,
                        onValueChange = { usdStr = it },
                        label = { Text("موجودی دلار ($)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = gramsStr,
                        onValueChange = { gramsStr = it },
                        label = { Text("طلای مستقیم (گرم)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("معادل گرمی طلای ۱۸ عیار:", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = Formatters.formatGrams(previewGoldEquiv),
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(
                            name.trim(),
                            selectedType,
                            Formatters.parseDoubleSafe(tomanStr),
                            Formatters.parseDoubleSafe(usdStr),
                            Formatters.parseDoubleSafe(gramsStr),
                            cardNumber.trim().takeIf { it.isNotEmpty() }
                        )
                    }
                },
                modifier = Modifier.testTag("save_account_button")
            ) {
                Text("ثبت حساب")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

@Composable
private fun AddExpenseDialog(
    accounts: List<ShopAccount>,
    sellers: List<ShopSeller>,
    gold18kRate: Double,
    onDismiss: () -> Unit,
    onSave: (String, ExpenseCategory, Double, ShopAccount?, String?, String?) -> Unit
) {
    val ext = LocalZarnegarColors.current
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ExpenseCategory.SUPPLIES) }
    var amountStr by remember { mutableStateOf("") }
    var selectedAccount by remember {
        mutableStateOf(accounts.firstOrNull { it.typeEnum == AccountType.PETTY_CASH } ?: accounts.firstOrNull())
    }
    var selectedSeller by remember { mutableStateOf<ShopSeller?>(null) }
    var notes by remember { mutableStateOf("") }

    val amount = Formatters.parseDoubleSafe(amountStr)
    val goldEq = if (gold18kRate > 0) amount / gold18kRate else 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ext.card,
        title = { Text("ثبت هزینه جدید مغازه", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("شرح هزینه (مثلاً قبض برق یا پیک)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("expense_title_input")
                )
                Text("دسته‌بندی هزینه:", style = MaterialTheme.typography.labelMedium, color = ext.mutedText)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ExpenseCategory.entries) { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.labelFa) }
                        )
                    }
                }
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("مبلغ هزینه (تومان)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("expense_amount_input")
                )
                if (amount > 0) {
                    Text(
                        text = "معادل طلای ۱۸ عیار: ${Formatters.formatGrams(goldEq)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (accounts.isNotEmpty()) {
                    Text("برداشت از حساب / تنخواه:", style = MaterialTheme.typography.labelMedium, color = ext.mutedText)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(accounts) { acc ->
                            FilterChip(
                                selected = selectedAccount?.id == acc.id,
                                onClick = { selectedAccount = acc },
                                label = { Text(acc.name) }
                            )
                        }
                    }
                }
                if (sellers.isNotEmpty()) {
                    Text("ثبت‌کننده / فروشنده (اختیاری):", style = MaterialTheme.typography.labelMedium, color = ext.mutedText)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(
                                selected = selectedSeller == null,
                                onClick = { selectedSeller = null },
                                label = { Text("مدیر مغازه") }
                            )
                        }
                        items(sellers) { s ->
                            FilterChip(
                                selected = selectedSeller?.id == s.id,
                                onClick = { selectedSeller = s },
                                label = { Text(s.name) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("توضیحات تکمیلی (اختیاری)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && amount > 0) {
                        onSave(title.trim(), category, amount, selectedAccount, selectedSeller?.name, notes.trim().takeIf { it.isNotEmpty() })
                    }
                },
                modifier = Modifier.testTag("save_expense_button")
            ) {
                Text("ثبت هزینه")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

@Composable
private fun AddTransactionDialog(
    viewModel: ZarnegarViewModel,
    accounts: List<ShopAccount>,
    customers: List<Customer>,
    inventory: List<InventoryItem>,
    sellers: List<ShopSeller>,
    snapshot: MarketSnapshot,
    onDismiss: () -> Unit,
    onSave: (GoldTransaction) -> Unit
) {
    val ext = LocalZarnegarColors.current
    var txType by remember { mutableStateOf(TxType.SALE) }
    var category by remember { mutableStateOf(GoldCategory.NEW_GOLD) }
    var selectedAccount by remember { mutableStateOf(accounts.firstOrNull()) }
    var targetAccount by remember { mutableStateOf(accounts.getOrNull(1)) }
    var selectedCustomer by remember { mutableStateOf<Customer?>(null) }
    var selectedSeller by remember { mutableStateOf<ShopSeller?>(null) }
    var selectedInventory by remember { mutableStateOf<InventoryItem?>(null) }
    var paymentMethod by remember { mutableStateOf(PaymentMethod.POS) }

    var weightStr by remember { mutableStateOf("1.5") }
    var karatStr by remember { mutableStateOf("750") }
    var unitPriceStr by remember { mutableStateOf(snapshot.gold18kGramToman.toLong().toString()) }
    var wageStr by remember { mutableStateOf("7") }
    var profitStr by remember { mutableStateOf("7") }
    var taxStr by remember { mutableStateOf("9") }
    var manualAmountStr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    LaunchedEffect(category) {
        karatStr = category.defaultKarat.toString()
        unitPriceStr = when (category) {
            GoldCategory.USD_CURRENCY -> snapshot.usdToToman.toLong().toString()
            GoldCategory.BULLION -> snapshot.gold24kGramToman.toLong().toString()
            else -> snapshot.gold18kGramToman.toLong().toString()
        }
        if (category == GoldCategory.USED_NO_WAGE || category == GoldCategory.MELTED_GOLD) {
            wageStr = "0"
            taxStr = "0"
        } else if (category == GoldCategory.NEW_GOLD) {
            wageStr = "7"
            taxStr = "9"
        }
    }

    val isTransfer = txType == TxType.TRANSFER_FROM_SHOP || txType == TxType.TRANSFER_TO_SHOP
    val preview = remember(category, weightStr, karatStr, unitPriceStr, wageStr, profitStr, taxStr, selectedInventory) {
        viewModel.calculateSaleBreakdown(
            category = category,
            weightOrAmount = Formatters.parseDoubleSafe(weightStr),
            karat = karatStr.toIntOrNull() ?: 750,
            unitPriceToman = Formatters.parseDoubleSafe(unitPriceStr),
            wagePercent = Formatters.parseDoubleSafe(wageStr),
            profitMarginPercent = Formatters.parseDoubleSafe(profitStr),
            taxPercent = Formatters.parseDoubleSafe(taxStr),
            costBasisPerUnitToman = selectedInventory?.avgBuyPricePerUnit ?: 0.0
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ext.card,
        title = { Text("ثبت تراکنش جدید", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("نوع تراکنش:", style = MaterialTheme.typography.labelMedium, color = ext.mutedText)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(TxType.entries) { t ->
                        FilterChip(selected = txType == t, onClick = { txType = t }, label = { Text(t.labelFa) })
                    }
                }

                if (!isTransfer) {
                    Text("دسته طلا / ارز:", style = MaterialTheme.typography.labelMedium, color = ext.mutedText)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(GoldCategory.inventoryCategories) { c ->
                            FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.labelFa) })
                        }
                    }

                    if (inventory.isNotEmpty() && (txType == TxType.SALE || txType == TxType.PURCHASE)) {
                        Text("کالای انبار (اختیاری):", style = MaterialTheme.typography.labelMedium, color = ext.mutedText)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item {
                                FilterChip(
                                    selected = selectedInventory == null,
                                    onClick = { selectedInventory = null },
                                    label = { Text("آزاد") }
                                )
                            }
                            items(inventory.filter { it.categoryEnum == category }) { inv ->
                                FilterChip(
                                    selected = selectedInventory?.id == inv.id,
                                    onClick = {
                                        selectedInventory = inv
                                        karatStr = inv.karat.toString()
                                        wageStr = inv.defaultWagePercent.toString()
                                    },
                                    label = { Text("${inv.title} (${Formatters.formatGrams(inv.weightGrams)})") }
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = weightStr,
                            onValueChange = { weightStr = it },
                            label = { Text(if (category == GoldCategory.USD_CURRENCY) "مقدار دلار" else "وزن (گرم)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = karatStr,
                            onValueChange = { karatStr = it },
                            label = { Text("عیار") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = unitPriceStr,
                        onValueChange = { unitPriceStr = it },
                        label = { Text("نرخ هر واحد (تومان)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (txType == TxType.SALE && category != GoldCategory.USD_CURRENCY) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = wageStr,
                                onValueChange = { wageStr = it },
                                label = { Text("اجرت٪") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = profitStr,
                                onValueChange = { profitStr = it },
                                label = { Text("سود٪") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = taxStr,
                                onValueChange = { taxStr = it },
                                label = { Text("مالیات٪") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = manualAmountStr,
                        onValueChange = { manualAmountStr = it },
                        label = { Text("مبلغ انتقال (تومان)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (accounts.isNotEmpty()) {
                    Text("حساب مغازه:", style = MaterialTheme.typography.labelMedium, color = ext.mutedText)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(accounts) { acc ->
                            FilterChip(
                                selected = selectedAccount?.id == acc.id,
                                onClick = { selectedAccount = acc },
                                label = { Text(acc.name) }
                            )
                        }
                    }
                }

                if (isTransfer && accounts.size > 1) {
                    Text("حساب مقصد (اختیاری):", style = MaterialTheme.typography.labelMedium, color = ext.mutedText)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(accounts.filter { it.id != selectedAccount?.id }) { acc ->
                            FilterChip(
                                selected = targetAccount?.id == acc.id,
                                onClick = { targetAccount = acc },
                                label = { Text(acc.name) }
                            )
                        }
                    }
                }

                if (sellers.isNotEmpty()) {
                    Text("فروشنده مغازه:", style = MaterialTheme.typography.labelMedium, color = ext.mutedText)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(
                                selected = selectedSeller == null,
                                onClick = { selectedSeller = null },
                                label = { Text("عمومی") }
                            )
                        }
                        items(sellers) { s ->
                            FilterChip(
                                selected = selectedSeller?.id == s.id,
                                onClick = { selectedSeller = s },
                                label = { Text(s.name) }
                            )
                        }
                    }
                }

                if (customers.isNotEmpty() && !isTransfer) {
                    Text("مشتری (اختیاری):", style = MaterialTheme.typography.labelMedium, color = ext.mutedText)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(
                                selected = selectedCustomer == null,
                                onClick = { selectedCustomer = null },
                                label = { Text("مشتری حضوری") }
                            )
                        }
                        items(customers) { c ->
                            FilterChip(
                                selected = selectedCustomer?.id == c.id,
                                onClick = { selectedCustomer = c },
                                label = { Text(c.fullName) }
                            )
                        }
                    }
                }

                Text("روش پرداخت:", style = MaterialTheme.typography.labelMedium, color = ext.mutedText)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(PaymentMethod.entries) { pm ->
                        FilterChip(selected = paymentMethod == pm, onClick = { paymentMethod = pm }, label = { Text(pm.labelFa) })
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("توضیحات (اختیاری)") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (!isTransfer) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("مبلغ نهایی فاکتور:", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    Formatters.formatToman(preview.totalAmountToman),
                                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (txType == TxType.SALE) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("سود خالص معامله:", style = MaterialTheme.typography.labelSmall, color = ext.mutedText)
                                    Text(
                                        Formatters.formatToman(preview.netProfitToman),
                                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                        color = ext.profit
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val acc = selectedAccount ?: return@Button
                    val total = if (isTransfer) Formatters.parseDoubleSafe(manualAmountStr) else preview.totalAmountToman
                    if (total <= 0) return@Button
                    onSave(
                        GoldTransaction(
                            txType = txType.name,
                            goldCategory = if (isTransfer) GoldCategory.NONE.name else category.name,
                            inventoryItemId = selectedInventory?.id,
                            customerId = selectedCustomer?.id,
                            customerName = selectedCustomer?.fullName,
                            sellerId = selectedSeller?.id,
                            sellerName = selectedSeller?.name,
                            accountId = acc.id,
                            targetAccountId = if (isTransfer) targetAccount?.id else null,
                            accountName = acc.name,
                            weightGrams = if (isTransfer) 0.0 else Formatters.parseDoubleSafe(weightStr),
                            karat = karatStr.toIntOrNull() ?: 750,
                            unitPriceToman = Formatters.parseDoubleSafe(unitPriceStr),
                            wagePercent = Formatters.parseDoubleSafe(wageStr),
                            profitMarginPercent = Formatters.parseDoubleSafe(profitStr),
                            taxPercent = Formatters.parseDoubleSafe(taxStr),
                            costBasisToman = if (isTransfer) total else preview.costBasisToman,
                            totalAmountToman = total,
                            calculatedProfitToman = if (txType == TxType.SALE) preview.netProfitToman else 0.0,
                            paymentMethod = paymentMethod.name,
                            paymentStatus = if (paymentMethod == PaymentMethod.GATEWAY) PaymentStatus.PENDING_GATEWAY.name else PaymentStatus.PAID.name,
                            notes = notes.trim().takeIf { it.isNotEmpty() }
                        )
                    )
                },
                modifier = Modifier.testTag("save_transaction_button")
            ) {
                Text("ثبت تراکنش")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}
