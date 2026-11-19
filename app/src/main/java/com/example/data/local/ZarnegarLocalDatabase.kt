package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "shop_accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val accountType: String,
    val balanceToman: Double,
    val balanceUsd: Double,
    val goldGrams18k: Double,
    val accountNumber: String?,
    val createdAtMillis: Long
)

@Entity(tableName = "inventory_items")
data class InventoryEntity(
    @PrimaryKey val id: String,
    val category: String,
    val pieceType: String,
    val title: String,
    val karat: Int,
    val weightGrams: Double,
    val quantity: Long,
    val avgBuyPricePerUnit: Double,
    val defaultWagePercent: Double,
    val labCode: String?,
    val createdAtMillis: Long
)

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey val id: String,
    val fullName: String,
    val phone: String,
    val nationalId: String?,
    val goldBalanceGrams: Double,
    val cashBalanceToman: Double,
    val totalPurchasedToman: Double,
    val notes: String?,
    val createdAtMillis: Long
)

@Entity(tableName = "investors")
data class InvestorEntity(
    @PrimaryKey val id: String,
    val fullName: String,
    val phone: String,
    val goldCategory: String,
    val investedGrams: Double,
    val investedCashToman: Double,
    val profitSharePercent: Double,
    val realizedProfitToman: Double,
    val contractStatus: String,
    val notes: String?,
    val createdAtMillis: Long
)

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val txType: String,
    val goldCategory: String,
    val inventoryItemId: String?,
    val customerId: String?,
    val customerName: String?,
    val investorId: String?,
    val sellerId: String?,
    val sellerName: String?,
    val accountId: String,
    val targetAccountId: String?,
    val accountName: String,
    val weightGrams: Double,
    val karat: Int,
    val unitPriceToman: Double,
    val wagePercent: Double,
    val profitMarginPercent: Double,
    val taxPercent: Double,
    val costBasisToman: Double,
    val totalAmountToman: Double,
    val calculatedProfitToman: Double,
    val paymentMethod: String,
    val paymentStatus: String,
    val gatewayRefId: String?,
    val invoiceNumber: String,
    val sheetsSynced: Boolean,
    val notes: String?,
    val createdAtMillis: Long
)

@Entity(tableName = "shop_sellers")
data class SellerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val roleTitle: String,
    val phone: String,
    val goldInGrams: Double,
    val goldOutGrams: Double,
    val usdIn: Double,
    val usdOut: Double,
    val cashInToman: Double,
    val cashOutToman: Double,
    val active: Boolean,
    val notes: String?,
    val createdAtMillis: Long
)

@Entity(tableName = "seller_movements")
data class SellerMovementEntity(
    @PrimaryKey val id: String,
    val sellerId: String,
    val sellerName: String,
    val direction: String,
    val goldCategory: String,
    val weightGrams: Double,
    val karat: Int,
    val usdAmount: Double,
    val cashToman: Double,
    val description: String,
    val timestampMillis: Long,
    val sheetsSynced: Boolean
)

@Entity(tableName = "shop_expenses")
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val title: String,
    val category: String,
    val amountToman: Double,
    val goldEquivalentGrams: Double,
    val accountId: String?,
    val accountName: String,
    val sellerName: String?,
    val notes: String?,
    val timestampMillis: Long,
    val sheetsSynced: Boolean
)

@Entity(tableName = "voice_notes")
data class VoiceNoteEntity(
    @PrimaryKey val id: String,
    val rawTranscript: String,
    val actionType: String,
    val goldCategory: String,
    val pieceType: String,
    val weightGrams: Double,
    val karat: Int,
    val usdAmount: Double,
    val cashToman: Double,
    val counterpartyName: String,
    val sellerName: String?,
    val recordedAtMillis: Long,
    val recordedDateTimeFa: String,
    val sheetsSynced: Boolean,
    val createdOffline: Boolean
)

@Entity(tableName = "price_alerts")
data class PriceAlertEntity(
    @PrimaryKey val id: String,
    val assetSymbol: String,
    val assetLabel: String,
    val condition: String,
    val targetPrice: Double,
    val enabled: Boolean,
    val triggered: Boolean,
    val createdAtMillis: Long
)

@Dao
interface ZarnegarDao {
    // Accounts
    @Query("SELECT * FROM shop_accounts ORDER BY createdAtMillis ASC")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT COUNT(*) FROM shop_accounts")
    suspend fun countAccounts(): Int

