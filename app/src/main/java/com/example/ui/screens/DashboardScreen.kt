package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.*
import com.example.ui.components.MinimalCard
import com.example.ui.components.MinimalSectionHeader
import com.example.ui.components.ZarnegarEmptyState
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalZarnegarColors
import com.example.utils.Formatters
import com.example.utils.InvoicePrinter
import com.example.viewmodel.GoldBreakdownSummary
import com.example.viewmodel.ZarnegarViewModel
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    viewModel: ZarnegarViewModel,
    onQuickAddSale: () -> Unit,
    onOpenExpenses: () -> Unit,
    onOpenVoiceRecorder: () -> Unit,
    onOpenReportsAndSheets: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ext = LocalZarnegarColors.current

    val marketState by viewModel.marketState.collectAsStateWithLifecycle()
    val summary by viewModel.dashboardSummary.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val manualPricing by viewModel.manualPricing.collectAsStateWithLifecycle()
    val isDark by viewModel.isDarkTheme.collectAsStateWithLifecycle()

    var showPricingDialog by remember { mutableStateOf(false) }

    LaunchedEffect(marketState) {
        if (marketState is UiState.Success) {
            viewModel.evaluateAlertsWithContext(context)
        }
    }

    val snapshot = (marketState as? UiState.Success)?.data ?: MarketSnapshot()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Minimal Top Bar with Logo + Theme Switcher + Online Refresh
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.zarnegar_minimal_icon),
                        contentDescription = "لوگوی زرنگار",
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Column {
                        Text(
                            text = settings.shopName.ifBlank { "طلافروشی زرنگار" },
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "حسابداری مینیمال طلا و ارز",
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.mutedText
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalIconButton(
                        onClick = { viewModel.toggleDarkTheme() },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("toggle_theme_button")
                    ) {
                        Icon(
                            imageVector = if (isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                            contentDescription = if (isDark) "تم روشن" else "تم تاریک"
                        )
                    }
                    FilledTonalIconButton(
                        onClick = { scope.launch { viewModel.refreshMarketRates(context) } },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("refresh_market_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "بروزرسانی نرخ آنلاین"
                        )
                    }
                }
            }
        }

        // 2. Gold & USD Price Board (Online API Inquiry + Manual Entry — NO Chart)
        item {
            MinimalCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "تابلو نرخ طلا و دلار",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            snapshot.isManualOverride -> ext.amber
                                            snapshot.isLiveNetwork -> ext.profit
                                            else -> ext.mutedText
                                        }
                                    )
                            )
                            Text(
                                text = when {
                                    snapshot.isManualOverride -> "نرخ دستی • ${snapshot.lastUpdatedText}"
                                    snapshot.isLiveNetwork -> "استعلام آنلاین API • ${snapshot.lastUpdatedText}"
                                    else -> "نرخ آفلاین • ${snapshot.lastUpdatedText}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = ext.mutedText
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { showPricingDialog = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .testTag("open_pricing_mode_button")
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (manualPricing.useManualPrice) "ویرایش دستی" else "ورود دستی / API",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                HorizontalDivider(color = ext.border)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PriceCell(
                        label = "گرم ۱۸ عیار",
                        value = Formatters.formatToman(snapshot.gold18kGramToman, false),
                        unit = "تومان",
                        accent = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    PriceCell(
                        label = "مثقال آبشده",
                        value = Formatters.formatToman(snapshot.meltedMesghalToman, false),
                        unit = "تومان",
                        accent = ext.amber,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PriceCell(
                        label = "شمش ۲۴ عیار",
                        value = Formatters.formatToman(snapshot.gold24kGramToman, false),
                        unit = "تومان",
                        accent = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    PriceCell(
                        label = "دلار آزاد",
                        value = Formatters.formatToman(snapshot.usdToToman, false),
                        unit = "تومان",
                        accent = ext.info,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 3. Voice Recorder Quick Strip with Offline Queue Badge
        item {
            Surface(
                onClick = onOpenVoiceRecorder,
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_voice_cta")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Mic,
                                contentDescription = "پیامگیر صوتی",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "پیامگیر صوتی هوشمند (آنلاین و آفلاین)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "مثال: «۱.۵ گرم آبشده به رضا» ← تحلیل و ثبت خودکار در گوگل شیت",
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.mutedText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (summary.pendingVoiceSyncCount > 0) {
                        Surface(
                            color = ext.amber.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "${Formatters.toPersianDigits(summary.pendingVoiceSyncCount.toString())} در صف",
                                style = MaterialTheme.typography.labelSmall,
                                color = ext.amber,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // 4. Gold Breakdown by Form + USD Balance
        item {
            GoldFormsBreakdownCard(breakdown = summary.goldBreakdown)
        }

        // 5. Minimal 2x2 KPI Summary
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MinimalKpiCard(
                        title = "فروش امروز",
                        value = Formatters.formatToman(summary.todaySalesToman, false),
                        subtitle = "سود: ${Formatters.formatToman(summary.todayProfitToman, false)}",
                        accent = ext.profit,
                        icon = Icons.Outlined.PointOfSale,
                        modifier = Modifier.weight(1f)
                    )
                    MinimalKpiCard(
                        title = "سود خالص (پس از مخارج)",
                        value = Formatters.formatToman(summary.netProfitAfterExpensesToman, false),
                        subtitle = "مخارج: ${Formatters.formatToman(summary.totalExpensesToman, false)}",
                        accent = if (summary.netProfitAfterExpensesToman >= 0) ext.profit else ext.loss,
                        icon = Icons.Outlined.AccountBalanceWallet,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MinimalKpiCard(
                        title = "کل حساب‌ها و کارت‌ها",
                        value = Formatters.formatToman(summary.totalCashAccountsToman, false),
                        subtitle = "معادل طلا: ${Formatters.formatGrams(summary.totalAccountsGoldEquivGrams)}",
                        accent = MaterialTheme.colorScheme.primary,
                        icon = Icons.Outlined.CreditCard,
                        modifier = Modifier.weight(1f)
                    )
                    MinimalKpiCard(
                        title = "مجموع طلا و سرمایه",
                        value = Formatters.formatGrams(summary.totalInventoryGrams),
                        subtitle = "سرمایه‌گذاران: ${Formatters.formatGrams(summary.totalInvestorGrams)}",
                        accent = ext.info,
                        icon = Icons.Outlined.Inventory2,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 6. Quick Actions Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionChip(
                    label = "ثبت فروش",
                    icon = Icons.Filled.Add,
                    onClick = onQuickAddSale,
                    testTag = "quick_add_sale",
                    modifier = Modifier.weight(1f)
                )
                QuickActionChip(
                    label = "ثبت خرج",
                    icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                    onClick = onOpenExpenses,
                    testTag = "quick_add_expense",
                    modifier = Modifier.weight(1f)
                )
                QuickActionChip(
                    label = "گزارش / شیت",
                    icon = Icons.Outlined.CloudUpload,
                    onClick = onOpenReportsAndSheets,
                    testTag = "quick_open_sheets",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 7. Recent Transactions
        item {
            MinimalSectionHeader(
                title = "آخرین تراکنش‌ها",
                subtitle = "${Formatters.toPersianDigits(transactions.size.toString())} رکورد ثبت شده"
            )
        }

        if (transactions.isEmpty()) {
            item {
                ZarnegarEmptyState(
                    title = "هنوز تراکنشی ثبت نشده است",
                    subtitle = "برای ثبت اولین فروش، خرید یا انتقال حساب از دکمه «ثبت فروش» یا پیامگیر صوتی استفاده کنید."
                )
            }
        } else {
            items(transactions.take(5), key = { it.id }) { tx ->
                MinimalTransactionRow(
                    tx = tx,
                    onPrint = {
                        InvoicePrinter.printTransactionInvoice(context, settings.shopName, settings.ownerName, tx)
                    }
                )
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }

    if (showPricingDialog) {
        PricingModeDialog(
            currentSnapshot = snapshot,
            currentConfig = manualPricing,
            onDismiss = { showPricingDialog = false },
            onSave = { useManual, g18, mesghal, g24, usd ->
                viewModel.savePricingSettings(useManual, g18, mesghal, g24, usd)
                showPricingDialog = false
            }
        )
    }
}

@Composable
private fun PriceCell(
    label: String,
    value: String,
    unit: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val ext = LocalZarnegarColors.current
    Surface(
        modifier = modifier,
        color = ext.elevated,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = ext.mutedText)
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                color = accent,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(text = unit, style = MaterialTheme.typography.labelSmall, color = ext.mutedText)
        }
    }
}

@Composable
private fun GoldFormsBreakdownCard(breakdown: GoldBreakdownSummary) {
    val ext = LocalZarnegarColors.current
    MinimalCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "موجودی طلا به تفکیک نوع و دلار",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "مجموع کل طلا: ${Formatters.formatGrams(breakdown.totalGoldGrams)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Surface(
                color = ext.infoContainer,
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AttachMoney,
                        contentDescription = null,
                        tint = ext.info,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = Formatters.formatUsd(breakdown.totalUsdBalance),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = ext.info,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        HorizontalDivider(color = ext.border)

        val rows = listOf(
            Triple("طلای آبشده", breakdown.meltedGrams, MaterialTheme.colorScheme.primary),
            Triple("شمش طلا (۲۴ عیار)", breakdown.bullionGrams, ext.amber),
            Triple("طلای نو ویترینی", breakdown.newDisplayGrams, ext.profit),
            Triple("طلای کارکرده بدون اجرت", breakdown.usedNoWageGrams, ext.info),
            Triple("سایر انواع طلا و سکه", breakdown.otherGoldGrams, ext.mutedText)
        )

        rows.forEach { (label, grams, dotColor) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = Formatters.formatGrams(grams),
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun MinimalKpiCard(
    title: String,
    value: String,
    subtitle: String,
    accent: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    val ext = LocalZarnegarColors.current
    MinimalCard(modifier = modifier, contentPadding = PaddingValues(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = ext.mutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
            color = accent,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = ext.mutedText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun QuickActionChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val ext = LocalZarnegarColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = ext.card,
        border = BorderStroke(1.dp, ext.border),
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
fun MinimalTransactionRow(
    tx: GoldTransaction,
    onPrint: () -> Unit
) {
    val ext = LocalZarnegarColors.current
    val isInflow = tx.txTypeEnum == TxType.SALE || tx.txTypeEnum == TxType.TRANSFER_TO_SHOP || tx.txTypeEnum == TxType.INVESTOR_DEPOSIT
    MinimalCard(contentPadding = PaddingValues(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "${tx.txTypeEnum.labelFa} • ${tx.categoryEnum.labelFa}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val meta = buildString {
                    if (!tx.customerName.isNullOrBlank()) append("مشتری: ${tx.customerName} • ")
                    if (!tx.sellerName.isNullOrBlank()) append("فروشنده: ${tx.sellerName} • ")
                    append(tx.accountName)
                }
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = ext.mutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${Formatters.formatGrams(tx.weightGrams)} • ${Formatters.formatMillis(tx.effectiveTimeMillis)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ext.mutedText
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = Formatters.formatToman(tx.totalAmountToman, false),
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = if (isInflow) ext.profit else ext.loss,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onPrint,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Print,
                        contentDescription = "چاپ فاکتور",
                        tint = ext.mutedText,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PricingModeDialog(
    currentSnapshot: MarketSnapshot,
    currentConfig: ManualPricingConfig,
    onDismiss: () -> Unit,
    onSave: (useManual: Boolean, g18: Double, mesghal: Double, g24: Double, usd: Double) -> Unit
) {
    val ext = LocalZarnegarColors.current
    var useManual by remember { mutableStateOf(currentConfig.useManualPrice) }
    var g18Input by remember {
        mutableStateOf(
            (if (currentConfig.manualGold18kToman > 0) currentConfig.manualGold18kToman else currentSnapshot.gold18kGramToman).toLong().toString()
        )
    }
    var mesghalInput by remember {
        mutableStateOf(
            (if (currentConfig.manualMeltedMesghalToman > 0) currentConfig.manualMeltedMesghalToman else currentSnapshot.meltedMesghalToman).toLong().toString()
        )
    }
    var g24Input by remember {
        mutableStateOf(
            (if (currentConfig.manualBullion24kToman > 0) currentConfig.manualBullion24kToman else currentSnapshot.gold24kGramToman).toLong().toString()
        )
    }
    var usdInput by remember {
        mutableStateOf(
            (if (currentConfig.manualUsdToman > 0) currentConfig.manualUsdToman else currentSnapshot.usdToToman).toLong().toString()
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ext.card,
        title = { Text("تنظیم منبع قیمت طلا و دلار", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !useManual,
                        onClick = { useManual = false },
                        label = { Text("استعلام آنلاین (API)") },
                        modifier = Modifier.weight(1f).testTag("pricing_mode_online")
                    )
                    FilterChip(
                        selected = useManual,
                        onClick = { useManual = true },
                        label = { Text("ورود دستی نرخ") },
                        modifier = Modifier.weight(1f).testTag("pricing_mode_manual")
                    )
                }

                if (!useManual) {
                    Text(
                        text = "نرخ انس جهانی به صورت زنده از API استعلام و با دلار آزاد به تومان تبدیل می‌شود. نرخ دلار زیر جهت کالیبراسیون بازار آزاد استفاده می‌شود:",
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.mutedText
                    )
                    OutlinedTextField(
                        value = usdInput,
                        onValueChange = { usdInput = it },
                        label = { Text("نرخ دلار آزاد (تومان)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = g18Input,
                        onValueChange = {
                            g18Input = it
                            val g18 = Formatters.parseDoubleSafe(it)
                            if (g18 > 0) {
                                mesghalInput = (g18 * 4.3318).toLong().toString()
                                g24Input = (g18 * (999.0 / 750.0)).toLong().toString()
                            }
                        },
                        label = { Text("گرم طلای ۱۸ عیار (تومان)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("manual_gold18k_input")
                    )
                    OutlinedTextField(
                        value = mesghalInput,
                        onValueChange = { mesghalInput = it },
                        label = { Text("مثقال طلای آبشده (تومان)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = g24Input,
                        onValueChange = { g24Input = it },
                        label = { Text("گرم شمش ۲۴ عیار (تومان)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = usdInput,
                        onValueChange = { usdInput = it },
                        label = { Text("نرخ دلار (تومان)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        useManual,
                        Formatters.parseDoubleSafe(g18Input),
                        Formatters.parseDoubleSafe(mesghalInput),
                        Formatters.parseDoubleSafe(g24Input),
                        Formatters.parseDoubleSafe(usdInput)
                    )
                },
                modifier = Modifier.testTag("save_pricing_button")
            ) {
                Text("اعمال روی تابلو")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}
