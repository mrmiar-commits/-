package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
fun LedgersScreen(viewModel: ZarnegarViewModel) {
    val context = LocalContext.current
    val ext = LocalZarnegarColors.current

    val inventory by viewModel.inventory.collectAsStateWithLifecycle()
    val sellers by viewModel.sellers.collectAsStateWithLifecycle()
    val sellerMovements by viewModel.sellerMovements.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val investors by viewModel.investors.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddInventoryDialog by remember { mutableStateOf(false) }
    var editingInventory by remember { mutableStateOf<InventoryItem?>(null) }
    var showAddSellerDialog by remember { mutableStateOf(false) }
    var movementTargetSeller by remember { mutableStateOf<ShopSeller?>(null) }
    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var activeCustomerFile by remember { mutableStateOf<Customer?>(null) }
    var showAddInvestorDialog by remember { mutableStateOf(false) }
    var settleTargetInvestor by remember { mutableStateOf<Investor?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            val canAddSeller = selectedTab != 1 || sellers.size < 5
            if (canAddSeller) {
                ExtendedFloatingActionButton(
                    onClick = {
                        when (selectedTab) {
                            0 -> showAddInventoryDialog = true
                            1 -> showAddSellerDialog = true
                            2 -> showAddCustomerDialog = true
                            else -> showAddInvestorDialog = true
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("ledgers_fab")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when (selectedTab) {
                            0 -> "کالای انبار"
                            1 -> "فروشنده جدید"
                            2 -> "مشتری جدید"
                            else -> "سرمایه‌گذار"
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("انبار طلا", "فروشندگان (۳ تا ۵)", "مشتریان", "سرمایه‌گذاران").forEachIndexed { idx, label ->
                    item {
                        val active = selectedTab == idx
                        Surface(
                            onClick = { selectedTab = idx },
                            shape = RoundedCornerShape(12.dp),
                            color = if (active) MaterialTheme.colorScheme.primary else ext.card,
                            border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary else ext.border),
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .testTag("ledger_tab_$idx")
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp)) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            when (selectedTab) {
                0 -> InventoryTabContent(
                    inventory = inventory,
                    onEdit = { editingInventory = it },
                    onDelete = { viewModel.deleteInventoryItem(it.id) }
                )
                1 -> SellersTabContent(
                    sellers = sellers,
                    movements = sellerMovements,
                    onRecordMovement = { movementTargetSeller = it },
                    onDeleteSeller = { viewModel.deleteSeller(it.id) }
                )
                2 -> CustomersTabContent(
                    customers = customers,
                    onOpenFile = { activeCustomerFile = it },
                    onDelete = { viewModel.deleteCustomer(it.id) }
                )
                else -> InvestorsTabContent(
                    investors = investors,
                    onSettle = { settleTargetInvestor = it },
                    onDelete = { viewModel.deleteInvestor(it.id) }
                )
            }
        }
    }

    if (showAddInventoryDialog || editingInventory != null) {
        InventoryItemDialog(
            existing = editingInventory,
            onDismiss = {
                showAddInventoryDialog = false
                editingInventory = null
            },
            onSave = { cat, piece, title, karat, weight, qty, buyPrice, wage, lab ->
                val target = editingInventory
                if (target == null) {
                    viewModel.addInventoryItem(cat, piece, title, karat, weight, qty, buyPrice, wage, lab)
                } else {
                    viewModel.updateInventoryItem(target, cat, piece, title, karat, weight, qty, buyPrice, wage, lab)
                }
                showAddInventoryDialog = false
                editingInventory = null
            }
        )
    }

    if (showAddSellerDialog) {
        AddSellerDialog(
            onDismiss = { showAddSellerDialog = false },
            onSave = { name, role, phone, notes ->
                viewModel.addSeller(name, role, phone, notes)
                showAddSellerDialog = false
            }
        )
    }

    movementTargetSeller?.let { seller ->
        RecordSellerMovementDialog(
            seller = seller,
            onDismiss = { movementTargetSeller = null },
            onSave = { dir, cat, grams, karat, usd, cash, desc ->
                viewModel.recordSellerMovement(seller, dir, cat, grams, karat, usd, cash, desc)
                movementTargetSeller = null
            }
        )
    }

    if (showAddCustomerDialog) {
        AddCustomerDialog(
            onDismiss = { showAddCustomerDialog = false },
            onSave = { name, phone, nid, notes ->
                viewModel.addCustomer(name, phone, nid, notes)
                showAddCustomerDialog = false
            }
        )
    }

    activeCustomerFile?.let { customer ->
        val customerTxs = transactions.filter { it.customerId == customer.id || it.customerName == customer.fullName }
        CustomerFileDialog(
            customer = customer,
            transactions = customerTxs,
            onPrint = { InvoicePrinter.printCustomerLedger(context, settings.shopName, customer, customerTxs) },
            onDismiss = { activeCustomerFile = null }
        )
    }

    if (showAddInvestorDialog) {
        AddInvestorDialog(
            onDismiss = { showAddInvestorDialog = false },
            onSave = { name, phone, cat, grams, cash, share, notes ->
                viewModel.addInvestor(name, phone, cat, grams, cash, share, notes)
                showAddInvestorDialog = false
            }
        )
    }

    settleTargetInvestor?.let { inv ->
        SettleInvestorDialog(
            investor = inv,
            onDismiss = { settleTargetInvestor = null },
            onConfirm = { payout ->
                viewModel.settleInvestorProfit(inv, payout)
                settleTargetInvestor = null
            }
        )
    }
}

@Composable
private fun InventoryTabContent(
    inventory: List<InventoryItem>,
    onEdit: (InventoryItem) -> Unit,
    onDelete: (InventoryItem) -> Unit
) {
    val ext = LocalZarnegarColors.current
    var categoryFilter by remember { mutableStateOf<GoldCategory?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 10.dp)
        ) {
            item {
                FilterChip(
                    selected = categoryFilter == null,
                    onClick = { categoryFilter = null },
                    label = { Text("همه (${Formatters.toPersianDigits(inventory.size.toString())})") }
                )
            }
            items(GoldCategory.inventoryCategories) { cat ->
                FilterChip(
                    selected = categoryFilter == cat,
                    onClick = { categoryFilter = if (categoryFilter == cat) null else cat },
                    label = { Text(cat.labelFa) }
                )
            }
        }

        val filtered = remember(inventory, categoryFilter) {
            if (categoryFilter == null) inventory else inventory.filter { it.categoryEnum == categoryFilter }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (filtered.isEmpty()) {
                item {
                    ZarnegarEmptyState(
                        title = "موجودی انبار خالی است",
                        subtitle = "طلای آبشده، شمش، طلای نو ویترینی، کارکرده بدون اجرت و موجودی دلار را با وزن و عیار ثبت کنید.",
                        icon = Icons.Outlined.Inventory2
                    )
                }
            } else {
                items(filtered, key = { it.id }) { item ->
                    MinimalCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(item.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "${item.categoryEnum.labelFa} • ${item.pieceTypeEnum.labelFa} • عیار ${Formatters.toPersianDigits(item.karat.toString())}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ext.mutedText
                                )
                                if (!item.labCode.isNullOrBlank()) {
                                    Text(
                                        text = "انگ / کد ری‌گیری: ${item.labCode}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ext.info
                                    )
                                }
                            }
                            Row {
                                IconButton(onClick = { onEdit(item) }) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "ویرایش", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { onDelete(item) }) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "حذف", tint = ext.mutedText)
                                }
                            }
                        }
                        HorizontalDivider(color = ext.border)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (item.categoryEnum == GoldCategory.USD_CURRENCY) {
                                    "مقدار: ${Formatters.formatUsd(item.quantity.toDouble())}"
                                } else {
                                    "وزن: ${Formatters.formatGrams(item.weightGrams)} (${Formatters.toPersianDigits(item.quantity.toString())} عدد)"
                                },
                                style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "میانگین خرید: ${Formatters.formatToman(item.avgBuyPricePerUnit, false)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ext.mutedText
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SellersTabContent(
    sellers: List<ShopSeller>,
    movements: List<SellerMovement>,
    onRecordMovement: (ShopSeller) -> Unit,
    onDeleteSeller: (ShopSeller) -> Unit
) {
    val ext = LocalZarnegarColors.current
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            MinimalCard(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "مدیریت فروشندگان مغازه (۳ تا ۵ کاربر)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "ثبت جزئیات ورود و خروج طلا و دلار توسط هر فروشنده",
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.mutedText
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "${Formatters.toPersianDigits(sellers.size.toString())} از ۵",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        if (sellers.isEmpty()) {
            item {
                ZarnegarEmptyState(
                    title = "فروشنده‌ای تعریف نشده است",
                    subtitle = "تا ۵ فروشنده مغازه تعریف کنید و ورود و خروج طلا و دلار هر نفر را دقیق پیگیری کنید.",
                    icon = Icons.Outlined.Badge
                )
            }
        } else {
            items(sellers, key = { it.id }) { seller ->
                MinimalCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(seller.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${seller.roleTitle} • ${Formatters.toPersianDigits(seller.phone)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.mutedText
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalButton(
                                onClick = { onRecordMovement(seller) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .heightIn(min = 40.dp)
                                    .testTag("seller_movement_btn_${seller.id}")
                            ) {
                                Icon(Icons.Outlined.SwapVert, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("ورود / خروج", style = MaterialTheme.typography.labelMedium)
                            }
                            IconButton(onClick = { onDeleteSeller(seller) }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "حذف فروشنده", tint = ext.mutedText)
                            }
                        }
                    }

                    HorizontalDivider(color = ext.border)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = ext.elevated,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("ورود / خروج طلا", style = MaterialTheme.typography.labelSmall, color = ext.mutedText)
                                Text(
                                    text = "+ ${Formatters.formatGrams(seller.goldInGrams)}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = ext.profit
                                )
                                Text(
                                    text = "- ${Formatters.formatGrams(seller.goldOutGrams)}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = ext.loss
                                )
                                Text(
                                    text = "تراز: ${Formatters.formatGrams(seller.netGoldGrams)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Surface(
                            color = ext.elevated,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("ورود / خروج دلار", style = MaterialTheme.typography.labelSmall, color = ext.mutedText)
                                Text(
                                    text = "+ ${Formatters.formatUsd(seller.usdIn)}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = ext.profit
                                )
                                Text(
                                    text = "- ${Formatters.formatUsd(seller.usdOut)}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = ext.loss
                                )
                                Text(
                                    text = "تراز: ${Formatters.formatUsd(seller.netUsd)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ext.info
                                )
                            }
                        }
                    }
                }
            }
        }

        if (movements.isNotEmpty()) {
            item {
                MinimalSectionHeader(
                    title = "تاریخچه ورود و خروج فروشندگان",
                    subtitle = "${Formatters.toPersianDigits(movements.size.toString())} ثبت"
                )
            }
            items(movements.take(15), key = { it.id }) { m ->
                val isIn = m.directionEnum == MovementDirection.IN
                MinimalCard(contentPadding = PaddingValues(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${m.sellerName} • ${m.directionEnum.labelFa}",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isIn) ext.profit else ext.loss
                            )
                            Text(
                                text = "${m.categoryEnum.labelFa} • ${m.description}",
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.mutedText
                            )
                            Text(
                                text = Formatters.formatMillis(m.timestampMillis),
                                style = MaterialTheme.typography.labelSmall,
                                color = ext.mutedText
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            if (m.weightGrams > 0) {
                                Text(
                                    text = Formatters.formatGrams(m.weightGrams),
                                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (m.usdAmount > 0) {
                                Text(
                                    text = Formatters.formatUsd(m.usdAmount),
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = ext.info
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomersTabContent(
    customers: List<Customer>,
    onOpenFile: (Customer) -> Unit,
    onDelete: (Customer) -> Unit
) {
    val ext = LocalZarnegarColors.current
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (customers.isEmpty()) {
            item {
                ZarnegarEmptyState(
                    title = "مشتری ثبت نشده است",
                    subtitle = "برای هر مشتری پرونده جداگانه با سابقه خرید و تراز طلایی ایجاد کنید.",
                    icon = Icons.Outlined.PeopleOutline
                )
            }
        } else {
            items(customers, key = { it.id }) { c ->
                MinimalCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(c.fullName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = Formatters.toPersianDigits(c.phone),
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.mutedText
                            )
                        }
                        Row {
                            OutlinedButton(onClick = { onOpenFile(c) }) {
                                Text("پرونده")
                            }
                            IconButton(onClick = { onDelete(c) }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "حذف مشتری", tint = ext.mutedText)
                            }
                        }
                    }
                    HorizontalDivider(color = ext.border)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "کل خرید: ${Formatters.formatToman(c.totalPurchasedToman, false)}",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            "تراز طلا: ${Formatters.formatGrams(c.goldBalanceGrams)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InvestorsTabContent(
    investors: List<Investor>,
    onSettle: (Investor) -> Unit,
    onDelete: (Investor) -> Unit
) {
    val ext = LocalZarnegarColors.current
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (investors.isEmpty()) {
            item {
                ZarnegarEmptyState(
                    title = "سرمایه‌گذاری ثبت نشده است",
                    subtitle = "سرمایه‌گذاران طلای آبشده، شمش، طلای بدون اجرت و طلای نو را همراه با سهم سود ثبت کنید.",
                    icon = Icons.Outlined.Handshake
                )
            }
        } else {
            items(investors, key = { it.id }) { inv ->
                MinimalCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(inv.fullName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${inv.categoryEnum.labelFa} • سهم سود: ${Formatters.toPersianDigits(inv.profitSharePercent.toInt().toString())}٪",
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.mutedText
                            )
                        }
                        Row {
                            FilledTonalButton(onClick = { onSettle(inv) }) {
                                Text("تسویه سود")
                            }
                            IconButton(onClick = { onDelete(inv) }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "حذف", tint = ext.mutedText)
                            }
                        }
                    }
                    HorizontalDivider(color = ext.border)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "سرمایه طلایی: ${Formatters.formatGrams(inv.investedGrams)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "سود پرداختی: ${Formatters.formatToman(inv.realizedProfitToman, false)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = ext.profit
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryItemDialog(
    existing: InventoryItem?,
    onDismiss: () -> Unit,
    onSave: (GoldCategory, GoldPieceType, String, Int, Double, Long, Double, Double, String?) -> Unit
) {
    val ext = LocalZarnegarColors.current
    var category by remember { mutableStateOf(existing?.categoryEnum ?: GoldCategory.MELTED_GOLD) }
    var pieceType by remember { mutableStateOf(existing?.pieceTypeEnum ?: GoldPieceType.defaultFor(category)) }
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var karatStr by remember { mutableStateOf((existing?.karat ?: category.defaultKarat).toString()) }
    var weightStr by remember { mutableStateOf(existing?.weightGrams?.toString() ?: "") }
    var qtyStr by remember { mutableStateOf((existing?.quantity ?: 1L).toString()) }
    var avgBuyStr by remember { mutableStateOf(existing?.avgBuyPricePerUnit?.toLong()?.toString() ?: "") }
    var wageStr by remember { mutableStateOf((existing?.defaultWagePercent ?: 0.0).toString()) }
    var labCode by remember { mutableStateOf(existing?.labCode ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ext.card,
        title = { Text(if (existing == null) "ثبت موجودی انبار طلا" else "ویرایش موجودی انبار") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("نوع طلا / ارز:", style = MaterialTheme.typography.labelMedium, color = ext.mutedText)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(GoldCategory.inventoryCategories) { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = {
                                category = c
                                pieceType = GoldPieceType.defaultFor(c)
                                karatStr = c.defaultKarat.toString()
                            },
                            label = { Text(c.labelFa) }
                        )
                    }
                }
                Text("نوع قطعه:", style = MaterialTheme.typography.labelMedium, color = ext.mutedText)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(GoldPieceType.entries) { p ->
                        FilterChip(selected = pieceType == p, onClick = { pieceType = p }, label = { Text(p.labelFa) })
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان کالا") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = weightStr,
                        onValueChange = { weightStr = it },
                        label = { Text("وزن (گرم)") },
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = qtyStr,
                        onValueChange = { qtyStr = it },
                        label = { Text(if (category == GoldCategory.USD_CURRENCY) "مقدار دلار" else "تعداد") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = wageStr,
                        onValueChange = { wageStr = it },
                        label = { Text("اجرت پیش‌فرض٪") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = avgBuyStr,
                    onValueChange = { avgBuyStr = it },
                    label = { Text("میانگین قیمت خرید (تومان)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = labCode,
                    onValueChange = { labCode = it },
                    label = { Text("شماره انگ / ری‌گیری (اختیاری)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (title.isNotBlank()) {
                    onSave(
                        category,
                        pieceType,
                        title.trim(),
                        karatStr.toIntOrNull() ?: 750,
                        Formatters.parseDoubleSafe(weightStr),
                        qtyStr.toLongOrNull() ?: 1L,
                        Formatters.parseDoubleSafe(avgBuyStr),
                        Formatters.parseDoubleSafe(wageStr),
                        labCode.trim().takeIf { it.isNotEmpty() }
                    )
                }
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
private fun AddSellerDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, String?) -> Unit
) {
    val ext = LocalZarnegarColors.current
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("فروشنده مغازه") }
    var phone by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ext.card,
        title = { Text("تعریف فروشنده مغازه") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("نام و نام خانوادگی فروشنده") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("seller_name_input")
                )
                OutlinedTextField(
                    value = role,
                    onValueChange = { role = it },
                    label = { Text("سمت (مثلاً فروشنده ویترین / مسئول آبشده)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("شماره تماس") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("توضیحات (اختیاری)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name.trim(), role.trim(), phone.trim(), notes.trim().takeIf { it.isNotEmpty() })
                    }
                },
                modifier = Modifier.testTag("save_seller_button")
            ) { Text("تعریف فروشنده") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
private fun RecordSellerMovementDialog(
    seller: ShopSeller,
    onDismiss: () -> Unit,
    onSave: (MovementDirection, GoldCategory, Double, Int, Double, Double, String) -> Unit
) {
    val ext = LocalZarnegarColors.current
    var direction by remember { mutableStateOf(MovementDirection.IN) }
    var category by remember { mutableStateOf(GoldCategory.MELTED_GOLD) }
    var weightStr by remember { mutableStateOf("") }
    var karatStr by remember { mutableStateOf("750") }
    var usdStr by remember { mutableStateOf("") }
    var cashStr by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ext.card,
        title = { Text("ورود و خروج طلا / دلار — ${seller.name}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MovementDirection.entries.forEach { dir ->
                        FilterChip(
                            selected = direction == dir,
                            onClick = { direction = dir },
                            label = { Text(dir.labelFa) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Text("نوع طلا / ارز:", style = MaterialTheme.typography.labelMedium, color = ext.mutedText)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(GoldCategory.inventoryCategories) { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = {
                                category = c
                                karatStr = c.defaultKarat.toString()
                            },
                            label = { Text(c.labelFa) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = weightStr,
                        onValueChange = { weightStr = it },
                        label = { Text("وزن طلا (گرم)") },
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = usdStr,
                        onValueChange = { usdStr = it },
                        label = { Text("مقدار دلار ($)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = cashStr,
                        onValueChange = { cashStr = it },
                        label = { Text("نقدی (تومان)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("شرح جزئیات (مثلاً تحویل آبشده به کارگاه)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val g = Formatters.parseDoubleSafe(weightStr)
                val u = Formatters.parseDoubleSafe(usdStr)
                val c = Formatters.parseDoubleSafe(cashStr)
                if (g > 0 || u > 0 || c > 0) {
                    onSave(direction, category, g, karatStr.toIntOrNull() ?: 750, u, c, desc.trim())
                }
            }) { Text("ثبت در کارنامه فروشنده") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
private fun AddCustomerDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String?, String?) -> Unit
) {
    val ext = LocalZarnegarColors.current
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var nid by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ext.card,
        title = { Text("ایجاد پرونده مشتری جدید") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("نام و نام خانوادگی") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("شماره همراه") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = nid, onValueChange = { nid = it }, label = { Text("کد ملی (اختیاری)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("یادداشت پرونده") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank()) onSave(name.trim(), phone.trim(), nid.trim().takeIf { it.isNotEmpty() }, notes.trim().takeIf { it.isNotEmpty() })
            }) { Text("ایجاد پرونده") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
private fun CustomerFileDialog(
    customer: Customer,
    transactions: List<GoldTransaction>,
    onPrint: () -> Unit,
    onDismiss: () -> Unit
) {
    val ext = LocalZarnegarColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ext.card,
        title = { Text("پرونده اختصاصی ${customer.fullName}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("تلفن: ${Formatters.toPersianDigits(customer.phone)}", style = MaterialTheme.typography.bodySmall)
                Text("جمع خرید: ${Formatters.formatToman(customer.totalPurchasedToman)}", style = MaterialTheme.typography.bodySmall)
                Text("تراز طلایی: ${Formatters.formatGrams(customer.goldBalanceGrams)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                HorizontalDivider(color = ext.border)
                if (transactions.isEmpty()) {
                    Text("هنوز تراکنشی در این پرونده ثبت نشده است.", style = MaterialTheme.typography.bodySmall, color = ext.mutedText)
                } else {
                    transactions.forEach { tx ->
                        Text(
                            text = "• ${tx.invoiceNumber} | ${tx.txTypeEnum.labelFa} (${tx.categoryEnum.labelFa}) | ${Formatters.formatGrams(tx.weightGrams)} | ${Formatters.formatToman(tx.totalAmountToman)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onPrint) { Text("چاپ PDF پرونده") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("بستن") } }
    )
}

@Composable
private fun AddInvestorDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, GoldCategory, Double, Double, Double, String?) -> Unit
) {
    val ext = LocalZarnegarColors.current
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(GoldCategory.MELTED_GOLD) }
    var gramsStr by remember { mutableStateOf("") }
    var cashStr by remember { mutableStateOf("0") }
    var shareStr by remember { mutableStateOf("50") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ext.card,
        title = { Text("ثبت سرمایه‌گذار جدید") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("نام سرمایه‌گذار") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("شماره تماس") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(GoldCategory.investorCategories) { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.labelFa) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = gramsStr, onValueChange = { gramsStr = it }, label = { Text("طلا (گرم)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = shareStr, onValueChange = { shareStr = it }, label = { Text("سهم سود٪") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = cashStr, onValueChange = { cashStr = it }, label = { Text("سرمایه نقدی (تومان)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("شرایط قرارداد") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank()) {
                    onSave(
                        name.trim(),
                        phone.trim(),
                        category,
                        Formatters.parseDoubleSafe(gramsStr),
                        Formatters.parseDoubleSafe(cashStr),
                        Formatters.parseDoubleSafe(shareStr),
                        notes.trim().takeIf { it.isNotEmpty() }
                    )
                }
            }) { Text("ثبت سرمایه‌گذار") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
private fun SettleInvestorDialog(
    investor: Investor,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    val ext = LocalZarnegarColors.current
    var payoutStr by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ext.card,
        title = { Text("تسویه سود ${investor.fullName}") },
        text = {
            OutlinedTextField(
                value = payoutStr,
                onValueChange = { payoutStr = it },
                label = { Text("مبلغ سود پرداختی (تومان)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = {
                val p = Formatters.parseDoubleSafe(payoutStr)
                if (p > 0) onConfirm(p)
            }) { Text("ثبت تسویه") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
