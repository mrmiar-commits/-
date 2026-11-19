package com.example.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.data.local.ZarnegarLocalRepository
import com.example.model.*
import com.example.security.SecurityPreferences
import com.example.utils.Formatters
import com.example.utils.PriceAlertNotifier
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SaleProfitPreview(
    val goldBaseValueToman: Double,
    val wageAmountToman: Double,
    val profitMarginAmountToman: Double,
    val taxAmountToman: Double,
    val totalAmountToman: Double,
    val costBasisToman: Double,
    val netProfitToman: Double
)

data class GoldBreakdownSummary(
    val meltedGrams: Double = 0.0,
    val bullionGrams: Double = 0.0,
    val newDisplayGrams: Double = 0.0,
    val usedNoWageGrams: Double = 0.0,
    val otherGoldGrams: Double = 0.0,
    val totalGoldGrams: Double = 0.0,
    val totalUsdBalance: Double = 0.0
)

data class DashboardSummary(
    val todaySalesToman: Double = 0.0,
    val todayProfitToman: Double = 0.0,
    val totalProfitToman: Double = 0.0,
    val totalExpensesToman: Double = 0.0,
    val netProfitAfterExpensesToman: Double = 0.0,
    val totalCashAccountsToman: Double = 0.0,
    val totalAccountsGoldEquivGrams: Double = 0.0,
    val totalUsdBalance: Double = 0.0,
    val totalInventoryGrams: Double = 0.0,
    val totalInvestorGrams: Double = 0.0,
    val pendingVoiceSyncCount: Int = 0,
    val goldBreakdown: GoldBreakdownSummary = GoldBreakdownSummary()
)

