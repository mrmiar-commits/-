package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

inline fun <reified T : Enum<T>> safeEnum(raw: String?, default: T): T {
    if (raw.isNullOrBlank()) return default
    return enumValues<T>().firstOrNull { it.name == raw } ?: default
}

enum class GoldCategory(val labelFa: String, val defaultKarat: Int) {
    NEW_GOLD("طلای نو ویترینی", 750),
    USED_NO_WAGE("طلای کارکرده بدون اجرت", 750),
    MELTED_GOLD("طلای آبشده", 750),
    BULLION("شمش طلا (۲۴ عیار)", 999),
    USD_CURRENCY("دلار و ارز", 0),
    OTHER_GOLD("سایر انواع طلا", 750),
    NONE("عملیات حساب / نقدی", 0);

    companion object {
        val inventoryCategories = listOf(NEW_GOLD, USED_NO_WAGE, MELTED_GOLD, BULLION, USD_CURRENCY, OTHER_GOLD)
        val goldOnlyCategories = listOf(MELTED_GOLD, BULLION, NEW_GOLD, USED_NO_WAGE, OTHER_GOLD)
        val investorCategories = listOf(MELTED_GOLD, USED_NO_WAGE, NEW_GOLD, BULLION)
    }
}

enum class TxType(val labelFa: String) {
    SALE("فروش روزانه"),
    PURCHASE("خرید از مشتری / همکار"),
    TRANSFER_FROM_SHOP("انتقال از حساب مغازه"),
    TRANSFER_TO_SHOP("انتقال به حساب مغازه"),
    INVESTOR_DEPOSIT("ورود سرمایه سرمایه‌گذار"),
    INVESTOR_PAYOUT("تسویه سرمایه‌گذار")
}

enum class AccountType(val labelFa: String) {
    CASH_REGISTER("صندوق نقدی مغازه"),
    BANK_POS("حساب بانکی / کارتخوان"),
    PERSONAL_CARD("کارت بانکی شخصی"),
    PETTY_CASH("تنخواه مغازه"),
    GOLD_SAFE("گاوصندوق طلا"),
    CUSTOM("سایر حساب / کارت")
}

enum class PaymentMethod(val labelFa: String) {
    CASH("نقدی"),
    POS("کارتخوان (POS)"),
    GATEWAY("درگاه پرداخت آنلاین"),
    GOLD_EXCHANGE("تهاتر / معاوضه طلا"),
    ACCOUNT_TRANSFER("کارت به کارت / پایا")
}

enum class PaymentStatus(val labelFa: String) {
    PAID("تسویه شده"),
    PENDING_GATEWAY("در انتظار تایید درگاه"),
    CREDIT("نسیه / دفتری")
}

enum class InvestorContractStatus(val labelFa: String) {
    ACTIVE("فعال"),
    SETTLED("تسویه شده")
}

enum class AlertAssetSymbol(val labelFa: String, val unitFa: String) {
    GOLD_18K("گرم طلای ۱۸ عیار", "تومان"),
    MELTED_MESGHAL("مثقال طلای آبشده", "تومان"),
    BULLION_24K("گرم شمش ۲۴ عیار", "تومان"),
    XAU_OUNCE("انس جهانی طلا (XAU)", "دلار"),
    USD_RATE("نرخ دلار آزاد", "تومان")
}

enum class AlertCondition(val labelFa: String) {
    ABOVE("بیشتر یا مساوی"),
    BELOW("کمتر یا مساوی")
}

enum class PaymentProvider(val labelFa: String) {
    ZARINPAL("زرین‌پال"),
    IDPAY("آیدی‌پی"),
    CUSTOM_LINK("لینک اختصاصی")
}

enum class MovementDirection(val labelFa: String) {
    IN("ورود به مغازه"),
    OUT("خروج از مغازه")
}

enum class ExpenseCategory(val labelFa: String) {
    RENT("اجاره مغازه"),
    UTILITIES("قبوض برق، آب و تلفن"),
    SALARY("حقوق و پورسانت"),
    SUPPLIES("ملزومات و پذیرایی"),
    SECURITY_REPAIR("تعمیرات و امنیت"),
    TRANSPORT("حمل و نقل و پیک"),
    TAX_UNION("مالیات و اتحادیه"),
    OTHER("سایر مخارج")
}

