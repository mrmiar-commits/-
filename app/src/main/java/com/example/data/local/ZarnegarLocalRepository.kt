package com.example.data.local

import com.example.model.*
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Date
import java.util.UUID

class ZarnegarLocalRepository(
    private val dao: ZarnegarDao
) {
    private fun newId(prefix: String): String = "${prefix}_${UUID.randomUUID().toString().take(12)}"

    suspend fun ensureDefaultSetup() {
        if (dao.countAccounts() == 0) {
            val now = System.currentTimeMillis()
            dao.upsertAccount(
                AccountEntity(
                    id = "acc_cash_main",
                    name = "صندوق نقدی مغازه",
                    accountType = AccountType.CASH_REGISTER.name,
                    balanceToman = 0.0,
                    balanceUsd = 0.0,
                    goldGrams18k = 0.0,
                    accountNumber = null,
                    createdAtMillis = now
                )
            )
            dao.upsertAccount(
                AccountEntity(
                    id = "acc_bank_pos",
                    name = "حساب بانکی و کارتخوان",
                    accountType = AccountType.BANK_POS.name,
                    balanceToman = 0.0,
                    balanceUsd = 0.0,
                    goldGrams18k = 0.0,
                    accountNumber = null,
                    createdAtMillis = now + 1
                )
            )
            dao.upsertAccount(
                AccountEntity(
                    id = "acc_petty_cash",
                    name = "تنخواه روزانه مغازه",
                    accountType = AccountType.PETTY_CASH.name,
                    balanceToman = 0.0,
                    balanceUsd = 0.0,
                    goldGrams18k = 0.0,
                    accountNumber = null,
                    createdAtMillis = now + 2
                )
            )
            dao.upsertAccount(
                AccountEntity(
                    id = "acc_gold_safe",
                    name = "گاوصندوق طلا",
                    accountType = AccountType.GOLD_SAFE.name,
                    balanceToman = 0.0,
                    balanceUsd = 0.0,
                    goldGrams18k = 0.0,
                    accountNumber = null,
                    createdAtMillis = now + 3
                )
            )
        }
    }

    // --- Observe Streams ---
    fun observeAccounts(): Flow<List<ShopAccount>> = dao.observeAccounts().map { list ->
        list.map { it.toDomain() }
    }

    fun observeInventory(): Flow<List<InventoryItem>> = dao.observeInventory().map { list ->
        list.map { it.toDomain() }
    }

    fun observeCustomers(): Flow<List<Customer>> = dao.observeCustomers().map { list ->
        list.map { it.toDomain() }
    }

    fun observeInvestors(): Flow<List<Investor>> = dao.observeInvestors().map { list ->
        list.map { it.toDomain() }
    }

    fun observeTransactions(): Flow<List<GoldTransaction>> = dao.observeTransactions().map { list ->
        list.map { it.toDomain() }
    }

    fun observeSellers(): Flow<List<ShopSeller>> = dao.observeSellers().map { list ->
        list.map { it.toDomain() }
    }

    fun observeSellerMovements(): Flow<List<SellerMovement>> = dao.observeSellerMovements().map { list ->
        list.map { it.toDomain() }
    }

    fun observeExpenses(): Flow<List<ShopExpense>> = dao.observeExpenses().map { list ->
        list.map { it.toDomain() }
    }

    fun observeVoiceNotes(): Flow<List<VoiceNoteRecord>> = dao.observeVoiceNotes().map { list ->
        list.map { it.toDomain() }
    }

    fun observePriceAlerts(): Flow<List<PriceAlert>> = dao.observePriceAlerts().map { list ->
        list.map { it.toDomain() }
    }

    // --- Accounts ---
    suspend fun addAccount(account: ShopAccount): Result<String> = runCatching {
        require(account.name.isNotBlank()) { "نام حساب یا کارت الزامی است." }
        val id = account.id.ifBlank { newId("acc") }
        dao.upsertAccount(
            AccountEntity(
                id = id,
                name = account.name.trim(),
                accountType = account.typeEnum.name,
                balanceToman = account.balanceToman,
                balanceUsd = account.balanceUsd,
                goldGrams18k = account.goldGrams18k,
                accountNumber = account.accountNumber?.trim()?.takeIf { it.isNotEmpty() },
                createdAtMillis = System.currentTimeMillis()
            )
        )
        id
    }

    suspend fun deleteAccount(id: String): Result<Unit> = runCatching {
        dao.deleteAccount(id)
    }

    // --- Inventory ---
    suspend fun addInventoryItem(item: InventoryItem): Result<String> = runCatching {
        require(item.title.isNotBlank()) { "عنوان کالا الزامی است." }
        val id = item.id.ifBlank { newId("inv") }
        dao.upsertInventory(
            InventoryEntity(
                id = id,
                category = item.categoryEnum.name,
                pieceType = item.pieceTypeEnum.name,
                title = item.title.trim(),
                karat = item.karat.coerceIn(0, 1000),
                weightGrams = item.weightGrams.coerceAtLeast(0.0),
                quantity = item.quantity.coerceAtLeast(0L),
                avgBuyPricePerUnit = item.avgBuyPricePerUnit.coerceAtLeast(0.0),
                defaultWagePercent = item.defaultWagePercent.coerceIn(0.0, 100.0),
                labCode = item.labCode?.trim()?.takeIf { it.isNotEmpty() },
                createdAtMillis = item.createdAt?.toDate()?.time ?: System.currentTimeMillis()
            )
        )
        id
    }

    suspend fun updateInventoryItem(item: InventoryItem): Result<Unit> = runCatching {
        require(item.id.isNotBlank())
        require(item.title.isNotBlank())
        val existing = dao.getInventoryById(item.id)
        dao.upsertInventory(
            InventoryEntity(
                id = item.id,
                category = item.categoryEnum.name,
                pieceType = item.pieceTypeEnum.name,
                title = item.title.trim(),
                karat = item.karat.coerceIn(0, 1000),
                weightGrams = item.weightGrams.coerceAtLeast(0.0),
                quantity = item.quantity.coerceAtLeast(0L),
                avgBuyPricePerUnit = item.avgBuyPricePerUnit.coerceAtLeast(0.0),
                defaultWagePercent = item.defaultWagePercent.coerceIn(0.0, 100.0),
                labCode = item.labCode?.trim()?.takeIf { it.isNotEmpty() },
                createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteInventoryItem(id: String): Result<Unit> = runCatching {
        dao.deleteInventory(id)
    }

    // --- Customers ---
    suspend fun addCustomer(customer: Customer): Result<String> = runCatching {
        require(customer.fullName.isNotBlank()) { "نام مشتری الزامی است." }
        val id = customer.id.ifBlank { newId("cust") }
        dao.upsertCustomer(
            CustomerEntity(
                id = id,
                fullName = customer.fullName.trim(),
                phone = customer.phone.trim().ifBlank { "-" },
                nationalId = customer.nationalId?.trim()?.takeIf { it.isNotEmpty() },
                goldBalanceGrams = customer.goldBalanceGrams,
                cashBalanceToman = customer.cashBalanceToman,
                totalPurchasedToman = customer.totalPurchasedToman.coerceAtLeast(0.0),
                notes = customer.notes?.trim()?.takeIf { it.isNotEmpty() },
                createdAtMillis = System.currentTimeMillis()
            )
        )
        id
    }

    suspend fun deleteCustomer(id: String): Result<Unit> = runCatching {
        dao.deleteCustomer(id)
    }

    // --- Investors ---
    suspend fun addInvestor(investor: Investor): Result<String> = runCatching {
        require(investor.fullName.isNotBlank()) { "نام سرمایه‌گذار الزامی است." }
        val id = investor.id.ifBlank { newId("invs") }
        dao.upsertInvestor(
            InvestorEntity(
                id = id,
                fullName = investor.fullName.trim(),
                phone = investor.phone.trim().ifBlank { "-" },
                goldCategory = investor.categoryEnum.name,
                investedGrams = investor.investedGrams.coerceAtLeast(0.0),
                investedCashToman = investor.investedCashToman.coerceAtLeast(0.0),
                profitSharePercent = investor.profitSharePercent.coerceIn(0.0, 100.0),
                realizedProfitToman = investor.realizedProfitToman,
                contractStatus = investor.statusEnum.name,
                notes = investor.notes?.trim()?.takeIf { it.isNotEmpty() },
                createdAtMillis = System.currentTimeMillis()
            )
        )
        id
    }

    suspend fun settleInvestorProfit(investor: Investor, payoutAmountToman: Double): Result<Unit> = runCatching {
        val current = dao.getInvestorById(investor.id) ?: return@runCatching
        dao.upsertInvestor(current.copy(realizedProfitToman = current.realizedProfitToman + payoutAmountToman))
    }

    suspend fun deleteInvestor(id: String): Result<Unit> = runCatching {
        dao.deleteInvestor(id)
    }

    // --- Sellers (3 to 5 Shop Users) ---
    suspend fun addSeller(seller: ShopSeller): Result<String> = runCatching {
        require(seller.name.isNotBlank()) { "نام فروشنده الزامی است." }
        val count = dao.countSellers()
        require(count < 5) { "حداکثر ۵ فروشنده برای مغازه قابل تعریف است." }
        val id = seller.id.ifBlank { newId("slr") }
        dao.upsertSeller(
            SellerEntity(
                id = id,
                name = seller.name.trim(),
                roleTitle = seller.roleTitle.trim().ifBlank { "فروشنده مغازه" },
                phone = seller.phone.trim().ifBlank { "-" },
                goldInGrams = seller.goldInGrams.coerceAtLeast(0.0),
                goldOutGrams = seller.goldOutGrams.coerceAtLeast(0.0),
                usdIn = seller.usdIn.coerceAtLeast(0.0),
                usdOut = seller.usdOut.coerceAtLeast(0.0),
                cashInToman = seller.cashInToman.coerceAtLeast(0.0),
                cashOutToman = seller.cashOutToman.coerceAtLeast(0.0),
                active = seller.active,
                notes = seller.notes?.trim()?.takeIf { it.isNotEmpty() },
                createdAtMillis = System.currentTimeMillis()
            )
        )
        id
    }

    suspend fun deleteSeller(id: String): Result<Unit> = runCatching {
        dao.deleteSeller(id)
    }

    suspend fun recordSellerMovement(movement: SellerMovement): Result<String> = runCatching {
        val seller = dao.getSellerById(movement.sellerId)
            ?: throw IllegalStateException("فروشنده یافت نشد.")
        val id = movement.id.ifBlank { newId("smv") }
        val now = if (movement.timestampMillis > 0L) movement.timestampMillis else System.currentTimeMillis()
        dao.upsertSellerMovement(
            SellerMovementEntity(
                id = id,
                sellerId = seller.id,
                sellerName = seller.name,
                direction = movement.directionEnum.name,
                goldCategory = movement.categoryEnum.name,
                weightGrams = movement.weightGrams.coerceAtLeast(0.0),
                karat = movement.karat.coerceIn(0, 1000),
                usdAmount = movement.usdAmount.coerceAtLeast(0.0),
                cashToman = movement.cashToman.coerceAtLeast(0.0),
                description = movement.description.trim(),
                timestampMillis = now,
                sheetsSynced = false
            )
        )
        val updatedSeller = if (movement.directionEnum == MovementDirection.IN) {
            seller.copy(
                goldInGrams = seller.goldInGrams + movement.weightGrams.coerceAtLeast(0.0),
                usdIn = seller.usdIn + movement.usdAmount.coerceAtLeast(0.0),
                cashInToman = seller.cashInToman + movement.cashToman.coerceAtLeast(0.0)
            )
        } else {
            seller.copy(
                goldOutGrams = seller.goldOutGrams + movement.weightGrams.coerceAtLeast(0.0),
                usdOut = seller.usdOut + movement.usdAmount.coerceAtLeast(0.0),
                cashOutToman = seller.cashOutToman + movement.cashToman.coerceAtLeast(0.0)
            )
        }
        dao.upsertSeller(updatedSeller)
        id
    }

    suspend fun deleteSellerMovement(id: String): Result<Unit> = runCatching {
        dao.deleteSellerMovement(id)
    }

    // --- Shop Expenses ---
    suspend fun recordExpense(expense: ShopExpense): Result<String> = runCatching {
        require(expense.title.isNotBlank()) { "عنوان هزینه الزامی است." }
        require(expense.amountToman > 0.0) { "مبلغ هزینه باید بزرگتر از صفر باشد." }
        val id = expense.id.ifBlank { newId("exp") }
        val now = if (expense.timestampMillis > 0L) expense.timestampMillis else System.currentTimeMillis()
        dao.upsertExpense(
            ExpenseEntity(
                id = id,
                title = expense.title.trim(),
                category = expense.categoryEnum.name,
                amountToman = expense.amountToman,
                goldEquivalentGrams = expense.goldEquivalentGrams.coerceAtLeast(0.0),
                accountId = expense.accountId,
                accountName = expense.accountName.ifBlank { "صندوق مغازه" },
                sellerName = expense.sellerName?.trim()?.takeIf { it.isNotEmpty() },
                notes = expense.notes?.trim()?.takeIf { it.isNotEmpty() },
                timestampMillis = now,
                sheetsSynced = false
            )
        )
        // Deduct from selected shop account / petty cash / personal card
        expense.accountId?.let { accId ->
            dao.getAccountById(accId)?.let { acc ->
                dao.upsertAccount(acc.copy(balanceToman = acc.balanceToman - expense.amountToman))
            }
        }
        id
    }

    suspend fun deleteExpense(id: String): Result<Unit> = runCatching {
        dao.deleteExpense(id)
    }

    // --- Voice Notes ---
    suspend fun saveVoiceNote(record: VoiceNoteRecord): Result<VoiceNoteRecord> = runCatching {
        require(record.rawTranscript.isNotBlank()) { "متن پیام صوتی خالی است." }
        val id = record.id.ifBlank { newId("voc") }
        val entity = VoiceNoteEntity(
            id = id,
            rawTranscript = record.rawTranscript.trim(),
            actionType = record.actionEnum.name,
            goldCategory = record.categoryEnum.name,
            pieceType = record.pieceTypeEnum.name,
            weightGrams = record.weightGrams.coerceAtLeast(0.0),
            karat = record.karat.coerceIn(0, 1000),
            usdAmount = record.usdAmount.coerceAtLeast(0.0),
            cashToman = record.cashToman.coerceAtLeast(0.0),
            counterpartyName = record.counterpartyName.trim().ifBlank { "ثبت نشده" },
            sellerName = record.sellerName?.trim()?.takeIf { it.isNotEmpty() },
            recordedAtMillis = record.recordedAtMillis,
            recordedDateTimeFa = record.recordedDateTimeFa,
            sheetsSynced = record.sheetsSynced,
            createdOffline = record.createdOffline
        )
        dao.upsertVoiceNote(entity)
        entity.toDomain()
    }

    suspend fun getUnsyncedVoiceNotes(): List<VoiceNoteRecord> =
        dao.getUnsyncedVoiceNotes().map { it.toDomain() }

    suspend fun markVoiceNotesSynced(ids: List<String>) {
        if (ids.isNotEmpty()) dao.markVoiceNotesSynced(ids)
    }

    suspend fun deleteVoiceNote(id: String): Result<Unit> = runCatching {
        dao.deleteVoiceNote(id)
    }

    // --- Transactions ---
    suspend fun recordTransaction(tx: GoldTransaction): Result<String> = runCatching {
        val id = tx.id.ifBlank { newId("tx") }
        val now = System.currentTimeMillis()
        val invoice = tx.invoiceNumber.ifBlank { "INV-${now % 1000000}" }
        dao.upsertTransaction(
            TransactionEntity(
                id = id,
                txType = tx.txTypeEnum.name,
                goldCategory = tx.categoryEnum.name,
                inventoryItemId = tx.inventoryItemId,
                customerId = tx.customerId,
                customerName = tx.customerName,
                investorId = tx.investorId,
                sellerId = tx.sellerId,
                sellerName = tx.sellerName,
                accountId = tx.accountId,
                targetAccountId = tx.targetAccountId,
                accountName = tx.accountName.ifBlank { "صندوق مغازه" },
                weightGrams = tx.weightGrams.coerceAtLeast(0.0),
                karat = tx.karat.coerceIn(0, 1000),
                unitPriceToman = tx.unitPriceToman.coerceAtLeast(0.0),
                wagePercent = tx.wagePercent.coerceIn(0.0, 100.0),
                profitMarginPercent = tx.profitMarginPercent.coerceIn(0.0, 100.0),
                taxPercent = tx.taxPercent.coerceIn(0.0, 100.0),
                costBasisToman = tx.costBasisToman.coerceAtLeast(0.0),
                totalAmountToman = tx.totalAmountToman.coerceAtLeast(0.0),
                calculatedProfitToman = tx.calculatedProfitToman,
                paymentMethod = tx.paymentMethodEnum.name,
                paymentStatus = tx.paymentStatusEnum.name,
                gatewayRefId = tx.gatewayRefId,
                invoiceNumber = invoice,
                sheetsSynced = false,
                notes = tx.notes,
                createdAtMillis = now
            )
        )

        // Update Account balances
        dao.getAccountById(tx.accountId)?.let { acc ->
            val updated = when (tx.txTypeEnum) {
                TxType.SALE -> {
                    val cashDelta = if (tx.paymentStatusEnum == PaymentStatus.PAID) tx.totalAmountToman else 0.0
                    val usdDelta = if (tx.categoryEnum == GoldCategory.USD_CURRENCY) -tx.weightGrams else 0.0
                    val goldDelta = if (tx.categoryEnum != GoldCategory.USD_CURRENCY && tx.categoryEnum != GoldCategory.NONE) -tx.weightGrams else 0.0
                    acc.copy(
                        balanceToman = acc.balanceToman + cashDelta,
                        balanceUsd = (acc.balanceUsd + usdDelta).coerceAtLeast(0.0),
                        goldGrams18k = (acc.goldGrams18k + goldDelta).coerceAtLeast(0.0)
                    )
                }
                TxType.PURCHASE -> {
                    val cashDelta = if (tx.paymentStatusEnum == PaymentStatus.PAID) -tx.totalAmountToman else 0.0
                    val usdDelta = if (tx.categoryEnum == GoldCategory.USD_CURRENCY) tx.weightGrams else 0.0
                    val goldDelta = if (tx.categoryEnum != GoldCategory.USD_CURRENCY && tx.categoryEnum != GoldCategory.NONE) tx.weightGrams else 0.0
                    acc.copy(
                        balanceToman = acc.balanceToman + cashDelta,
                        balanceUsd = acc.balanceUsd + usdDelta,
                        goldGrams18k = acc.goldGrams18k + goldDelta
                    )
                }
                TxType.TRANSFER_FROM_SHOP -> acc.copy(balanceToman = acc.balanceToman - tx.totalAmountToman)
                TxType.TRANSFER_TO_SHOP -> acc.copy(balanceToman = acc.balanceToman + tx.totalAmountToman)
                TxType.INVESTOR_DEPOSIT -> acc.copy(
                    balanceToman = acc.balanceToman + tx.totalAmountToman,
                    goldGrams18k = acc.goldGrams18k + tx.weightGrams
                )
                TxType.INVESTOR_PAYOUT -> acc.copy(
                    balanceToman = acc.balanceToman - tx.totalAmountToman,
                    goldGrams18k = (acc.goldGrams18k - tx.weightGrams).coerceAtLeast(0.0)
                )
            }
            dao.upsertAccount(updated)
            if (tx.txTypeEnum == TxType.TRANSFER_FROM_SHOP && !tx.targetAccountId.isNullOrBlank()) {
                dao.getAccountById(tx.targetAccountId)?.let { target ->
                    dao.upsertAccount(target.copy(balanceToman = target.balanceToman + tx.totalAmountToman))
                }
            }
        }

        // Update linked Inventory
        tx.inventoryItemId?.let { iid ->
            dao.getInventoryById(iid)?.let { inv ->
                when (tx.txTypeEnum) {
                    TxType.SALE -> {
                        val newWeight = (inv.weightGrams - tx.weightGrams).coerceAtLeast(0.0)
                        val newQty = if (inv.category == GoldCategory.USD_CURRENCY.name) {
                            (inv.quantity - tx.weightGrams.toLong()).coerceAtLeast(0L)
                        } else inv.quantity
                        dao.upsertInventory(inv.copy(weightGrams = newWeight, quantity = newQty))
                    }
                    TxType.PURCHASE, TxType.INVESTOR_DEPOSIT -> {
                        val added = tx.weightGrams.coerceAtLeast(0.0)
                        val newWeight = inv.weightGrams + added
                        val newCost = if (newWeight > 0.0 && tx.unitPriceToman > 0.0) {
                            ((inv.weightGrams * inv.avgBuyPricePerUnit) + (added * tx.unitPriceToman)) / newWeight
                        } else inv.avgBuyPricePerUnit
                        dao.upsertInventory(inv.copy(weightGrams = newWeight, avgBuyPricePerUnit = newCost))
                    }
                    else -> Unit
                }
            }
        }

        // Update linked Customer
        tx.customerId?.let { cid ->
            dao.getCustomerById(cid)?.let { cust ->
                val newTotal = if (tx.txTypeEnum == TxType.SALE) cust.totalPurchasedToman + tx.totalAmountToman else cust.totalPurchasedToman
                val newCash = when {
                    tx.txTypeEnum == TxType.SALE && tx.paymentStatusEnum == PaymentStatus.CREDIT -> cust.cashBalanceToman + tx.totalAmountToman
                    tx.txTypeEnum == TxType.PURCHASE && tx.paymentStatusEnum == PaymentStatus.CREDIT -> cust.cashBalanceToman - tx.totalAmountToman
                    else -> cust.cashBalanceToman
                }
                val newGold = if (tx.paymentMethodEnum == PaymentMethod.GOLD_EXCHANGE) {
                    cust.goldBalanceGrams + (if (tx.txTypeEnum == TxType.SALE) tx.weightGrams else -tx.weightGrams)
                } else cust.goldBalanceGrams
                dao.upsertCustomer(cust.copy(totalPurchasedToman = newTotal, cashBalanceToman = newCash, goldBalanceGrams = newGold))
            }
        }

        // Update linked Investor
        tx.investorId?.let { invId ->
            dao.getInvestorById(invId)?.let { inv ->
                when (tx.txTypeEnum) {
                    TxType.INVESTOR_DEPOSIT -> dao.upsertInvestor(
                        inv.copy(
                            investedGrams = inv.investedGrams + tx.weightGrams,
                            investedCashToman = inv.investedCashToman + tx.totalAmountToman
                        )
                    )
                    TxType.INVESTOR_PAYOUT -> dao.upsertInvestor(
                        inv.copy(realizedProfitToman = inv.realizedProfitToman + tx.totalAmountToman)
                    )
                    else -> Unit
                }
            }
        }

        // Update linked Shop Seller gold/USD entry/exit automatically
        tx.sellerId?.let { sId ->
            dao.getSellerById(sId)?.let { seller ->
                val isUsd = tx.categoryEnum == GoldCategory.USD_CURRENCY
                val isGold = !isUsd && tx.categoryEnum != GoldCategory.NONE
                val updated = when (tx.txTypeEnum) {
                    TxType.SALE -> seller.copy(
                        goldOutGrams = seller.goldOutGrams + if (isGold) tx.weightGrams else 0.0,
                        usdOut = seller.usdOut + if (isUsd) tx.weightGrams else 0.0,
                        cashInToman = seller.cashInToman + tx.totalAmountToman
                    )
                    TxType.PURCHASE -> seller.copy(
                        goldInGrams = seller.goldInGrams + if (isGold) tx.weightGrams else 0.0,
                        usdIn = seller.usdIn + if (isUsd) tx.weightGrams else 0.0,
                        cashOutToman = seller.cashOutToman + tx.totalAmountToman
                    )
                    else -> seller
                }
                dao.upsertSeller(updated)
            }
        }

        id
    }

    suspend fun confirmGatewayPayment(tx: GoldTransaction, refId: String): Result<Unit> = runCatching {
        dao.upsertTransaction(
            TransactionEntity(
                id = tx.id,
                txType = tx.txTypeEnum.name,
                goldCategory = tx.categoryEnum.name,
                inventoryItemId = tx.inventoryItemId,
                customerId = tx.customerId,
                customerName = tx.customerName,
                investorId = tx.investorId,
                sellerId = tx.sellerId,
                sellerName = tx.sellerName,
                accountId = tx.accountId,
                targetAccountId = tx.targetAccountId,
                accountName = tx.accountName,
                weightGrams = tx.weightGrams,
                karat = tx.karat,
                unitPriceToman = tx.unitPriceToman,
                wagePercent = tx.wagePercent,
                profitMarginPercent = tx.profitMarginPercent,
                taxPercent = tx.taxPercent,
                costBasisToman = tx.costBasisToman,
                totalAmountToman = tx.totalAmountToman,
                calculatedProfitToman = tx.calculatedProfitToman,
                paymentMethod = tx.paymentMethodEnum.name,
                paymentStatus = PaymentStatus.PAID.name,
                gatewayRefId = refId,
                invoiceNumber = tx.invoiceNumber,
                sheetsSynced = tx.sheetsSynced,
                notes = tx.notes,
                createdAtMillis = tx.effectiveTimeMillis
            )
        )
        if (tx.paymentStatusEnum == PaymentStatus.PENDING_GATEWAY) {
            dao.getAccountById(tx.accountId)?.let { acc ->
                dao.upsertAccount(acc.copy(balanceToman = acc.balanceToman + tx.totalAmountToman))
            }
        }
    }

    suspend fun markAllSynced(
        txIds: List<String>,
        expenseIds: List<String>,
        movementIds: List<String>,
        voiceIds: List<String>
    ) {
        if (txIds.isNotEmpty()) dao.markTransactionsSynced(txIds)
        if (expenseIds.isNotEmpty()) dao.markExpensesSynced(expenseIds)
        if (movementIds.isNotEmpty()) dao.markSellerMovementsSynced(movementIds)
        if (voiceIds.isNotEmpty()) dao.markVoiceNotesSynced(voiceIds)
    }

    // --- Price Alerts ---
    suspend fun addPriceAlert(alert: PriceAlert): Result<String> = runCatching {
        val id = alert.id.ifBlank { newId("alt") }
        dao.upsertPriceAlert(
            PriceAlertEntity(
                id = id,
                assetSymbol = alert.assetEnum.name,
                assetLabel = alert.assetLabel.ifBlank { alert.assetEnum.labelFa },
                condition = alert.conditionEnum.name,
                targetPrice = alert.targetPrice.coerceAtLeast(1.0),
                enabled = alert.enabled,
                triggered = alert.triggered,
                createdAtMillis = System.currentTimeMillis()
            )
        )
        id
    }

    suspend fun updatePriceAlertStatus(id: String, enabled: Boolean, triggered: Boolean): Result<Unit> = runCatching {
        dao.updatePriceAlertStatus(id, enabled, triggered)
    }

    suspend fun deletePriceAlert(id: String): Result<Unit> = runCatching {
        dao.deletePriceAlert(id)
    }

    // --- Entity Mappers ---
    private fun ts(millis: Long) = Timestamp(Date(millis))

    private fun AccountEntity.toDomain() = ShopAccount(
        id = id,
        userId = "local",
        name = name,
        accountType = accountType,
        balanceToman = balanceToman,
        balanceUsd = balanceUsd,
        goldGrams18k = goldGrams18k,
        accountNumber = accountNumber,
        createdAt = ts(createdAtMillis),
        updatedAt = ts(createdAtMillis)
    )

    private fun InventoryEntity.toDomain() = InventoryItem(
        id = id,
        userId = "local",
        category = category,
        pieceType = pieceType,
        title = title,
        karat = karat,
        weightGrams = weightGrams,
        quantity = quantity,
        avgBuyPricePerUnit = avgBuyPricePerUnit,
        defaultWagePercent = defaultWagePercent,
        labCode = labCode,
        createdAt = ts(createdAtMillis),
        updatedAt = ts(createdAtMillis)
    )

    private fun CustomerEntity.toDomain() = Customer(
        id = id,
        userId = "local",
        fullName = fullName,
        phone = phone,
        nationalId = nationalId,
        goldBalanceGrams = goldBalanceGrams,
        cashBalanceToman = cashBalanceToman,
        totalPurchasedToman = totalPurchasedToman,
        notes = notes,
        createdAt = ts(createdAtMillis),
        updatedAt = ts(createdAtMillis)
    )

    private fun InvestorEntity.toDomain() = Investor(
        id = id,
        userId = "local",
        fullName = fullName,
        phone = phone,
        goldCategory = goldCategory,
        investedGrams = investedGrams,
        investedCashToman = investedCashToman,
        profitSharePercent = profitSharePercent,
        realizedProfitToman = realizedProfitToman,
        contractStatus = contractStatus,
        notes = notes,
        createdAt = ts(createdAtMillis),
        updatedAt = ts(createdAtMillis)
    )

    private fun TransactionEntity.toDomain() = GoldTransaction(
        id = id,
        userId = "local",
        txType = txType,
        goldCategory = goldCategory,
        inventoryItemId = inventoryItemId,
        customerId = customerId,
        customerName = customerName,
        investorId = investorId,
        sellerId = sellerId,
        sellerName = sellerName,
        accountId = accountId,
        targetAccountId = targetAccountId,
        accountName = accountName,
        weightGrams = weightGrams,
        karat = karat,
        unitPriceToman = unitPriceToman,
        wagePercent = wagePercent,
        profitMarginPercent = profitMarginPercent,
        taxPercent = taxPercent,
        costBasisToman = costBasisToman,
        totalAmountToman = totalAmountToman,
        calculatedProfitToman = calculatedProfitToman,
        paymentMethod = paymentMethod,
        paymentStatus = paymentStatus,
        gatewayRefId = gatewayRefId,
        invoiceNumber = invoiceNumber,
        sheetsSynced = sheetsSynced,
        notes = notes,
        timestampMillis = createdAtMillis,
        createdAt = ts(createdAtMillis),
        updatedAt = ts(createdAtMillis)
    )

    private fun SellerEntity.toDomain() = ShopSeller(
        id = id,
        userId = "local",
        name = name,
        roleTitle = roleTitle,
        phone = phone,
        goldInGrams = goldInGrams,
        goldOutGrams = goldOutGrams,
        usdIn = usdIn,
        usdOut = usdOut,
        cashInToman = cashInToman,
        cashOutToman = cashOutToman,
        active = active,
        notes = notes,
        createdAt = ts(createdAtMillis),
        updatedAt = ts(createdAtMillis)
    )

    private fun SellerMovementEntity.toDomain() = SellerMovement(
        id = id,
        userId = "local",
        sellerId = sellerId,
        sellerName = sellerName,
        direction = direction,
        goldCategory = goldCategory,
        weightGrams = weightGrams,
        karat = karat,
        usdAmount = usdAmount,
        cashToman = cashToman,
        description = description,
        timestampMillis = timestampMillis,
        sheetsSynced = sheetsSynced,
        createdAt = ts(timestampMillis),
        updatedAt = ts(timestampMillis)
    )

    private fun ExpenseEntity.toDomain() = ShopExpense(
        id = id,
        userId = "local",
        title = title,
        category = category,
        amountToman = amountToman,
        goldEquivalentGrams = goldEquivalentGrams,
        accountId = accountId,
        accountName = accountName,
        sellerName = sellerName,
        notes = notes,
        timestampMillis = timestampMillis,
        sheetsSynced = sheetsSynced,
        createdAt = ts(timestampMillis),
        updatedAt = ts(timestampMillis)
    )

    private fun VoiceNoteEntity.toDomain() = VoiceNoteRecord(
        id = id,
        userId = "local",
        rawTranscript = rawTranscript,
        actionType = actionType,
        goldCategory = goldCategory,
        pieceType = pieceType,
        weightGrams = weightGrams,
        karat = karat,
        usdAmount = usdAmount,
        cashToman = cashToman,
        counterpartyName = counterpartyName,
        sellerName = sellerName,
        recordedAtMillis = recordedAtMillis,
        recordedDateTimeFa = recordedDateTimeFa,
        sheetsSynced = sheetsSynced,
        createdOffline = createdOffline,
        createdAt = ts(recordedAtMillis),
        updatedAt = ts(recordedAtMillis)
    )

    private fun PriceAlertEntity.toDomain() = PriceAlert(
        id = id,
        userId = "local",
        assetSymbol = assetSymbol,
        assetLabel = assetLabel,
        condition = condition,
        targetPrice = targetPrice,
        enabled = enabled,
        triggered = triggered,
        createdAt = ts(createdAtMillis),
        updatedAt = ts(createdAtMillis)
    )
}