    @Query("SELECT * FROM shop_accounts WHERE id = :id LIMIT 1")
    suspend fun getAccountById(id: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccount(entity: AccountEntity)

    @Query("DELETE FROM shop_accounts WHERE id = :id")
    suspend fun deleteAccount(id: String)

    // Inventory
    @Query("SELECT * FROM inventory_items ORDER BY createdAtMillis DESC")
    fun observeInventory(): Flow<List<InventoryEntity>>

    @Query("SELECT * FROM inventory_items WHERE id = :id LIMIT 1")
    suspend fun getInventoryById(id: String): InventoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInventory(entity: InventoryEntity)

    @Query("DELETE FROM inventory_items WHERE id = :id")
    suspend fun deleteInventory(id: String)

    // Customers
    @Query("SELECT * FROM customers ORDER BY createdAtMillis DESC")
    fun observeCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun getCustomerById(id: String): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCustomer(entity: CustomerEntity)

    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun deleteCustomer(id: String)

    // Investors
    @Query("SELECT * FROM investors ORDER BY createdAtMillis DESC")
    fun observeInvestors(): Flow<List<InvestorEntity>>

    @Query("SELECT * FROM investors WHERE id = :id LIMIT 1")
    suspend fun getInvestorById(id: String): InvestorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInvestor(entity: InvestorEntity)

    @Query("DELETE FROM investors WHERE id = :id")
    suspend fun deleteInvestor(id: String)

    // Transactions
    @Query("SELECT * FROM transactions ORDER BY createdAtMillis DESC")
    fun observeTransactions(): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransaction(entity: TransactionEntity)

    @Query("UPDATE transactions SET sheetsSynced = 1 WHERE id IN (:ids)")
    suspend fun markTransactionsSynced(ids: List<String>)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransaction(id: String)

    // Sellers & Movements
    @Query("SELECT * FROM shop_sellers ORDER BY createdAtMillis ASC")
    fun observeSellers(): Flow<List<SellerEntity>>

    @Query("SELECT COUNT(*) FROM shop_sellers")
    suspend fun countSellers(): Int

    @Query("SELECT * FROM shop_sellers WHERE id = :id LIMIT 1")
    suspend fun getSellerById(id: String): SellerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSeller(entity: SellerEntity)

    @Query("DELETE FROM shop_sellers WHERE id = :id")
    suspend fun deleteSeller(id: String)

    @Query("SELECT * FROM seller_movements ORDER BY timestampMillis DESC")
    fun observeSellerMovements(): Flow<List<SellerMovementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSellerMovement(entity: SellerMovementEntity)

    @Query("UPDATE seller_movements SET sheetsSynced = 1 WHERE id IN (:ids)")
    suspend fun markSellerMovementsSynced(ids: List<String>)

    @Query("DELETE FROM seller_movements WHERE id = :id")
    suspend fun deleteSellerMovement(id: String)

    // Expenses
    @Query("SELECT * FROM shop_expenses ORDER BY timestampMillis DESC")
    fun observeExpenses(): Flow<List<ExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExpense(entity: ExpenseEntity)

    @Query("UPDATE shop_expenses SET sheetsSynced = 1 WHERE id IN (:ids)")
    suspend fun markExpensesSynced(ids: List<String>)

    @Query("DELETE FROM shop_expenses WHERE id = :id")
    suspend fun deleteExpense(id: String)

    // Voice Notes
    @Query("SELECT * FROM voice_notes ORDER BY recordedAtMillis DESC")
    fun observeVoiceNotes(): Flow<List<VoiceNoteEntity>>

    @Query("SELECT * FROM voice_notes WHERE sheetsSynced = 0 ORDER BY recordedAtMillis ASC")
    suspend fun getUnsyncedVoiceNotes(): List<VoiceNoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVoiceNote(entity: VoiceNoteEntity)

    @Query("UPDATE voice_notes SET sheetsSynced = 1 WHERE id IN (:ids)")
    suspend fun markVoiceNotesSynced(ids: List<String>)

    @Query("DELETE FROM voice_notes WHERE id = :id")
    suspend fun deleteVoiceNote(id: String)

    // Price Alerts
    @Query("SELECT * FROM price_alerts ORDER BY createdAtMillis DESC")
    fun observePriceAlerts(): Flow<List<PriceAlertEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPriceAlert(entity: PriceAlertEntity)

    @Query("UPDATE price_alerts SET enabled = :enabled, triggered = :triggered WHERE id = :id")
    suspend fun updatePriceAlertStatus(id: String, enabled: Boolean, triggered: Boolean)

    @Query("DELETE FROM price_alerts WHERE id = :id")
    suspend fun deletePriceAlert(id: String)
}

@Database(
    entities = [
        AccountEntity::class,
        InventoryEntity::class,
        CustomerEntity::class,
        InvestorEntity::class,
        TransactionEntity::class,
        SellerEntity::class,
        SellerMovementEntity::class,
        ExpenseEntity::class,
        VoiceNoteEntity::class,
        PriceAlertEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ZarnegarLocalDatabase : RoomDatabase() {
    abstract fun dao(): ZarnegarDao

    companion object {
        @Volatile
        private var INSTANCE: ZarnegarLocalDatabase? = null

        fun getInstance(context: Context): ZarnegarLocalDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ZarnegarLocalDatabase::class.java,
                    "zarnegar_offline_db"
                ).fallbackToDestructiveMigration(dropAllTables = true).build().also { INSTANCE = it }
            }
        }
    }
}