enum class VoiceActionType(val labelFa: String) {
    SALE_OUT("تحویل / فروش به"),
    PURCHASE_IN("دریافت / خرید از"),
    EXPENSE("ثبت هزینه مغازه"),
    NOTE("یادداشت صوتی")
}

enum class ReportPeriod(val labelFa: String, val days: Int?) {
    TODAY("امروز", 1),
    WEEK("۷ روز اخیر", 7),
    MONTH("۳۰ روز اخیر", 30),
    ALL("کل دوره", null)
}

enum class GoldPieceType(val labelFa: String) {
    RING("انگشتر"),
    NECKLACE("گردنبند"),
    BANGLE("النگو"),
    BRACELET("دستبند"),
    EARRING("گوشواره"),
    SET("سرویس و نیم‌ست"),
    PENDANT("پلاک و آویز"),
    COIN("سکه"),
    BULLION_BAR("شمش"),
    MELTED_BLOCK("قطعه آبشده"),
    CURRENCY_UNIT("واحد ارز"),
    OTHER("سایر قطعات");

    companion object {
        fun defaultFor(category: GoldCategory): GoldPieceType = when (category) {
            GoldCategory.MELTED_GOLD -> MELTED_BLOCK
            GoldCategory.BULLION -> BULLION_BAR
            GoldCategory.USD_CURRENCY -> CURRENCY_UNIT
            else -> RING
        }
    }
}

