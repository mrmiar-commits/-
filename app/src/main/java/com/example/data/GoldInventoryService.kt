package com.example.data

import android.content.Context
import com.example.R
import com.example.model.InventoryItem
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

/**
 * Dedicated Firestore service for managing Gold & Currency Inventory items
 * (weight in grams, karat/purity, piece type, cost basis, and stock edits).
 */
class GoldInventoryService(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
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

    private fun inventoryCollection(uid: String) =
        db.collection("users").document(uid).collection("inventory")

    fun observeInventory(userId: String): Flow<List<InventoryItem>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/inventory"
        emitAll(
            inventoryCollection(uid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        InventoryItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error -> handleStreamError(error, OperationType.LIST, path, uid) }
        )
    }

    suspend fun getInventoryItem(userId: String, itemId: String): Result<InventoryItem?> {
        val path = "users/$userId/inventory/$itemId"
        return try {
            val uid = requireUserId(userId)
            require(itemId.isNotBlank()) { "شناسه کالا معتبر نیست." }
            val snap = inventoryCollection(uid).document(itemId).get().await()
            val item = snap.toObject(
                InventoryItem::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            Result.success(item)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun registerInventoryItem(userId: String, item: InventoryItem): Result<String> {
        val path = "users/$userId/inventory"
        return try {
            val uid = requireUserId(userId)
            validateInventoryInput(item)
            val docRef = inventoryCollection(uid).document()
            docRef.set(item.toCreateMap(uid)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateInventoryItem(userId: String, item: InventoryItem): Result<Unit> {
        val path = "users/$userId/inventory/${item.id}"
        return try {
            val uid = requireUserId(userId)
            require(item.id.isNotBlank()) { "شناسه کالا برای ویرایش الزامی است." }
            validateInventoryInput(item)
            inventoryCollection(uid).document(item.id)
                .update(item.toUpdateMap())
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun adjustInventoryStock(
        userId: String,
        itemId: String,
        newWeightGrams: Double,
        newQuantity: Long,
        newAvgBuyPricePerUnit: Double? = null
    ): Result<Unit> {
        val path = "users/$userId/inventory/$itemId"
        return try {
            val uid = requireUserId(userId)
            require(itemId.isNotBlank()) { "شناسه کالا معتبر نیست." }
            require(newWeightGrams >= 0.0) { "وزن نمی‌تواند منفی باشد." }
            require(newQuantity >= 0L) { "تعداد نمی‌تواند منفی باشد." }
            val updates = mutableMapOf<String, Any>(
                "weightGrams" to newWeightGrams,
                "quantity" to newQuantity,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            newAvgBuyPricePerUnit?.let {
                require(it >= 0.0) { "میانگین قیمت خرید نمی‌تواند منفی باشد." }
                updates["avgBuyPricePerUnit"] = it
            }
            inventoryCollection(uid).document(itemId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteInventoryItem(userId: String, itemId: String): Result<Unit> {
        val path = "users/$userId/inventory/$itemId"
        return try {
            val uid = requireUserId(userId)
            require(itemId.isNotBlank()) { "شناسه کالا معتبر نیست." }
            inventoryCollection(uid).document(itemId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private fun validateInventoryInput(item: InventoryItem) {
        require(item.title.isNotBlank()) { "عنوان کالا الزامی است." }
        require(item.karat in 0..1000) { "عیار باید بین ۰ تا ۱۰۰۰ باشد." }
        require(item.weightGrams >= 0.0) { "وزن نمی‌تواند منفی باشد." }
        require(item.quantity >= 0L) { "تعداد نمی‌تواند منفی باشد." }
        require(item.avgBuyPricePerUnit >= 0.0) { "قیمت خرید نمی‌تواند منفی باشد." }
        require(item.defaultWagePercent in 0.0..100.0) { "درصد اجرت باید بین ۰ تا ۱۰۰ باشد." }
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
}