class ZarnegarViewModel(
    private val localRepo: ZarnegarLocalRepository,
    private val cloudRepo: ZarnegarRepository,
    private val inventoryService: GoldInventoryService,
    private val marketRepo: MarketRateRepository,
    private val sheetsService: GoogleSheetsSyncService,
    private val gatewayService: PaymentGatewayService,
    private val prefs: SecurityPreferences
) : ViewModel() {

    private val _marketState = MutableStateFlow<UiState<MarketSnapshot>>(UiState.Loading)
    val marketState: StateFlow<UiState<MarketSnapshot>> = _marketState.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isSyncingSheets = MutableStateFlow(false)
    val isSyncingSheets: StateFlow<Boolean> = _isSyncingSheets.asStateFlow()

    private val _isTestingWebhook = MutableStateFlow(false)
    val isTestingWebhook: StateFlow<Boolean> = _isTestingWebhook.asStateFlow()

    private val _pendingGatewayCheckout = MutableStateFlow<Pair<GoldTransaction, PaymentGatewayCheckoutResult>?>(null)
    val pendingGatewayCheckout: StateFlow<Pair<GoldTransaction, PaymentGatewayCheckoutResult>?> = _pendingGatewayCheckout.asStateFlow()

    val isDarkTheme: StateFlow<Boolean> = prefs.observeDarkTheme()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    val manualPricing: StateFlow<ManualPricingConfig> = prefs.observeManualPricing()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), ManualPricingConfig())

    val calibratedUsdToman: StateFlow<Double?> = prefs.observeCalibratedUsdToman()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val settings: StateFlow<UserSettings> = prefs.observeLocalSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UserSettings())

    val accounts: StateFlow<List<ShopAccount>> = localRepo.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val inventory: StateFlow<List<InventoryItem>> = localRepo.observeInventory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val customers: StateFlow<List<Customer>> = localRepo.observeCustomers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val investors: StateFlow<List<Investor>> = localRepo.observeInvestors()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val transactions: StateFlow<List<GoldTransaction>> = localRepo.observeTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val sellers: StateFlow<List<ShopSeller>> = localRepo.observeSellers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val sellerMovements: StateFlow<List<SellerMovement>> = localRepo.observeSellerMovements()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val expenses: StateFlow<List<ShopExpense>> = localRepo.observeExpenses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val voiceNotes: StateFlow<List<VoiceNoteRecord>> = localRepo.observeVoiceNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val priceAlerts: StateFlow<List<PriceAlert>> = localRepo.observePriceAlerts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val dashboardSummary: StateFlow<DashboardSummary> = combine(
        combine(transactions, accounts, inventory, investors) { a, b, c, d -> Quad(a, b, c, d) },
        combine(expenses, voiceNotes, marketState) { e, v, m -> Triple(e, v, m) }
    ) { (txs, accs, stock, invs), (exps, voices, mState) ->
        val snap = (mState as? UiState.Success)?.data ?: MarketSnapshot()
        val gold18kRate = snap.gold18kGramToman.coerceAtLeast(1.0)
        val usdRate = snap.usdToToman

        val startOfDay = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        val sales = txs.filter { it.txTypeEnum == TxType.SALE }
        val todaySales = sales.filter { it.effectiveTimeMillis >= startOfDay }

        val melted = stock.filter { it.categoryEnum == GoldCategory.MELTED_GOLD }.sumOf { it.weightGrams }
        val bullion = stock.filter { it.categoryEnum == GoldCategory.BULLION }.sumOf { it.weightGrams }
        val newDisplay = stock.filter { it.categoryEnum == GoldCategory.NEW_GOLD }.sumOf { it.weightGrams }
        val usedNoWage = stock.filter { it.categoryEnum == GoldCategory.USED_NO_WAGE }.sumOf { it.weightGrams }
        val otherGold = stock.filter { it.categoryEnum == GoldCategory.OTHER_GOLD }.sumOf { it.weightGrams }
        val stockUsd = stock.filter { it.categoryEnum == GoldCategory.USD_CURRENCY }.sumOf { it.quantity.toDouble() }
        val accountUsd = accs.sumOf { it.balanceUsd }
        val totalGold = melted + bullion + newDisplay + usedNoWage + otherGold
        val totalProfit = sales.sumOf { it.calculatedProfitToman }
        val totalExpenses = exps.sumOf { it.amountToman }

        DashboardSummary(
            todaySalesToman = todaySales.sumOf { it.totalAmountToman },
            todayProfitToman = todaySales.sumOf { it.calculatedProfitToman },
            totalProfitToman = totalProfit,
            totalExpensesToman = totalExpenses,
            netProfitAfterExpensesToman = totalProfit - totalExpenses,
            totalCashAccountsToman = accs.sumOf { it.balanceToman },
            totalAccountsGoldEquivGrams = accs.sumOf { it.goldEquivalent18kGrams(gold18kRate, usdRate) },
            totalUsdBalance = accountUsd + stockUsd,
            totalInventoryGrams = totalGold,
            totalInvestorGrams = invs.filter { it.statusEnum == InvestorContractStatus.ACTIVE }.sumOf { it.investedGrams },
            pendingVoiceSyncCount = voices.count { !it.sheetsSynced },
            goldBreakdown = GoldBreakdownSummary(
                meltedGrams = melted,
                bullionGrams = bullion,
                newDisplayGrams = newDisplay,
                usedNoWageGrams = usedNoWage,
                otherGoldGrams = otherGold,
                totalGoldGrams = totalGold,
                totalUsdBalance = accountUsd + stockUsd
            )
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DashboardSummary())

    init {
        viewModelScope.launch {
            localRepo.ensureDefaultSetup()
        }
        startMarketPolling()
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    fun clearStatusMessage() { _statusMessage.value = null }
    fun showMessage(msg: String) { _statusMessage.value = msg }

    fun toggleDarkTheme() {
        viewModelScope.launch {
            prefs.setDarkTheme(!isDarkTheme.value)
        }
    }

    fun setDarkTheme(dark: Boolean) {
        viewModelScope.launch { prefs.setDarkTheme(dark) }
    }

    private fun startMarketPolling() {
        viewModelScope.launch {
            while (isActive) {
                refreshMarketRates()
                delay(45_000L)
            }
        }
    }

    suspend fun refreshMarketRates(appContextForAlerts: Context? = null) {
        val snapshot = marketRepo.fetchLiveMarketSnapshot(
            calibratedUsdToman = calibratedUsdToman.value,
            manualPricing = manualPricing.value
        )
        _marketState.value = UiState.Success(snapshot)

        if (appContextForAlerts != null) {
            PriceAlertNotifier.evaluateAndNotify(appContextForAlerts, priceAlerts.value, snapshot) { alertId ->
                viewModelScope.launch {
                    val target = priceAlerts.value.firstOrNull { it.id == alertId }
                    if (target != null) {
                        localRepo.updatePriceAlertStatus(alertId, target.enabled, true)
                    }
                }
            }
        }
    }

    fun evaluateAlertsWithContext(context: Context) {
        val snapshot = (_marketState.value as? UiState.Success)?.data ?: return
        PriceAlertNotifier.evaluateAndNotify(context, priceAlerts.value, snapshot) { alertId ->
            viewModelScope.launch {
                val target = priceAlerts.value.firstOrNull { it.id == alertId }
                if (target != null) {
                    localRepo.updatePriceAlertStatus(alertId, target.enabled, true)
                }
            }
        }
    }

    fun savePricingSettings(
        useManual: Boolean,
        gold18kToman: Double,
        mesghalToman: Double,
        bullion24kToman: Double,
        usdToman: Double
    ) {
        viewModelScope.launch {
            if (usdToman > 1000.0) {
                prefs.saveCalibratedUsdToman(usdToman)
            }
            prefs.saveManualPricing(
                ManualPricingConfig(
                    useManualPrice = useManual,
                    manualGold18kToman = gold18kToman,
                    manualMeltedMesghalToman = mesghalToman,
                    manualBullion24kToman = bullion24kToman,
                    manualUsdToman = usdToman
                )
            )
            refreshMarketRates()
            _statusMessage.value = if (useManual) {
                "نرخ دستی طلا و ارز روی تابلو ثبت شد"
            } else {
                "استعلام آنلاین نرخ طلا از API فعال شد"
            }
        }
    }

    fun calculateSaleBreakdown(
        category: GoldCategory,
        weightOrAmount: Double,
        karat: Int,
        unitPriceToman: Double,
        wagePercent: Double,
        profitMarginPercent: Double,
        taxPercent: Double,
        costBasisPerUnitToman: Double
    ): SaleProfitPreview {
        if (category == GoldCategory.USD_CURRENCY) {
            val total = weightOrAmount * unitPriceToman
            val cost = weightOrAmount * (if (costBasisPerUnitToman > 0) costBasisPerUnitToman else unitPriceToman * 0.985)
            return SaleProfitPreview(total, 0.0, total - cost, 0.0, total, cost, total - cost)
        }
        val effectiveUnitRate = if (karat > 0 && karat != 750) unitPriceToman * (karat / 750.0) else unitPriceToman
        val baseGold = weightOrAmount * effectiveUnitRate
        val wage = baseGold * (wagePercent / 100.0)
        val profit = (baseGold + wage) * (profitMarginPercent / 100.0)
        val tax = (wage + profit) * (taxPercent / 100.0)
        val total = baseGold + wage + profit + tax
        val effectiveCost = if (costBasisPerUnitToman > 0.0) costBasisPerUnitToman else effectiveUnitRate
        val costBasis = weightOrAmount * effectiveCost
        val netProfit = total - tax - costBasis
        return SaleProfitPreview(baseGold, wage, profit, tax, total, costBasis, netProfit)
    }

    // --- Accounts ---
    fun addShopAccount(name: String, type: AccountType, balanceToman: Double, balanceUsd: Double, goldGrams: Double, accountNumber: String?) {
        viewModelScope.launch {
            val acc = ShopAccount(
                name = name,
                accountType = type.name,
                balanceToman = balanceToman,
                balanceUsd = balanceUsd,
                goldGrams18k = goldGrams,
                accountNumber = accountNumber
            )
            localRepo.addAccount(acc)
                .onSuccess { id ->
                    _statusMessage.value = "حساب / کارت «$name» اضافه شد"
                    mirrorToFirestoreIfSignedIn { uid -> cloudRepo.addAccount(uid, acc.copy(id = id)) }
                }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "خطا در ثبت حساب" }
        }
    }

    fun deleteShopAccount(accountId: String) {
        viewModelScope.launch {
            localRepo.deleteAccount(accountId)
                .onSuccess { _statusMessage.value = "حساب حذف شد" }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "خطا در حذف حساب" }
        }
    }

    // --- Inventory ---
    fun addInventoryItem(
        category: GoldCategory,
        pieceType: GoldPieceType,
        title: String,
        karat: Int,
        weightGrams: Double,
        quantity: Long,
        avgBuyPrice: Double,
        defaultWagePercent: Double,
        labCode: String?
    ) {
        viewModelScope.launch {
            val item = InventoryItem(
                category = category.name,
                pieceType = pieceType.name,
                title = title,
                karat = karat,
                weightGrams = weightGrams,
                quantity = quantity,
                avgBuyPricePerUnit = avgBuyPrice,
                defaultWagePercent = defaultWagePercent,
                labCode = labCode
            )
            localRepo.addInventoryItem(item)
                .onSuccess {
                    _statusMessage.value = "کالا به موجودی انبار اضافه شد"
                    mirrorToFirestoreIfSignedIn { uid -> inventoryService.registerInventoryItem(uid, item) }
                }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "خطا در ثبت موجودی" }
        }
    }

    fun updateInventoryItem(
        existingItem: InventoryItem,
        category: GoldCategory,
        pieceType: GoldPieceType,
        title: String,
        karat: Int,
        weightGrams: Double,
        quantity: Long,
        avgBuyPrice: Double,
        defaultWagePercent: Double,
        labCode: String?
    ) {
        viewModelScope.launch {
            val updated = existingItem.copy(
                category = category.name,
                pieceType = pieceType.name,
                title = title,
                karat = karat,
                weightGrams = weightGrams,
                quantity = quantity,
                avgBuyPricePerUnit = avgBuyPrice,
                defaultWagePercent = defaultWagePercent,
                labCode = labCode
            )
            localRepo.updateInventoryItem(updated)
                .onSuccess { _statusMessage.value = "موجودی «$title» ویرایش شد" }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "خطا در ویرایش موجودی" }
        }
    }

    fun deleteInventoryItem(itemId: String) {
        viewModelScope.launch {
            localRepo.deleteInventoryItem(itemId)
                .onSuccess { _statusMessage.value = "آیتم از انبار حذف شد" }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "خطا در حذف" }
        }
    }

    // --- Sellers (3 to 5 Shop Users) ---
    fun addSeller(name: String, roleTitle: String, phone: String, notes: String?) {
        viewModelScope.launch {
            val seller = ShopSeller(name = name, roleTitle = roleTitle, phone = phone, notes = notes)
            localRepo.addSeller(seller)
                .onSuccess { _statusMessage.value = "فروشنده «$name» تعریف شد" }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "خطا در تعریف فروشنده" }
        }
    }

    fun deleteSeller(sellerId: String) {
        viewModelScope.launch {
            localRepo.deleteSeller(sellerId)
                .onSuccess { _statusMessage.value = "فروشنده حذف شد" }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "خطا در حذف فروشنده" }
        }
    }

    fun recordSellerMovement(
        seller: ShopSeller,
        direction: MovementDirection,
        category: GoldCategory,
        weightGrams: Double,
        karat: Int,
        usdAmount: Double,
        cashToman: Double,
        description: String
    ) {
        viewModelScope.launch {
            val movement = SellerMovement(
                sellerId = seller.id,
                sellerName = seller.name,
                direction = direction.name,
                goldCategory = category.name,
                weightGrams = weightGrams,
                karat = karat,
                usdAmount = usdAmount,
                cashToman = cashToman,
                description = description.ifBlank { "${direction.labelFa} توسط ${seller.name}" }
            )
            localRepo.recordSellerMovement(movement)
                .onSuccess { _statusMessage.value = "${direction.labelFa} برای «${seller.name}» ثبت شد" }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "خطا در ثبت ورود/خروج فروشنده" }
        }
    }

    // --- Shop Expenses ---
    fun addExpense(
        title: String,
        category: ExpenseCategory,
        amountToman: Double,
        account: ShopAccount?,
        sellerName: String?,
        notes: String?
    ) {
        viewModelScope.launch {
            val gold18kRate = (_marketState.value as? UiState.Success)?.data?.gold18kGramToman ?: 0.0
            val goldEquiv = if (gold18kRate > 0.0) amountToman / gold18kRate else 0.0
            val expense = ShopExpense(
                title = title,
                category = category.name,
                amountToman = amountToman,
                goldEquivalentGrams = goldEquiv,
                accountId = account?.id,
                accountName = account?.name ?: "صندوق مغازه",
                sellerName = sellerName,
                notes = notes
            )
            localRepo.recordExpense(expense)
                .onSuccess { _statusMessage.value = "هزینه «$title» ثبت شد" }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "خطا در ثبت هزینه" }
        }
    }

    fun deleteExpense(expenseId: String) {
        viewModelScope.launch {
            localRepo.deleteExpense(expenseId)
                .onSuccess { _statusMessage.value = "هزینه حذف شد" }
        }
    }

    // --- Customers & Investors ---
    fun addCustomer(fullName: String, phone: String, nationalId: String?, notes: String?) {
        viewModelScope.launch {
            localRepo.addCustomer(Customer(fullName = fullName, phone = phone, nationalId = nationalId, notes = notes))
                .onSuccess { _statusMessage.value = "پرونده مشتری «$fullName» ایجاد شد" }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "خطا در ثبت مشتری" }
        }
    }

    fun deleteCustomer(customerId: String) {
        viewModelScope.launch {
            localRepo.deleteCustomer(customerId)
                .onSuccess { _statusMessage.value = "پرونده مشتری حذف شد" }
        }
    }

    fun addInvestor(
        fullName: String,
        phone: String,
        goldCategory: GoldCategory,
        investedGrams: Double,
        investedCashToman: Double,
        profitSharePercent: Double,
        notes: String?
    ) {
        viewModelScope.launch {
            localRepo.addInvestor(
                Investor(
                    fullName = fullName,
                    phone = phone,
                    goldCategory = goldCategory.name,
                    investedGrams = investedGrams,
                    investedCashToman = investedCashToman,
                    profitSharePercent = profitSharePercent,
                    notes = notes
                )
            )
                .onSuccess { _statusMessage.value = "سرمایه‌گذار «$fullName» ثبت شد" }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "خطا در ثبت سرمایه‌گذار" }
        }
    }

    fun settleInvestorProfit(investor: Investor, payoutAmountToman: Double) {
        viewModelScope.launch {
            localRepo.settleInvestorProfit(investor, payoutAmountToman)
                .onSuccess { _statusMessage.value = "تسویه سود سرمایه‌گذار ثبت شد" }
        }
    }

    fun deleteInvestor(investorId: String) {
        viewModelScope.launch {
            localRepo.deleteInvestor(investorId)
                .onSuccess { _statusMessage.value = "سرمایه‌گذار حذف شد" }
        }
    }

    // --- Transactions & Payment Gateway ---
    fun recordTransaction(tx: GoldTransaction) {
        viewModelScope.launch {
            val invoiceNumber = tx.invoiceNumber.ifBlank { "ZN-${System.currentTimeMillis() % 1000000}" }
            val readyTx = tx.copy(invoiceNumber = invoiceNumber)
            localRepo.recordTransaction(readyTx)
                .onSuccess { newId ->
                    val saved = readyTx.copy(id = newId)
                    if (saved.paymentMethodEnum == PaymentMethod.GATEWAY && saved.paymentStatusEnum == PaymentStatus.PENDING_GATEWAY) {
                        val s = settings.value
                        val provider = safeEnum(s.paymentProvider, PaymentProvider.ZARINPAL)
                        gatewayService.createPaymentSession(
                            provider = provider,
                            merchantId = s.merchantId,
                            amountToman = saved.totalAmountToman,
                            invoiceNumber = saved.invoiceNumber,
                            customerName = saved.customerName
                        ).onSuccess { checkout ->
                            _pendingGatewayCheckout.value = saved to checkout
                        }.onFailure { err ->
                            _statusMessage.value = err.localizedMessage ?: "خطا در ایجاد لینک درگاه پرداخت"
                        }
                    }
                    _statusMessage.value = "تراکنش با شماره فاکتور $invoiceNumber ثبت شد"
                }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "خطا در ثبت تراکنش" }
        }
    }

    fun confirmGatewayPayment(tx: GoldTransaction, refId: String) {
        viewModelScope.launch {
            localRepo.confirmGatewayPayment(tx, refId)
                .onSuccess {
                    _pendingGatewayCheckout.value = null
                    _statusMessage.value = "پرداخت درگاه با کد پیگیری $refId تایید شد"
                }
        }
    }

    fun dismissPendingGateway() { _pendingGatewayCheckout.value = null }

    // --- Voice Message Recorder + Offline-to-Google-Sheets Auto-Sync ---
    fun saveVoiceNote(
        context: Context,
        parsed: VoiceNoteRecord,
        alsoRecordAsAccountingEntry: Boolean = false
    ) {
        viewModelScope.launch {
            val isOnline = GoogleSheetsSyncService.isNetworkAvailable(context)
            val recordToSave = parsed.copy(
                createdOffline = !isOnline,
                sheetsSynced = false,
                recordedDateTimeFa = parsed.recordedDateTimeFa.ifBlank { Formatters.formatMillis(parsed.recordedAtMillis) }
            )
            localRepo.saveVoiceNote(recordToSave)
                .onSuccess { saved ->
                    if (alsoRecordAsAccountingEntry) {
                        postVoiceNoteToAccounting(saved)
                    }
                    val webhookUrl = settings.value.sheetsWebhookUrl
                    if (webhookUrl.isNullOrBlank()) {
                        _statusMessage.value = "پیام صوتی ذخیره شد. برای ارسال خودکار، لینک گوگل شیت را در بخش گزارش و شیت ثبت کنید."
                    } else if (isOnline) {
                        val res = sheetsService.appendVoiceNotesViaWebhook(webhookUrl, listOf(saved))
                        if (res.isSuccess) {
                            localRepo.markVoiceNotesSynced(listOf(saved.id))
                            _statusMessage.value = "پیام صوتی تحلیل و با موفقیت در گوگل شیت ثبت شد"
                        } else {
                            SheetsAutoSyncWorker.enqueueWhenOnline(context)
                            _statusMessage.value = "پیام ذخیره شد و در صف ارسال خودکار به گوگل شیت قرار گرفت"
                        }
                    } else {
                        SheetsAutoSyncWorker.enqueueWhenOnline(context)
                        _statusMessage.value = "آفلاین ذخیره شد؛ به محض اتصال اینترنت خودکار در گوگل شیت ثبت می‌شود"
                    }
                }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "خطا در ثبت پیام صوتی" }
        }
    }

    private suspend fun postVoiceNoteToAccounting(v: VoiceNoteRecord) {
        val defaultAcc = accounts.value.firstOrNull() ?: return
        val snap = (_marketState.value as? UiState.Success)?.data ?: MarketSnapshot()
        if (v.actionEnum == VoiceActionType.EXPENSE && v.cashToman > 0.0) {
            val goldEq = if (snap.gold18kGramToman > 0.0) v.cashToman / snap.gold18kGramToman else 0.0
            localRepo.recordExpense(
                ShopExpense(
                    title = v.rawTranscript.take(60),
                    category = ExpenseCategory.OTHER.name,
                    amountToman = v.cashToman,
                    goldEquivalentGrams = goldEq,
                    accountId = defaultAcc.id,
                    accountName = defaultAcc.name,
                    sellerName = v.sellerName,
                    notes = "ثبت خودکار از پیامگیر صوتی"
                )
            )
        } else if (v.weightGrams > 0.0 || v.usdAmount > 0.0) {
            val txType = if (v.actionEnum == VoiceActionType.PURCHASE_IN) TxType.PURCHASE else TxType.SALE
            val qty = if (v.categoryEnum == GoldCategory.USD_CURRENCY) v.usdAmount else v.weightGrams
            val unitRate = if (v.categoryEnum == GoldCategory.USD_CURRENCY) snap.usdToToman else snap.gold18kGramToman
            val total = if (v.cashToman > 0.0) v.cashToman else qty * unitRate
            localRepo.recordTransaction(
                GoldTransaction(
                    txType = txType.name,
                    goldCategory = v.categoryEnum.name,
                    customerName = v.counterpartyName.takeIf { it != "ثبت نشده" },
                    sellerName = v.sellerName,
                    accountId = defaultAcc.id,
                    accountName = defaultAcc.name,
                    weightGrams = qty,
                    karat = v.karat,
                    unitPriceToman = unitRate,
                    costBasisToman = total,
                    totalAmountToman = total,
                    calculatedProfitToman = 0.0,
                    paymentMethod = PaymentMethod.CASH.name,
                    paymentStatus = PaymentStatus.PAID.name,
                    notes = "ثبت از پیامگیر صوتی: ${v.rawTranscript}"
                )
            )
        }
    }

    fun retryPendingVoiceSync(context: Context) {
        viewModelScope.launch {
            val webhookUrl = settings.value.sheetsWebhookUrl
            if (webhookUrl.isNullOrBlank()) {
                _statusMessage.value = "ابتدا لینک وب‌اپ گوگل شیت را در بخش «گزارش و شیت» وارد کنید"
                return@launch
            }
            val pending = localRepo.getUnsyncedVoiceNotes()
            if (pending.isEmpty()) {
                _statusMessage.value = "همه پیام‌های صوتی در گوگل شیت ثبت شده‌اند"
                return@launch
            }
            if (!GoogleSheetsSyncService.isNetworkAvailable(context)) {
                SheetsAutoSyncWorker.enqueueWhenOnline(context)
                _statusMessage.value = "اینترنت قطع است؛ به محض اتصال به‌صورت خودکار ارسال می‌شود"
                return@launch
            }
            _isSyncingSheets.value = true
            sheetsService.appendVoiceNotesViaWebhook(webhookUrl, pending)
                .onSuccess { count ->
                    localRepo.markVoiceNotesSynced(pending.map { it.id })
                    _statusMessage.value = "${Formatters.toPersianDigits(count.toString())} پیام صوتی با موفقیت به گوگل شیت ارسال شد"
                }
                .onFailure {
                    SheetsAutoSyncWorker.enqueueWhenOnline(context)
                    _statusMessage.value = it.localizedMessage ?: "خطا در ارسال به گوگل شیت"
                }
            _isSyncingSheets.value = false
        }
    }

    fun deleteVoiceNote(id: String) {
        viewModelScope.launch {
            localRepo.deleteVoiceNote(id)
                .onSuccess { _statusMessage.value = "پیام صوتی حذف شد" }
        }
    }

    // --- Price Alerts ---
    fun addPriceAlert(symbol: AlertAssetSymbol, condition: AlertCondition, targetPrice: Double) {
        viewModelScope.launch {
            localRepo.addPriceAlert(
                PriceAlert(assetSymbol = symbol.name, assetLabel = symbol.labelFa, condition = condition.name, targetPrice = targetPrice)
            )
                .onSuccess { _statusMessage.value = "هشدار قیمتی ثبت شد" }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "خطا در ثبت هشدار" }
        }
    }

    fun togglePriceAlert(alert: PriceAlert) {
        viewModelScope.launch {
            localRepo.updatePriceAlertStatus(alert.id, enabled = !alert.enabled, triggered = false)
        }
    }

    fun deletePriceAlert(id: String) {
        viewModelScope.launch {
            localRepo.deletePriceAlert(id)
                .onSuccess { _statusMessage.value = "هشدار حذف شد" }
        }
    }

    // --- Google Sheets Connection (Solves Access Blocked) ---
    fun saveShopAndSheetsSettings(
        shopName: String,
        ownerName: String,
        paymentProvider: PaymentProvider,
        merchantId: String?,
        spreadsheetId: String?,
        sheetsWebhookUrl: String?
    ) {
        viewModelScope.launch {
            prefs.saveShopProfile(shopName, ownerName, paymentProvider, merchantId, spreadsheetId, sheetsWebhookUrl)
            _statusMessage.value = "تنظیمات مغازه و اتصال گوگل شیت ذخیره شد"
        }
    }

    fun testSheetsWebhook(url: String) {
        viewModelScope.launch {
            _isTestingWebhook.value = true
            sheetsService.testWebhookConnection(url)
                .onSuccess { sheetName ->
                    prefs.saveSheetsWebhookUrl(url)
                    _statusMessage.value = "اتصال مستقیم به شیت «$sheetName» با موفقیت تایید شد!"
                }
                .onFailure { e ->
                    _statusMessage.value = e.localizedMessage ?: "خطا در تست اتصال گوگل شیت"
                }
            _isTestingWebhook.value = false
        }
    }

    fun syncAllToGoogleSheetsViaWebhook(context: Context) {
        viewModelScope.launch {
            val webhookUrl = settings.value.sheetsWebhookUrl
            if (webhookUrl.isNullOrBlank()) {
                _statusMessage.value = "لطفاً ابتدا لینک وب‌اپ Google Apps Script را وارد و ذخیره کنید"
                return@launch
            }
            if (!GoogleSheetsSyncService.isNetworkAvailable(context)) {
                SheetsAutoSyncWorker.enqueueWhenOnline(context)
                _statusMessage.value = "اینترنت متصل نیست؛ پیام‌ها در صف ارسال خودکار قرار گرفتند"
                return@launch
            }
            _isSyncingSheets.value = true
            val snap = (_marketState.value as? UiState.Success)?.data ?: MarketSnapshot()
            sheetsService.syncAllViaWebhook(
                webhookUrl = webhookUrl,
                transactions = transactions.value,
                accounts = accounts.value,
                customers = customers.value,
                investors = investors.value,
                inventory = inventory.value,
                sellers = sellers.value,
                sellerMovements = sellerMovements.value,
                expenses = expenses.value,
                voiceNotes = voiceNotes.value,
                gold18kPrice = snap.gold18kGramToman,
                usdPrice = snap.usdToToman
            ).onSuccess { count ->
                localRepo.markAllSynced(
                    txIds = transactions.value.map { it.id },
                    expenseIds = expenses.value.map { it.id },
                    movementIds = sellerMovements.value.map { it.id },
                    voiceIds = voiceNotes.value.map { it.id }
                )
                _statusMessage.value = "تمام جداول (${Formatters.toPersianDigits(count.toString())} ردیف) بدون خطا در گوگل شیت ثبت شد"
            }.onFailure { e ->
                _statusMessage.value = e.localizedMessage ?: "خطا در همگام‌سازی گوگل شیت"
            }
            _isSyncingSheets.value = false
        }
    }

    fun syncGoogleSheetsOAuth(
        context: Context,
        onNeedsResolution: (android.app.PendingIntent) -> Unit
    ) {
        viewModelScope.launch {
            _isSyncingSheets.value = true
            when (val res = sheetsService.authorizeSheets(context)) {
                is SheetsAuthResult.Token -> pushLedgersOAuth(res.accessToken)
                is SheetsAuthResult.NeedsResolution -> {
                    _isSyncingSheets.value = false
                    onNeedsResolution(res.pendingIntent)
                }
                is SheetsAuthResult.Failed -> {
                    _isSyncingSheets.value = false
                    _statusMessage.value = res.message
                }
            }
        }
    }

    fun handleSheetsConsentResult(context: Context, data: Intent?) {
        val token = sheetsService.extractTokenFromIntent(context, data)
        if (token.isNullOrBlank()) {
            _statusMessage.value = "دسترسی OAuth توسط گوگل مسدود یا لغو شد. از روش مستقیم وب‌اپ در بالا استفاده کنید."
            return
        }
        viewModelScope.launch {
            _isSyncingSheets.value = true
            pushLedgersOAuth(token)
        }
    }

    private suspend fun pushLedgersOAuth(accessToken: String) {
        val s = settings.value
        val snap = (_marketState.value as? UiState.Success)?.data ?: MarketSnapshot()
        val sheetRes = sheetsService.ensureSpreadsheet(accessToken, s.spreadsheetId, s.shopName)
        if (sheetRes.isFailure) {
            _isSyncingSheets.value = false
            _statusMessage.value = "خطا در ایجاد شیت OAuth؛ لطفاً از اتصال مستقیم وب‌اپ استفاده کنید."
            return
        }
        val sheetId = sheetRes.getOrThrow()
        if (sheetId != s.spreadsheetId) {
            prefs.saveSpreadsheetId(sheetId)
        }
        sheetsService.syncAllLedgersOAuth(
            accessToken = accessToken,
            spreadsheetId = sheetId,
            transactions = transactions.value,
            accounts = accounts.value,
            customers = customers.value,
            investors = investors.value,
            inventory = inventory.value,
            sellers = sellers.value,
            sellerMovements = sellerMovements.value,
            expenses = expenses.value,
            voiceNotes = voiceNotes.value,
            gold18kPrice = snap.gold18kGramToman,
            usdPrice = snap.usdToToman
        ).onSuccess {
            localRepo.markAllSynced(
                txIds = transactions.value.map { it.id },
                expenseIds = expenses.value.map { it.id },
                movementIds = sellerMovements.value.map { it.id },
                voiceIds = voiceNotes.value.map { it.id }
            )
            _statusMessage.value = "همگام‌سازی کامل با Google Sheets انجام شد"
        }.onFailure { e ->
            _statusMessage.value = e.localizedMessage ?: "خطا در ارسال به Google Sheets"
        }
        _isSyncingSheets.value = false
    }

    fun buildCsvExportText(): String =
        sheetsService.buildCsvExport(transactions.value, expenses.value, voiceNotes.value)

    // --- Optional Cloud Backup (strictly gated behind Google Sign-In) ---
    private fun mirrorToFirestoreIfSignedIn(action: suspend (String) -> Unit) {
        val uid = Firebase.auth.currentUser?.uid ?: return
        viewModelScope.launch {
            runCatching { action(uid) }
        }
    }

    fun backupInventoryToFirestoreCloud() {
        val uid = Firebase.auth.currentUser?.uid
        if (uid == null) {
            _statusMessage.value = "برای پشتیبان‌گیری ابری ابتدا حساب گوگل خود را در تنظیمات متصل کنید"
            return
        }
        viewModelScope.launch {
            cloudRepo.ensureDefaultSetup(uid, Firebase.auth.currentUser?.displayName)
            var backedUp = 0
            inventory.value.forEach { item ->
                if (inventoryService.registerInventoryItem(uid, item).isSuccess) backedUp++
            }
            _statusMessage.value = "پشتیبان ابری (${Formatters.toPersianDigits(backedUp.toString())} ردیف انبار) در Firestore ذخیره شد"
        }
    }
}
