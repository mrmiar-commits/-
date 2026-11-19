package com.example.data

import android.content.Context
import com.example.R
import com.example.model.*
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ZarnegarRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private val inventoryService = GoldInventoryService(db, auth)

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUserId: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("کاربر باید ابتدا وارد حساب گوگل شود.")
        if (expectedUserId != null && uid != expectedUserId) {
            throw IllegalStateException("تغییر حساب کاربری شناسایی شد.")
        }
        return uid
    }

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    fun observeUserSettings(userId: String): Flow<UserSettings?> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid"
        emitAll(
            userDoc(uid).snapshots()
                .map { it.toObject(UserSettings::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error -> handleStreamError(error, OperationType.GET, path, uid) }
        )
    }

    fun observeAccounts(userId: String): Flow<List<ShopAccount>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/accounts"
        emitAll(
            userDoc(uid).collection("accounts")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { it.toObjects(ShopAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error -> handleStreamError(error, OperationType.LIST, path, uid) }
        )
    }

    fun observeInventory(userId: String): Flow<List<InventoryItem>> =
        inventoryService.observeInventory(userId)

    fun observeCustomers(userId: String): Flow<List<Customer>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/customers"
        emitAll(
            userDoc(uid).collection("customers")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { it.toObjects(Customer::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error -> handleStreamError(error, OperationType.LIST, path, uid) }
        )
    }

    fun observeInvestors(userId: String): Flow<List<Investor>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/investors"
        emitAll(
            userDoc(uid).collection("investors")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { it.toObjects(Investor::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error -> handleStreamError(error, OperationType.LIST, path, uid) }
        )
    }

    fun observeTransactions(userId: String): Flow<List<GoldTransaction>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/transactions"
        emitAll(
            userDoc(uid).collection("transactions")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { it.toObjects(GoldTransaction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error -> handleStreamError(error, OperationType.LIST, path, uid) }
        )
    }

    fun observePriceAlerts(userId: String): Flow<List<PriceAlert>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/price_alerts"
        emitAll(
            userDoc(uid).collection("price_alerts")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { it.toObjects(PriceAlert::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error -> handleStreamError(error, OperationType.LIST, path, uid) }
        )
    }

    private fun handleStreamError(error: Throwable, op: OperationType, path: String, uid: String): Nothing {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
        throw error
    }

    suspend fun ensureDefaultSetup(userId: String, displayName: String?): Result<Unit> {
        return try {
            val uid = requireUserId(userId)
            val profileSnap = userDoc(uid).get().await()
            if (!profileSnap.exists()) {
                val settings = UserSettings(
                    userId = uid,
                    shopName = "گالری و طلافروشی زرنگار",
                    ownerName = displayName?.takeIf { it.isNotBlank() } ?: "مدیر مغازه",
                    paymentProvider = PaymentProvider.ZARINPAL.name
                )
                userDoc(uid).set(settings.toCreateMap(uid)).await()
            }
            val accountsSnap = userDoc(uid).collection("accounts").limit(1).get().await()
            if (accountsSnap.isEmpty) {
                val batch = db.batch()
                val cashAcc = ShopAccount(
                    userId = uid,
                    name = "صندوق نقدی مغازه",
                    accountType = AccountType.CASH_REGISTER.name,
                    balanceToman = 0.0,
                    balanceUsd = 0.0,
                    goldGrams18k = 0.0
                )
                val bankAcc = ShopAccount(
                    userId = uid,
                    name = "حساب بانکی و کارتخوان مغازه",
                    accountType = AccountType.BANK_POS.name,
                    balanceToman = 0.0,
                    balanceUsd = 0.0,
                    goldGrams18k = 0.0
                )
                val safeAcc = ShopAccount(
                    userId = uid,
                    name = "گاوصندوق طلای مغازه",
                    accountType = AccountType.GOLD_SAFE.name,
                    balanceToman = 0.0,
                    balanceUsd = 0.0,
                    goldGrams18k = 0.0
                )
                batch.set(userDoc(uid).collection("accounts").document("acc_cash_main"), cashAcc.toCreateMap(uid))
                batch.set(userDoc(uid).collection("accounts").document("acc_bank_pos"), bankAcc.toCreateMap(uid))
                batch.set(userDoc(uid).collection("accounts").document("acc_gold_safe"), safeAcc.toCreateMap(uid))
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$userId")
            Result.failure(e)
        }
    }

    suspend fun updateUserSettings(
        userId: String,
        shopName: String? = null,
        ownerName: String? = null,
        totpEnabled: Boolean? = null,
        biometricEnabled: Boolean? = null,
        spreadsheetId: String? = null,
        paymentProvider: PaymentProvider? = null,
        merchantId: String? = null
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
            shopName?.trim()?.takeIf { it.isNotEmpty() }?.let { updates["shopName"] = it.take(100) }
            ownerName?.trim()?.takeIf { it.isNotEmpty() }?.let { updates["ownerName"] = it.take(100) }
            totpEnabled?.let { updates["totpEnabled"] = it }
            biometricEnabled?.let { updates["biometricEnabled"] = it }
            spreadsheetId?.trim()?.takeIf { it.isNotEmpty() }?.let { updates["spreadsheetId"] = it.take(128) }
            paymentProvider?.let { updates["paymentProvider"] = it.name }
            merchantId?.trim()?.let { if (it.isNotEmpty()) updates["merchantId"] = it.take(128) }
            userDoc(uid).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun addAccount(userId: String, account: ShopAccount): Result<String> {
        val path = "users/$userId/accounts"
        return try {
            val uid = requireUserId(userId)
            require(account.name.isNotBlank()) { "نام حساب الزامی است" }
            val docRef = userDoc(uid).collection("accounts").document()
            docRef.set(account.toCreateMap(uid)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteAccount(userId: String, accountId: String): Result<Unit> {
        val path = "users/$userId/accounts/$accountId"
        return try {
            val uid = requireUserId(userId)
            userDoc(uid).collection("accounts").document(accountId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun addInventoryItem(userId: String, item: InventoryItem): Result<String> =
        inventoryService.registerInventoryItem(userId, item)

    suspend fun updateInventoryItem(userId: String, item: InventoryItem): Result<Unit> =
        inventoryService.updateInventoryItem(userId, item)

    suspend fun deleteInventoryItem(userId: String, itemId: String): Result<Unit> =
        inventoryService.deleteInventoryItem(userId, itemId)

    suspend fun addCustomer(userId: String, customer: Customer): Result<String> {
        val path = "users/$userId/customers"
        return try {
            val uid = requireUserId(userId)
            require(customer.fullName.isNotBlank()) { "نام مشتری الزامی است" }
            val docRef = userDoc(uid).collection("customers").document()
            docRef.set(customer.toCreateMap(uid)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateCustomer(userId: String, customer: Customer): Result<Unit> {
        val path = "users/$userId/customers/${customer.id}"
        return try {
            val uid = requireUserId(userId)
            require(customer.id.isNotBlank())
            val updates = mutableMapOf<String, Any>(
                "fullName" to customer.fullName.trim().take(100),
                "phone" to customer.phone.trim().ifBlank { "09000000000" }.take(30),
                "goldBalanceGrams" to customer.goldBalanceGrams,
                "cashBalanceToman" to customer.cashBalanceToman,
                "totalPurchasedToman" to customer.totalPurchasedToman.coerceAtLeast(0.0),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            customer.nationalId?.takeIf { it.isNotBlank() }?.let { updates["nationalId"] = it.take(24) }
            customer.notes?.takeIf { it.isNotBlank() }?.let { updates["notes"] = it.take(500) }
            userDoc(uid).collection("customers").document(customer.id).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteCustomer(userId: String, customerId: String): Result<Unit> {
        val path = "users/$userId/customers/$customerId"
        return try {
            val uid = requireUserId(userId)
            userDoc(uid).collection("customers").document(customerId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun addInvestor(userId: String, investor: Investor): Result<String> {
        val path = "users/$userId/investors"
        return try {
            val uid = requireUserId(userId)
            require(investor.fullName.isNotBlank()) { "نام سرمایه‌گذار الزامی است" }
            val docRef = userDoc(uid).collection("investors").document()
            docRef.set(investor.toCreateMap(uid)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateInvestor(userId: String, investor: Investor): Result<Unit> {
        val path = "users/$userId/investors/${investor.id}"
        return try {
            val uid = requireUserId(userId)
            require(investor.id.isNotBlank())
            val updates = mutableMapOf<String, Any>(
                "fullName" to investor.fullName.trim().take(100),
                "phone" to investor.phone.trim().ifBlank { "09000000000" }.take(30),
                "goldCategory" to investor.categoryEnum.name,
                "investedGrams" to investor.investedGrams.coerceAtLeast(0.0),
                "investedCashToman" to investor.investedCashToman.coerceAtLeast(0.0),
                "profitSharePercent" to investor.profitSharePercent.coerceIn(0.0, 100.0),
                "realizedProfitToman" to investor.realizedProfitToman,
                "contractStatus" to investor.statusEnum.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            investor.notes?.takeIf { it.isNotBlank() }?.let { updates["notes"] = it.take(500) }
            userDoc(uid).collection("investors").document(investor.id).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteInvestor(userId: String, investorId: String): Result<Unit> {
        val path = "users/$userId/investors/$investorId"
        return try {
            val uid = requireUserId(userId)
            userDoc(uid).collection("investors").document(investorId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun recordTransaction(
        userId: String,
        tx: GoldTransaction,
        accounts: List<ShopAccount>,
        inventory: List<InventoryItem>,
        customers: List<Customer>,
        investors: List<Investor>
    ): Result<String> {
        val path = "users/$userId/transactions"
        return try {
            val uid = requireUserId(userId)
            require(tx.accountId.isNotBlank()) { "انتخاب حساب مغازه الزامی است" }

            val batch = db.batch()
            val txRef = userDoc(uid).collection("transactions").document()
            batch.set(txRef, tx.toCreateMap(uid))

            val sourceAccount = accounts.firstOrNull { it.id == tx.accountId }
            val targetAccount = tx.targetAccountId?.let { tid -> accounts.firstOrNull { it.id == tid } }

            // Update source & target shop accounts atomically
            if (sourceAccount != null) {
                val accRef = userDoc(uid).collection("accounts").document(sourceAccount.id)
                when (tx.txTypeEnum) {
                    TxType.SALE -> {
                        if (tx.paymentStatusEnum == PaymentStatus.PAID) {
                            if (tx.categoryEnum == GoldCategory.USD_CURRENCY && tx.paymentMethodEnum == PaymentMethod.CASH) {
                                batch.update(accRef, mapOf(
                                    "balanceToman" to (sourceAccount.balanceToman + tx.totalAmountToman),
                                    "updatedAt" to FieldValue.serverTimestamp()
                                ))
                            } else {
                                batch.update(accRef, mapOf(
                                    "balanceToman" to (sourceAccount.balanceToman + tx.totalAmountToman),
                                    "goldGrams18k" to (sourceAccount.goldGrams18k - tx.weightGrams).coerceAtLeast(0.0),
                                    "updatedAt" to FieldValue.serverTimestamp()
                                ))
                            }
                        }
                    }
                    TxType.PURCHASE -> {
                        batch.update(accRef, mapOf(
                            "balanceToman" to (sourceAccount.balanceToman - tx.totalAmountToman),
                            "goldGrams18k" to (sourceAccount.goldGrams18k + tx.weightGrams),
                            "updatedAt" to FieldValue.serverTimestamp()
                        ))
                    }
                    TxType.TRANSFER_FROM_SHOP -> {
                        val isUsd = tx.categoryEnum == GoldCategory.USD_CURRENCY
                        val updatedToman = if (!isUsd && tx.weightGrams <= 0.0) sourceAccount.balanceToman - tx.totalAmountToman else sourceAccount.balanceToman
                        val updatedUsd = if (isUsd) sourceAccount.balanceUsd - tx.totalAmountToman else sourceAccount.balanceUsd
                        val updatedGold = if (!isUsd && tx.weightGrams > 0.0) (sourceAccount.goldGrams18k - tx.weightGrams).coerceAtLeast(0.0) else sourceAccount.goldGrams18k
                        batch.update(accRef, mapOf(
                            "balanceToman" to updatedToman,
                            "balanceUsd" to updatedUsd,
                            "goldGrams18k" to updatedGold,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ))
                        if (targetAccount != null && targetAccount.id != sourceAccount.id) {
                            val targetRef = userDoc(uid).collection("accounts").document(targetAccount.id)
                            batch.update(targetRef, mapOf(
                                "balanceToman" to if (!isUsd && tx.weightGrams <= 0.0) targetAccount.balanceToman + tx.totalAmountToman else targetAccount.balanceToman,
                                "balanceUsd" to if (isUsd) targetAccount.balanceUsd + tx.totalAmountToman else targetAccount.balanceUsd,
                                "goldGrams18k" to if (!isUsd && tx.weightGrams > 0.0) targetAccount.goldGrams18k + tx.weightGrams else targetAccount.goldGrams18k,
                                "updatedAt" to FieldValue.serverTimestamp()
                            ))
                        }
                    }
                    TxType.TRANSFER_TO_SHOP -> {
                        val isUsd = tx.categoryEnum == GoldCategory.USD_CURRENCY
                        batch.update(accRef, mapOf(
                            "balanceToman" to if (!isUsd && tx.weightGrams <= 0.0) sourceAccount.balanceToman + tx.totalAmountToman else sourceAccount.balanceToman,
                            "balanceUsd" to if (isUsd) sourceAccount.balanceUsd + tx.totalAmountToman else sourceAccount.balanceUsd,
                            "goldGrams18k" to if (!isUsd && tx.weightGrams > 0.0) sourceAccount.goldGrams18k + tx.weightGrams else sourceAccount.goldGrams18k,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ))
                    }
                    TxType.INVESTOR_DEPOSIT -> {
                        batch.update(accRef, mapOf(
                            "balanceToman" to (sourceAccount.balanceToman + tx.totalAmountToman),
                            "goldGrams18k" to (sourceAccount.goldGrams18k + tx.weightGrams),
                            "updatedAt" to FieldValue.serverTimestamp()
                        ))
                    }
                    TxType.INVESTOR_PAYOUT -> {
                        batch.update(accRef, mapOf(
                            "balanceToman" to (sourceAccount.balanceToman - tx.totalAmountToman),
                            "goldGrams18k" to (sourceAccount.goldGrams18k - tx.weightGrams).coerceAtLeast(0.0),
                            "updatedAt" to FieldValue.serverTimestamp()
                        ))
                    }
                }
            }

            // Update linked Inventory lot
            val invItem = tx.inventoryItemId?.let { iid -> inventory.firstOrNull { it.id == iid } }
            if (invItem != null) {
                val invRef = userDoc(uid).collection("inventory").document(invItem.id)
                when (tx.txTypeEnum) {
                    TxType.SALE -> {
                        val newWeight = (invItem.weightGrams - tx.weightGrams).coerceAtLeast(0.0)
                        val newQty = if (invItem.categoryEnum == GoldCategory.USD_CURRENCY) {
                            (invItem.quantity - tx.weightGrams.toLong()).coerceAtLeast(0L)
                        } else {
                            invItem.quantity
                        }
                        batch.update(invRef, mapOf(
                            "weightGrams" to newWeight,
                            "quantity" to newQty,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ))
                    }
                    TxType.PURCHASE, TxType.INVESTOR_DEPOSIT -> {
                        val addedWeight = tx.weightGrams.coerceAtLeast(0.0)
                        val newWeight = invItem.weightGrams + addedWeight
                        val newAvgCost = if (newWeight > 0.0 && tx.unitPriceToman > 0.0) {
                            ((invItem.weightGrams * invItem.avgBuyPricePerUnit) + (addedWeight * tx.unitPriceToman)) / newWeight
                        } else invItem.avgBuyPricePerUnit
                        batch.update(invRef, mapOf(
                            "weightGrams" to newWeight,
                            "avgBuyPricePerUnit" to newAvgCost,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ))
                    }
                    else -> Unit
                }
            }

            // Update linked Customer file balances
            val customer = tx.customerId?.let { cid -> customers.firstOrNull { it.id == cid } }
            if (customer != null) {
                val custRef = userDoc(uid).collection("customers").document(customer.id)
                val newTotalPurchased = if (tx.txTypeEnum == TxType.SALE) {
                    customer.totalPurchasedToman + tx.totalAmountToman
                } else customer.totalPurchasedToman
                val newCashBal = when {
                    tx.txTypeEnum == TxType.SALE && tx.paymentStatusEnum == PaymentStatus.CREDIT ->
                        customer.cashBalanceToman + tx.totalAmountToman
                    tx.txTypeEnum == TxType.PURCHASE && tx.paymentStatusEnum == PaymentStatus.CREDIT ->
                        customer.cashBalanceToman - tx.totalAmountToman
                    else -> customer.cashBalanceToman
                }
                val newGoldBal = if (tx.paymentMethodEnum == PaymentMethod.GOLD_EXCHANGE) {
                    customer.goldBalanceGrams + (if (tx.txTypeEnum == TxType.SALE) tx.weightGrams else -tx.weightGrams)
                } else customer.goldBalanceGrams
                batch.update(custRef, mapOf(
                    "totalPurchasedToman" to newTotalPurchased,
                    "cashBalanceToman" to newCashBal,
                    "goldBalanceGrams" to newGoldBal,
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
            }

            // Update linked Investor balances
            val investor = tx.investorId?.let { id -> investors.firstOrNull { it.id == id } }
            if (investor != null) {
                val invRef = userDoc(uid).collection("investors").document(investor.id)
                when (tx.txTypeEnum) {
                    TxType.INVESTOR_DEPOSIT -> {
                        batch.update(invRef, mapOf(
                            "investedGrams" to (investor.investedGrams + tx.weightGrams),
                            "investedCashToman" to (investor.investedCashToman + tx.totalAmountToman),
                            "updatedAt" to FieldValue.serverTimestamp()
                        ))
                    }
                    TxType.INVESTOR_PAYOUT -> {
                        batch.update(invRef, mapOf(
                            "realizedProfitToman" to (investor.realizedProfitToman + tx.totalAmountToman),
                            "updatedAt" to FieldValue.serverTimestamp()
                        ))
                    }
                    else -> Unit
                }
            }

            batch.commit().await()
            Result.success(txRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun confirmGatewayPayment(
        userId: String,
        tx: GoldTransaction,
        gatewayRefId: String,
        account: ShopAccount?
    ): Result<Unit> {
        val path = "users/$userId/transactions/${tx.id}"
        return try {
            val uid = requireUserId(userId)
            val batch = db.batch()
            val txRef = userDoc(uid).collection("transactions").document(tx.id)
            batch.update(txRef, mapOf(
                "paymentStatus" to PaymentStatus.PAID.name,
                "gatewayRefId" to gatewayRefId.take(128),
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            if (account != null && tx.paymentStatusEnum == PaymentStatus.PENDING_GATEWAY) {
                val accRef = userDoc(uid).collection("accounts").document(account.id)
                batch.update(accRef, mapOf(
                    "balanceToman" to (account.balanceToman + tx.totalAmountToman),
                    "goldGrams18k" to (account.goldGrams18k - tx.weightGrams).coerceAtLeast(0.0),
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun markTransactionsSynced(userId: String, txIds: List<String>): Result<Unit> {
        if (txIds.isEmpty()) return Result.success(Unit)
        return try {
            val uid = requireUserId(userId)
            txIds.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { txId ->
                    val ref = userDoc(uid).collection("transactions").document(txId)
                    batch.update(ref, mapOf(
                        "sheetsSynced" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ))
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$userId/transactions")
            Result.failure(e)
        }
    }

    suspend fun deleteTransaction(userId: String, txId: String): Result<Unit> {
        val path = "users/$userId/transactions/$txId"
        return try {
            val uid = requireUserId(userId)
            userDoc(uid).collection("transactions").document(txId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun addPriceAlert(userId: String, alert: PriceAlert): Result<String> {
        val path = "users/$userId/price_alerts"
        return try {
            val uid = requireUserId(userId)
            require(alert.targetPrice > 0.0) { "قیمت هدف باید بزرگتر از صفر باشد" }
            val ref = userDoc(uid).collection("price_alerts").document()
            ref.set(alert.toCreateMap(uid)).await()
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePriceAlertStatus(
        userId: String,
        alertId: String,
        enabled: Boolean? = null,
        triggered: Boolean? = null
    ): Result<Unit> {
        val path = "users/$userId/price_alerts/$alertId"
        return try {
            val uid = requireUserId(userId)
            val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
            enabled?.let { updates["enabled"] = it }
            triggered?.let { updates["triggered"] = it }
            userDoc(uid).collection("price_alerts").document(alertId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deletePriceAlert(userId: String, alertId: String): Result<Unit> {
        val path = "users/$userId/price_alerts/$alertId"
        return try {
            val uid = requireUserId(userId)
            userDoc(uid).collection("price_alerts").document(alertId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
