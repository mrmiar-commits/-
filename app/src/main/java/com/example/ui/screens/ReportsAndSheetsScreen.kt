package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.GoogleSheetsSyncService
import com.example.model.*
import com.example.ui.auth.onGoogleSignInClicked
import com.example.ui.components.MinimalCard
import com.example.ui.components.MinimalSectionHeader
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalZarnegarColors
import com.example.utils.Formatters
import com.example.utils.InvoicePrinter
import com.example.viewmodel.ZarnegarViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@Composable
fun ReportsAndSheetsScreen(
    viewModel: ZarnegarViewModel,
    initialSubTab: Int = 0
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ext = LocalZarnegarColors.current
    val credentialManager = remember { CredentialManager.create(context) }

    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val sellers by viewModel.sellers.collectAsStateWithLifecycle()
    val sellerMovements by viewModel.sellerMovements.collectAsStateWithLifecycle()
    val voiceNotes by viewModel.voiceNotes.collectAsStateWithLifecycle()
    val summary by viewModel.dashboardSummary.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val priceAlerts by viewModel.priceAlerts.collectAsStateWithLifecycle()
    val marketState by viewModel.marketState.collectAsStateWithLifecycle()
    val isDark by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val isSyncingSheets by viewModel.isSyncingSheets.collectAsStateWithLifecycle()
    val isTestingWebhook by viewModel.isTestingWebhook.collectAsStateWithLifecycle()

    val snapshot = (marketState as? UiState.Success)?.data ?: MarketSnapshot()
    val gold18kRate = snapshot.gold18kGramToman.coerceAtLeast(1.0)
    val usdRate = snapshot.usdToToman

    var selectedSubTab by remember(initialSubTab) { mutableIntStateOf(initialSubTab.coerceIn(0, 1)) }
    var reportPeriod by remember { mutableStateOf(ReportPeriod.MONTH) }

    // Settings editable state
    var webhookInput by remember(settings.sheetsWebhookUrl) { mutableStateOf(settings.sheetsWebhookUrl.orEmpty()) }
    var shopNameInput by remember(settings.shopName) { mutableStateOf(settings.shopName) }
    var ownerNameInput by remember(settings.ownerName) { mutableStateOf(settings.ownerName) }
    var paymentProvider by remember(settings.paymentProvider) {
        mutableStateOf(safeEnum(settings.paymentProvider, PaymentProvider.ZARINPAL))
    }
    var merchantIdInput by remember(settings.merchantId) { mutableStateOf(settings.merchantId.orEmpty()) }
    var showAddAlertDialog by remember { mutableStateOf(false) }
    var showAppsScriptDialog by remember { mutableStateOf(false) }
    var firebaseUserEmail by remember { mutableStateOf(Firebase.auth.currentUser?.email) }

    val sheetsOAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.handleSheetsConsentResult(context, result.data)
        } else {
            viewModel.showMessage("مجوز OAuth لغو شد. از روش مستقیم وب‌اپ بدون خطای Access Blocked استفاده کنید.")
        }
    }

    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        showAddAlertDialog = true
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Sub-tab Switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("گزارش‌گیری مالی", "گوگل شیت و تنظیمات").forEachIndexed { idx, label ->
                val active = selectedSubTab == idx
                Surface(
                    onClick = { selectedSubTab = idx },
                    shape = RoundedCornerShape(12.dp),
                    color = if (active) MaterialTheme.colorScheme.primary else ext.card,
                    border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary else ext.border),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .testTag("reports_tab_$idx")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        if (selectedSubTab == 0) {
            // ========================
            // TAB 0: REPORTING SECTION
            // ========================
            val cutoffMillis = remember(reportPeriod) {
                val days = reportPeriod.days ?: return@remember 0L
                val cal = java.util.Calendar.getInstance()
                if (days == 1) {
                    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                    cal.set(java.util.Calendar.MINUTE, 0)
                    cal.set(java.util.Calendar.SECOND, 0)
                    cal.set(java.util.Calendar.MILLISECOND, 0)
                    cal.timeInMillis
                } else {
                    System.currentTimeMillis() - days * 86_400_000L
                }
            }

            val periodTxs = remember(transactions, cutoffMillis) {
                transactions.filter { it.effectiveTimeMillis >= cutoffMillis }
            }
            val periodSales = remember(periodTxs) { periodTxs.filter { it.txTypeEnum == TxType.SALE } }
            val periodPurchases = remember(periodTxs) { periodTxs.filter { it.txTypeEnum == TxType.PURCHASE } }
            val periodExpenses = remember(expenses, cutoffMillis) {
                expenses.filter { it.timestampMillis >= cutoffMillis }
            }

            val salesTotal = periodSales.sumOf { it.totalAmountToman }
            val purchaseTotal = periodPurchases.sumOf { it.totalAmountToman }
            val grossProfit = periodSales.sumOf { it.calculatedProfitToman }
            val expensesTotal = periodExpenses.sumOf { it.amountToman }
            val netProfit = grossProfit - expensesTotal
            val netProfitGoldGrams = if (gold18kRate > 0) netProfit / gold18kRate else 0.0
            val totalGoldSoldGrams = periodSales.filter { it.categoryEnum != GoldCategory.USD_CURRENCY }.sumOf { it.weightGrams }
            val totalGoldBoughtGrams = periodPurchases.filter { it.categoryEnum != GoldCategory.USD_CURRENCY }.sumOf { it.weightGrams }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Period Chips
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ReportPeriod.entries) { period ->
                            FilterChip(
                                selected = reportPeriod == period,
                                onClick = { reportPeriod = period },
                                label = { Text(period.labelFa) },
                                modifier = Modifier.testTag("report_period_${period.name}")
                            )
                        }
                    }
                }

                // Profit & Loss Summary Card
                item {
                    MinimalCard(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    ) {
                        Text(
                            text = "تراز سود و زیان (${reportPeriod.labelFa})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        HorizontalDivider(color = ext.border)
                        ReportRow("جمع فروش:", Formatters.formatToman(salesTotal), MaterialTheme.colorScheme.onSurface)
                        ReportRow("جمع خرید:", Formatters.formatToman(purchaseTotal), ext.mutedText)
                        ReportRow("سود ناخالص فروش:", Formatters.formatToman(grossProfit), ext.profit)
                        ReportRow("مجموع مخارج مغازه:", Formatters.formatToman(expensesTotal), ext.loss)
                        HorizontalDivider(color = ext.border)
                        ReportRow(
                            "سود خالص پس از مخارج:",
                            Formatters.formatToman(netProfit),
                            if (netProfit >= 0) ext.profit else ext.loss
                        )
                        ReportRow(
                            "معادل طلای ۱۸ عیار سود خالص:",
                            Formatters.formatGrams(netProfitGoldGrams),
                            MaterialTheme.colorScheme.primary
                        )
                        ReportRow(
                            "وزن طلای فروخته‌شده / خریداری‌شده:",
                            "${Formatters.formatGrams(totalGoldSoldGrams)} / ${Formatters.formatGrams(totalGoldBoughtGrams)}",
                            MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Gold Breakdown Report
                item {
                    val gb = summary.goldBreakdown
                    MinimalCard {
                        Text("گزارش موجودی طلا به تفکیک شکل و دلار", style = MaterialTheme.typography.titleSmall)
                        HorizontalDivider(color = ext.border)
                        ReportRow("طلای آبشده:", Formatters.formatGrams(gb.meltedGrams))
                        ReportRow("شمش طلا (۲۴ عیار):", Formatters.formatGrams(gb.bullionGrams))
                        ReportRow("طلای نو ویترینی:", Formatters.formatGrams(gb.newDisplayGrams))
                        ReportRow("طلای کارکرده بدون اجرت:", Formatters.formatGrams(gb.usedNoWageGrams))
                        ReportRow("سایر انواع طلا:", Formatters.formatGrams(gb.otherGoldGrams))
                        ReportRow("جمع کل موجودی طلا:", Formatters.formatGrams(gb.totalGoldGrams), MaterialTheme.colorScheme.primary)
                        ReportRow("موجودی کل دلار:", Formatters.formatUsd(gb.totalUsdBalance), ext.info)
                    }
                }

                // Accounts Gold Equivalent Report
                item {
                    MinimalCard {
                        Text("گزارش حساب‌ها، کارت شخصی و تنخواه (معادل گرمی طلا)", style = MaterialTheme.typography.titleSmall)
                        HorizontalDivider(color = ext.border)
                        if (accounts.isEmpty()) {
                            Text("حسابی ثبت نشده است", style = MaterialTheme.typography.bodySmall, color = ext.mutedText)
                        } else {
                            accounts.forEach { acc ->
                                val eq = acc.goldEquivalent18kGrams(gold18kRate, usdRate)
                                ReportRow(
                                    label = "${acc.name} (${acc.typeEnum.labelFa})",
                                    value = "${Formatters.formatToman(acc.balanceToman, false)} ≈ ${Formatters.formatGrams(eq)}"
                                )
                            }
                        }
                    }
                }

                // Expenses by Category Report
                item {
                    val groupedExp = remember(periodExpenses) {
                        periodExpenses.groupBy { it.categoryEnum }
                            .mapValues { (_, list) -> list.sumOf { it.amountToman } }
                            .entries.sortedByDescending { it.value }
                    }
                    MinimalCard {
                        Text("تفکیک مخارج مغازه (${reportPeriod.labelFa})", style = MaterialTheme.typography.titleSmall)
                        HorizontalDivider(color = ext.border)
                        if (groupedExp.isEmpty()) {
                            Text("در این بازه هزینه‌ای ثبت نشده است.", style = MaterialTheme.typography.bodySmall, color = ext.mutedText)
                        } else {
                            groupedExp.forEach { (cat, sum) ->
                                ReportRow(cat.labelFa, Formatters.formatToman(sum), ext.loss)
                            }
                        }
                    }
                }

                // Sellers Performance Report
                item {
                    MinimalCard {
                        Text("گزارش ورود و خروج فروشندگان مغازه", style = MaterialTheme.typography.titleSmall)
                        HorizontalDivider(color = ext.border)
                        if (sellers.isEmpty()) {
                            Text("فروشنده‌ای تعریف نشده است.", style = MaterialTheme.typography.bodySmall, color = ext.mutedText)
                        } else {
                            sellers.forEach { s ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("${s.name} (${s.roleTitle})", style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        text = "طلا: ورود ${Formatters.formatGrams(s.goldInGrams)} | خروج ${Formatters.formatGrams(s.goldOutGrams)} | تراز ${Formatters.formatGrams(s.netGoldGrams)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ext.mutedText
                                    )
                                    Text(
                                        text = "دلار: ورود ${Formatters.formatUsd(s.usdIn)} | خروج ${Formatters.formatUsd(s.usdOut)} | تراز ${Formatters.formatUsd(s.netUsd)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ext.info
                                    )
                                }
                                HorizontalDivider(color = ext.border)
                            }
                        }
                    }
                }

                // Print PDF & Export CSV
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                val lines = listOf(
                                    "جمع فروش دوره" to Formatters.formatToman(salesTotal),
                                    "جمع خرید دوره" to Formatters.formatToman(purchaseTotal),
                                    "سود ناخالص" to Formatters.formatToman(grossProfit),
                                    "مخارج مغازه" to Formatters.formatToman(expensesTotal),
                                    "سود خالص پس از مخارج" to Formatters.formatToman(netProfit),
                                    "معادل طلای ۱۸ عیار سود خالص" to Formatters.formatGrams(netProfitGoldGrams),
                                    "موجودی کل طلا" to Formatters.formatGrams(summary.goldBreakdown.totalGoldGrams),
                                    "طلای آبشده" to Formatters.formatGrams(summary.goldBreakdown.meltedGrams),
                                    "شمش ۲۴ عیار" to Formatters.formatGrams(summary.goldBreakdown.bullionGrams),
                                    "طلای نو ویترینی" to Formatters.formatGrams(summary.goldBreakdown.newDisplayGrams),
                                    "طلای کارکرده بدون اجرت" to Formatters.formatGrams(summary.goldBreakdown.usedNoWageGrams),
                                    "موجودی دلار" to Formatters.formatUsd(summary.goldBreakdown.totalUsdBalance),
                                    "معادل گرمی حساب‌ها و کارت‌ها" to Formatters.formatGrams(summary.totalAccountsGoldEquivGrams),
                                    "تعداد پیام‌های صوتی" to Formatters.toPersianDigits(voiceNotes.size.toString())
                                )
                                InvoicePrinter.printFinancialReport(context, settings.shopName, reportPeriod.labelFa, lines)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("print_report_pdf_button")
                        ) {
                            Icon(Icons.Outlined.Print, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("چاپ PDF گزارش")
                        }
                        OutlinedButton(
                            onClick = {
                                val csv = viewModel.buildCsvExportText()
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(Intent.EXTRA_SUBJECT, "گزارش زرنگار - ${reportPeriod.labelFa}")
                                    putExtra(Intent.EXTRA_TEXT, csv)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "اشتراک‌گذاری گزارش CSV"))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("share_csv_button")
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("خروجی CSV")
                        }
                    }
                }

                item { Spacer(Modifier.height(20.dp)) }
            }
        } else {
            // =====================================
            // TAB 1: GOOGLE SHEETS & APP SETTINGS
            // =====================================
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Light & Dark Theme Switcher
                item {
                    MinimalCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("حالت ظاهری برنامه", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    if (isDark) "تم تاریک مینیمال فعال است" else "تم روشن مینیمال فعال است",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ext.mutedText
                                )
                            }
                            Switch(
                                checked = isDark,
                                onCheckedChange = { viewModel.setDarkTheme(it) },
                                modifier = Modifier.testTag("settings_theme_switch")
                            )
                        }
                    }
                }

                // 2. Direct Google Sheets Connection (Solves Error 403: Access Blocked!)
                item {
                    MinimalCard(
                        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "اتصال مستقیم گوگل شیت (رفع ارور Access Blocked)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "ارسال آنلاین و آفلاین تراکنش‌ها، پیامگیر صوتی، مخارج و فروشندگان بدون محدودیت تایید OAuth گوگل",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ext.mutedText
                                )
                            }
                        }

                        HorizontalDivider(color = ext.border)

                        Text(
                            text = "راهنمای ۳ مرحله‌ای اتصال دائمی:\n۱. در گوگل شیت خود به مسیر Extensions → Apps Script بروید.\n۲. کد آماده زیر را جای‌گذاری کرده و روی Deploy → New deployment → Web app بزنید (دسترسی: Anyone).\n۳. لینک تولیدشده (script.google.com/macros/s/.../exec) را پایین وارد کنید:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Zarnegar Apps Script", GoogleSheetsSyncService.APPS_SCRIPT_TEMPLATE))
                                    viewModel.showMessage("کد آماده Apps Script در کلیپ‌بورد کپی شد")
                                },
                                modifier = Modifier.weight(1f).testTag("copy_apps_script_button")
                            ) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("کپی کد اسکریپت")
                            }
                            TextButton(onClick = { showAppsScriptDialog = true }) {
                                Text("مشاهده کد")
                            }
                        }

                        OutlinedTextField(
                            value = webhookInput,
                            onValueChange = { webhookInput = it },
                            label = { Text("لینک وب‌اپ Google Apps Script") },
                            placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("sheets_webhook_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.testSheetsWebhook(webhookInput) },
                                enabled = !isTestingWebhook && webhookInput.isNotBlank(),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("test_webhook_button")
                            ) {
                                if (isTestingWebhook) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("تست و ذخیره")
                                }
                            }
                            Button(
                                onClick = {
                                    if (webhookInput.isNotBlank() && webhookInput != settings.sheetsWebhookUrl) {
                                        viewModel.saveShopAndSheetsSettings(
                                            shopNameInput, ownerNameInput, paymentProvider,
                                            merchantIdInput.takeIf { it.isNotBlank() },
                                            settings.spreadsheetId, webhookInput
                                        )
                                    }
                                    viewModel.syncAllToGoogleSheetsViaWebhook(context)
                                },
                                enabled = !isSyncingSheets,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("sync_sheets_now_button")
                            ) {
                                if (isSyncingSheets) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Text("همگام‌سازی شیت")
                                }
                            }
                        }

                        TextButton(
                            onClick = {
                                viewModel.syncGoogleSheetsOAuth(context) { pendingIntent ->
                                    sheetsOAuthLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                                }
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("روش جایگزین: اتصال با OAuth گوگل", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // 3. Price Alerts
                item {
                    MinimalCard {
                        MinimalSectionHeader(
                            title = "هشدارهای نوسان قیمت",
                            subtitle = "${Formatters.toPersianDigits(priceAlerts.size.toString())} هشدار فعال",
                            trailing = {
                                FilledTonalButton(onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        showAddAlertDialog = true
                                    }
                                }) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("هشدار جدید")
                                }
                            }
                        )
                        if (priceAlerts.isEmpty()) {
                            Text("هشداری تعریف نشده است.", style = MaterialTheme.typography.bodySmall, color = ext.mutedText)
                        } else {
                            priceAlerts.forEach { alert ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${alert.assetEnum.labelFa} (${alert.conditionEnum.labelFa})", style = MaterialTheme.typography.labelLarge)
                                        Text(
                                            text = "${Formatters.formatToman(alert.targetPrice, false)} ${alert.assetEnum.unitFa}",
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(checked = alert.enabled, onCheckedChange = { viewModel.togglePriceAlert(alert) })
                                        IconButton(onClick = { viewModel.deletePriceAlert(alert.id) }) {
                                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "حذف هشدار", tint = ext.mutedText)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Shop Profile & Payment Gateway Settings
                item {
                    MinimalCard {
                        Text("اطلاعات مغازه و درگاه پرداخت", style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(
                            value = shopNameInput,
                            onValueChange = { shopNameInput = it },
                            label = { Text("نام طلافروشی") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = ownerNameInput,
                            onValueChange = { ownerNameInput = it },
                            label = { Text("نام مدیریت") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(PaymentProvider.entries) { p ->
                                FilterChip(
                                    selected = paymentProvider == p,
                                    onClick = { paymentProvider = p },
                                    label = { Text(p.labelFa) }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = merchantIdInput,
                            onValueChange = { merchantIdInput = it },
                            label = { Text("کد پذیرنده درگاه (Merchant ID / لینک)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                viewModel.saveShopAndSheetsSettings(
                                    shopName = shopNameInput,
                                    ownerName = ownerNameInput,
                                    paymentProvider = paymentProvider,
                                    merchantId = merchantIdInput.takeIf { it.isNotBlank() },
                                    spreadsheetId = settings.spreadsheetId,
                                    sheetsWebhookUrl = webhookInput.takeIf { it.isNotBlank() }
                                )
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Text("ذخیره تنظیمات مغازه")
                        }
                    }
                }

                // 5. Optional Cloud Backup (Strictly Optional — Zero Launch Login Wall)
                item {
                    MinimalCard {
                        Text("پشتیبان ابری اختیاری (Firestore)", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = if (firebaseUserEmail != null) {
                                "متصل به حساب گوگل: $firebaseUserEmail"
                            } else {
                                "برنامه به صورت کاملاً آفلاین و بدون نیاز به ورود کار می‌کند. در صورت تمایل می‌توانید برای پشتیبان ابری متصل شوید."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.mutedText
                        )
                        if (firebaseUserEmail == null) {
                            OutlinedButton(
                                onClick = {
                                    onGoogleSignInClicked(
                                        context = context,
                                        credentialManager = credentialManager,
                                        onAuthSuccess = {
                                            firebaseUserEmail = Firebase.auth.currentUser?.email ?: "متصل"
                                            viewModel.showMessage("حساب گوگل برای پشتیبان ابری متصل شد")
                                        },
                                        onAuthError = { viewModel.showMessage(it) },
                                        scope = scope
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("اتصال اختیاری حساب گوگل برای بکاپ")
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { viewModel.backupInventoryToFirestoreCloud() },
                                    modifier = Modifier.weight(1f)
                                ) { Text("ارسال بکاپ انبار") }
                                OutlinedButton(
                                    onClick = {
                                        Firebase.auth.signOut()
                                        firebaseUserEmail = null
                                        viewModel.showMessage("اتصال بکاپ ابری قطع شد")
                                    }
                                ) { Text("قطع اتصال") }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }

    if (showAddAlertDialog) {
        AddPriceAlertDialog(
            snapshot = snapshot,
            onDismiss = { showAddAlertDialog = false },
            onSave = { sym, cond, target ->
                viewModel.addPriceAlert(sym, cond, target)
                showAddAlertDialog = false
            }
        )
    }

    if (showAppsScriptDialog) {
        AlertDialog(
            onDismissRequest = { showAppsScriptDialog = false },
            containerColor = ext.card,
            title = { Text("کد Google Apps Script") },
            text = {
                Text(
                    text = GoogleSheetsSyncService.APPS_SCRIPT_TEMPLATE,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily)
                )
            },
            confirmButton = {
                Button(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Zarnegar Apps Script", GoogleSheetsSyncService.APPS_SCRIPT_TEMPLATE))
                    viewModel.showMessage("کد اسکریپت کپی شد")
                    showAppsScriptDialog = false
                }) { Text("کپی کد") }
            },
            dismissButton = {
                TextButton(onClick = { showAppsScriptDialog = false }) { Text("بستن") }
            }
        )
    }
}

@Composable
private fun ReportRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    val ext = LocalZarnegarColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = ext.mutedText,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily),
            color = valueColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun AddPriceAlertDialog(
    snapshot: MarketSnapshot,
    onDismiss: () -> Unit,
    onSave: (AlertAssetSymbol, AlertCondition, Double) -> Unit
) {
    val ext = LocalZarnegarColors.current
    var symbol by remember { mutableStateOf(AlertAssetSymbol.GOLD_18K) }
    var condition by remember { mutableStateOf(AlertCondition.ABOVE) }
    var targetStr by remember { mutableStateOf(snapshot.gold18kGramToman.toLong().toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ext.card,
        title = { Text("ثبت هشدار قیمتی") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(AlertAssetSymbol.entries) { s ->
                        FilterChip(
                            selected = symbol == s,
                            onClick = {
                                symbol = s
                                targetStr = when (s) {
                                    AlertAssetSymbol.GOLD_18K -> snapshot.gold18kGramToman.toLong().toString()
                                    AlertAssetSymbol.MELTED_MESGHAL -> snapshot.meltedMesghalToman.toLong().toString()
                                    AlertAssetSymbol.BULLION_24K -> snapshot.gold24kGramToman.toLong().toString()
                                    AlertAssetSymbol.XAU_OUNCE -> snapshot.xauOunceUsd.toLong().toString()
                                    AlertAssetSymbol.USD_RATE -> snapshot.usdToToman.toLong().toString()
                                }
                            },
                            label = { Text(s.labelFa) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AlertCondition.entries.forEach { c ->
                        FilterChip(
                            selected = condition == c,
                            onClick = { condition = c },
                            label = { Text(c.labelFa) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                OutlinedTextField(
                    value = targetStr,
                    onValueChange = { targetStr = it },
                    label = { Text("قیمت هدف (${symbol.unitFa})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val t = Formatters.parseDoubleSafe(targetStr)
                if (t > 0) onSave(symbol, condition, t)
            }) { Text("ثبت هشدار") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