@Keep
@IgnoreExtraProperties
data class UserSettings(
    @DocumentId val id: String = "",
    val userId: String = "",
    val shopName: String = "طلافروشی زرنگار",
    val ownerName: String = "مدیر مغازه",
    val totpEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val spreadsheetId: String? = null,
    val sheetsWebhookUrl: String? = null,
    val paymentProvider: String = PaymentProvider.ZARINPAL.name,
    val merchantId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> {
        val raw = mapOf(
            "userId" to uid,
            "shopName" to shopName.ifBlank { "طلافروشی زرنگار" }.take(100),
            "ownerName" to ownerName.ifBlank { "مدیر مغازه" }.take(100),
            "totpEnabled" to false,
            "biometricEnabled" to false,
            "spreadsheetId" to spreadsheetId?.takeIf { it.isNotBlank() }?.take(128),
            "sheetsWebhookUrl" to sheetsWebhookUrl?.takeIf { it.isNotBlank() }?.take(512),
            "paymentProvider" to safeEnum(paymentProvider, PaymentProvider.ZARINPAL).name,
            "merchantId" to merchantId?.takeIf { it.isNotBlank() }?.take(128),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }
}

@Keep
@IgnoreExtraProperties
data class ShopAccount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val accountType: String = AccountType.CASH_REGISTER.name,
    val balanceToman: Double = 0.0,
    val balanceUsd: Double = 0.0,
    val goldGrams18k: Double = 0.0,
    val accountNumber: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val typeEnum: AccountType get() = safeEnum(accountType, AccountType.CUSTOM)

    fun goldEquivalent18kGrams(gold18kGramPriceToman: Double, usdTomanPrice: Double): Double {
        if (gold18kGramPriceToman <= 0.0) return goldGrams18k
        val cashEquiv = balanceToman / gold18kGramPriceToman
        val usdEquiv = if (usdTomanPrice > 0.0) (balanceUsd * usdTomanPrice) / gold18kGramPriceToman else 0.0
        return goldGrams18k + cashEquiv + usdEquiv
    }

    fun toCreateMap(uid: String): Map<String, Any> {
        val raw = mapOf(
            "userId" to uid,
            "name" to name.trim().take(80),
            "accountType" to typeEnum.name,
            "balanceToman" to balanceToman,
            "balanceUsd" to balanceUsd,
            "goldGrams18k" to goldGrams18k,
            "accountNumber" to accountNumber?.takeIf { it.isNotBlank() }?.take(64),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }
}

@Keep
@IgnoreExtraProperties
data class InventoryItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val category: String = GoldCategory.NEW_GOLD.name,
    val pieceType: String = GoldPieceType.RING.name,
    val title: String = "",
    val karat: Int = 750,
    val weightGrams: Double = 0.0,
    val quantity: Long = 0L,
    val avgBuyPricePerUnit: Double = 0.0,
    val defaultWagePercent: Double = 0.0,
    val labCode: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val categoryEnum: GoldCategory get() = safeEnum(category, GoldCategory.NEW_GOLD)
    val pieceTypeEnum: GoldPieceType get() = safeEnum(pieceType, GoldPieceType.OTHER)

    fun toCreateMap(uid: String): Map<String, Any> {
        val raw = mapOf(
            "userId" to uid,
            "category" to categoryEnum.name,
            "pieceType" to pieceTypeEnum.name,
            "title" to title.trim().take(120),
            "karat" to karat.coerceIn(0, 1000),
            "weightGrams" to weightGrams.coerceAtLeast(0.0),
            "quantity" to quantity.coerceAtLeast(0L),
            "avgBuyPricePerUnit" to avgBuyPricePerUnit.coerceAtLeast(0.0),
            "defaultWagePercent" to defaultWagePercent.coerceIn(0.0, 100.0),
            "labCode" to labCode?.takeIf { it.isNotBlank() }?.take(64),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }

    fun toUpdateMap(): Map<String, Any> {
        val updates = mutableMapOf<String, Any>(
            "category" to categoryEnum.name,
            "pieceType" to pieceTypeEnum.name,
            "title" to title.trim().take(120),
            "karat" to karat.coerceIn(0, 1000),
            "weightGrams" to weightGrams.coerceAtLeast(0.0),
            "quantity" to quantity.coerceAtLeast(0L),
            "avgBuyPricePerUnit" to avgBuyPricePerUnit.coerceAtLeast(0.0),
            "defaultWagePercent" to defaultWagePercent.coerceIn(0.0, 100.0),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        val cleanLab = labCode?.trim()?.takeIf { it.isNotEmpty() }?.take(64)
        updates["labCode"] = cleanLab ?: FieldValue.delete()
        return updates
    }
}

@Keep
@IgnoreExtraProperties
data class ShopSeller(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val roleTitle: String = "فروشنده مغازه",
    val phone: String = "",
    val goldInGrams: Double = 0.0,
    val goldOutGrams: Double = 0.0,
    val usdIn: Double = 0.0,
    val usdOut: Double = 0.0,
    val cashInToman: Double = 0.0,
    val cashOutToman: Double = 0.0,
    val active: Boolean = true,
    val notes: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val netGoldGrams: Double get() = goldInGrams - goldOutGrams
    val netUsd: Double get() = usdIn - usdOut

    fun toCreateMap(uid: String): Map<String, Any> {
        val raw = mapOf(
            "userId" to uid,
            "name" to name.trim().take(100),
            "roleTitle" to roleTitle.trim().ifBlank { "فروشنده مغازه" }.take(60),
            "phone" to phone.trim().ifBlank { "-" }.take(30),
            "goldInGrams" to goldInGrams.coerceAtLeast(0.0),
            "goldOutGrams" to goldOutGrams.coerceAtLeast(0.0),
            "usdIn" to usdIn.coerceAtLeast(0.0),
            "usdOut" to usdOut.coerceAtLeast(0.0),
            "cashInToman" to cashInToman.coerceAtLeast(0.0),
            "cashOutToman" to cashOutToman.coerceAtLeast(0.0),
            "active" to active,
            "notes" to notes?.takeIf { it.isNotBlank() }?.take(300),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }
}

@Keep
@IgnoreExtraProperties
data class SellerMovement(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sellerId: String = "",
    val sellerName: String = "",
    val direction: String = MovementDirection.IN.name,
    val goldCategory: String = GoldCategory.NEW_GOLD.name,
    val weightGrams: Double = 0.0,
    val karat: Int = 750,
    val usdAmount: Double = 0.0,
    val cashToman: Double = 0.0,
    val description: String = "",
    val timestampMillis: Long = System.currentTimeMillis(),
    val sheetsSynced: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val directionEnum: MovementDirection get() = safeEnum(direction, MovementDirection.IN)
    val categoryEnum: GoldCategory get() = safeEnum(goldCategory, GoldCategory.NEW_GOLD)

    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "sellerId" to sellerId.take(128),
        "sellerName" to sellerName.trim().take(100),
        "direction" to directionEnum.name,
        "goldCategory" to categoryEnum.name,
        "weightGrams" to weightGrams.coerceAtLeast(0.0),
        "karat" to karat.coerceIn(0, 1000),
        "usdAmount" to usdAmount.coerceAtLeast(0.0),
        "cashToman" to cashToman.coerceAtLeast(0.0),
        "description" to description.trim().take(300),
        "timestampMillis" to timestampMillis,
        "sheetsSynced" to sheetsSynced,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class ShopExpense(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val category: String = ExpenseCategory.OTHER.name,
    val amountToman: Double = 0.0,
    val goldEquivalentGrams: Double = 0.0,
    val accountId: String? = null,
    val accountName: String = "صندوق مغازه",
    val sellerName: String? = null,
    val notes: String? = null,
    val timestampMillis: Long = System.currentTimeMillis(),
    val sheetsSynced: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val categoryEnum: ExpenseCategory get() = safeEnum(category, ExpenseCategory.OTHER)

    fun toCreateMap(uid: String): Map<String, Any> {
        val raw = mapOf(
            "userId" to uid,
            "title" to title.trim().take(120),
            "category" to categoryEnum.name,
            "amountToman" to amountToman.coerceAtLeast(0.0),
            "goldEquivalentGrams" to goldEquivalentGrams.coerceAtLeast(0.0),
            "accountId" to accountId?.takeIf { it.isNotBlank() }?.take(128),
            "accountName" to accountName.trim().ifBlank { "صندوق مغازه" }.take(100),
            "sellerName" to sellerName?.takeIf { it.isNotBlank() }?.take(100),
            "notes" to notes?.takeIf { it.isNotBlank() }?.take(400),
            "timestampMillis" to timestampMillis,
            "sheetsSynced" to sheetsSynced,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }
}

@Keep
@IgnoreExtraProperties
data class VoiceNoteRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val rawTranscript: String = "",
    val actionType: String = VoiceActionType.SALE_OUT.name,
    val goldCategory: String = GoldCategory.MELTED_GOLD.name,
    val pieceType: String = GoldPieceType.MELTED_BLOCK.name,
    val weightGrams: Double = 0.0,
    val karat: Int = 750,
    val usdAmount: Double = 0.0,
    val cashToman: Double = 0.0,
    val counterpartyName: String = "",
    val sellerName: String? = null,
    val recordedAtMillis: Long = System.currentTimeMillis(),
    val recordedDateTimeFa: String = "",
    val sheetsSynced: Boolean = false,
    val createdOffline: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val actionEnum: VoiceActionType get() = safeEnum(actionType, VoiceActionType.NOTE)
    val categoryEnum: GoldCategory get() = safeEnum(goldCategory, GoldCategory.OTHER_GOLD)
    val pieceTypeEnum: GoldPieceType get() = safeEnum(pieceType, GoldPieceType.OTHER)

    fun toCreateMap(uid: String): Map<String, Any> {
        val raw = mapOf(
            "userId" to uid,
            "rawTranscript" to rawTranscript.trim().take(600),
            "actionType" to actionEnum.name,
            "goldCategory" to categoryEnum.name,
            "pieceType" to pieceTypeEnum.name,
            "weightGrams" to weightGrams.coerceAtLeast(0.0),
            "karat" to karat.coerceIn(0, 1000),
            "usdAmount" to usdAmount.coerceAtLeast(0.0),
            "cashToman" to cashToman.coerceAtLeast(0.0),
            "counterpartyName" to counterpartyName.trim().take(100),
            "sellerName" to sellerName?.takeIf { it.isNotBlank() }?.take(100),
            "recordedAtMillis" to recordedAtMillis,
            "recordedDateTimeFa" to recordedDateTimeFa.take(64),
            "sheetsSynced" to sheetsSynced,
            "createdOffline" to createdOffline,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }
}

@Keep
@IgnoreExtraProperties
data class Customer(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fullName: String = "",
    val phone: String = "",
    val nationalId: String? = null,
    val goldBalanceGrams: Double = 0.0,
    val cashBalanceToman: Double = 0.0,
    val totalPurchasedToman: Double = 0.0,
    val notes: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> {
        val raw = mapOf(
            "userId" to uid,
            "fullName" to fullName.trim().take(100),
            "phone" to phone.trim().ifBlank { "09000000000" }.take(30),
            "nationalId" to nationalId?.takeIf { it.isNotBlank() }?.take(24),
            "goldBalanceGrams" to goldBalanceGrams,
            "cashBalanceToman" to cashBalanceToman,
            "totalPurchasedToman" to totalPurchasedToman.coerceAtLeast(0.0),
            "notes" to notes?.takeIf { it.isNotBlank() }?.take(500),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }
}

@Keep
@IgnoreExtraProperties
data class Investor(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fullName: String = "",
    val phone: String = "",
    val goldCategory: String = GoldCategory.MELTED_GOLD.name,
    val investedGrams: Double = 0.0,
    val investedCashToman: Double = 0.0,
    val profitSharePercent: Double = 50.0,
    val realizedProfitToman: Double = 0.0,
    val contractStatus: String = InvestorContractStatus.ACTIVE.name,
    val notes: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val categoryEnum: GoldCategory get() = safeEnum(goldCategory, GoldCategory.MELTED_GOLD)
    val statusEnum: InvestorContractStatus get() = safeEnum(contractStatus, InvestorContractStatus.ACTIVE)

    fun toCreateMap(uid: String): Map<String, Any> {
        val raw = mapOf(
            "userId" to uid,
            "fullName" to fullName.trim().take(100),
            "phone" to phone.trim().ifBlank { "09000000000" }.take(30),
            "goldCategory" to categoryEnum.name,
            "investedGrams" to investedGrams.coerceAtLeast(0.0),
            "investedCashToman" to investedCashToman.coerceAtLeast(0.0),
            "profitSharePercent" to profitSharePercent.coerceIn(0.0, 100.0),
            "realizedProfitToman" to realizedProfitToman,
            "contractStatus" to statusEnum.name,
            "notes" to notes?.takeIf { it.isNotBlank() }?.take(500),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }
}

@Keep
@IgnoreExtraProperties
data class GoldTransaction(
    @DocumentId val id: String = "",
    val userId: String = "",
    val txType: String = TxType.SALE.name,
    val goldCategory: String = GoldCategory.NEW_GOLD.name,
    val inventoryItemId: String? = null,
    val customerId: String? = null,
    val customerName: String? = null,
    val investorId: String? = null,
    val sellerId: String? = null,
    val sellerName: String? = null,
    val accountId: String = "",
    val targetAccountId: String? = null,
    val accountName: String = "",
    val weightGrams: Double = 0.0,
    val karat: Int = 750,
    val unitPriceToman: Double = 0.0,
    val wagePercent: Double = 0.0,
    val profitMarginPercent: Double = 0.0,
    val taxPercent: Double = 0.0,
    val costBasisToman: Double = 0.0,
    val totalAmountToman: Double = 0.0,
    val calculatedProfitToman: Double = 0.0,
    val paymentMethod: String = PaymentMethod.CASH.name,
    val paymentStatus: String = PaymentStatus.PAID.name,
    val gatewayRefId: String? = null,
    val invoiceNumber: String = "",
    val sheetsSynced: Boolean = false,
    val notes: String? = null,
    val timestampMillis: Long = System.currentTimeMillis(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val txTypeEnum: TxType get() = safeEnum(txType, TxType.SALE)
    val categoryEnum: GoldCategory get() = safeEnum(goldCategory, GoldCategory.NEW_GOLD)
    val paymentMethodEnum: PaymentMethod get() = safeEnum(paymentMethod, PaymentMethod.CASH)
    val paymentStatusEnum: PaymentStatus get() = safeEnum(paymentStatus, PaymentStatus.PAID)

    val effectiveTimeMillis: Long
        get() = createdAt?.toDate()?.time ?: timestampMillis

    fun toCreateMap(uid: String): Map<String, Any> {
        val raw = mapOf(
            "userId" to uid,
            "txType" to txTypeEnum.name,
            "goldCategory" to categoryEnum.name,
            "inventoryItemId" to inventoryItemId?.takeIf { it.isNotBlank() }?.take(128),
            "customerId" to customerId?.takeIf { it.isNotBlank() }?.take(128),
            "customerName" to customerName?.takeIf { it.isNotBlank() }?.take(100),
            "investorId" to investorId?.takeIf { it.isNotBlank() }?.take(128),
            "sellerId" to sellerId?.takeIf { it.isNotBlank() }?.take(128),
            "sellerName" to sellerName?.takeIf { it.isNotBlank() }?.take(100),
            "accountId" to accountId.take(128),
            "targetAccountId" to targetAccountId?.takeIf { it.isNotBlank() }?.take(128),
            "accountName" to accountName.trim().ifBlank { "حساب مغازه" }.take(100),
            "weightGrams" to weightGrams.coerceAtLeast(0.0),
            "karat" to karat.coerceIn(0, 1000),
            "unitPriceToman" to unitPriceToman.coerceAtLeast(0.0),
            "wagePercent" to wagePercent.coerceIn(0.0, 100.0),
            "profitMarginPercent" to profitMarginPercent.coerceIn(0.0, 100.0),
            "taxPercent" to taxPercent.coerceIn(0.0, 100.0),
            "costBasisToman" to costBasisToman.coerceAtLeast(0.0),
            "totalAmountToman" to totalAmountToman.coerceAtLeast(0.0),
            "calculatedProfitToman" to calculatedProfitToman,
            "paymentMethod" to paymentMethodEnum.name,
            "paymentStatus" to paymentStatusEnum.name,
            "gatewayRefId" to gatewayRefId?.takeIf { it.isNotBlank() }?.take(128),
            "invoiceNumber" to invoiceNumber.ifBlank { "INV-${System.currentTimeMillis()}" }.take(64),
            "sheetsSynced" to sheetsSynced,
            "notes" to notes?.takeIf { it.isNotBlank() }?.take(500),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }
}

@Keep
@IgnoreExtraProperties
data class PriceAlert(
    @DocumentId val id: String = "",
    val userId: String = "",
    val assetSymbol: String = AlertAssetSymbol.GOLD_18K.name,
    val assetLabel: String = AlertAssetSymbol.GOLD_18K.labelFa,
    val condition: String = AlertCondition.ABOVE.name,
    val targetPrice: Double = 0.0,
    val enabled: Boolean = true,
    val triggered: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val assetEnum: AlertAssetSymbol get() = safeEnum(assetSymbol, AlertAssetSymbol.GOLD_18K)
    val conditionEnum: AlertCondition get() = safeEnum(condition, AlertCondition.ABOVE)

    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "assetSymbol" to assetEnum.name,
        "assetLabel" to assetLabel.ifBlank { assetEnum.labelFa }.take(80),
        "condition" to conditionEnum.name,
        "targetPrice" to targetPrice.coerceAtLeast(1.0),
        "enabled" to enabled,
        "triggered" to triggered,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

data class ManualPricingConfig(
    val useManualPrice: Boolean = false,
    val manualGold18kToman: Double = 0.0,
    val manualMeltedMesghalToman: Double = 0.0,
    val manualBullion24kToman: Double = 0.0,
    val manualUsdToman: Double = 0.0
)

data class MarketSnapshot(
    val xauOunceUsd: Double = 0.0,
    val usdToToman: Double = 0.0,
    val eurToToman: Double = 0.0,
    val gold24kGramToman: Double = 0.0,
    val gold18kGramToman: Double = 0.0,
    val meltedMesghalToman: Double = 0.0,
    val lastUpdatedText: String = "",
    val isLiveNetwork: Boolean = false,
    val isManualOverride: Boolean = false,
    val sourceNote: String = ""
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
