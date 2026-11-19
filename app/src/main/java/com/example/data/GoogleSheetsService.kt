package com.example.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.model.*
import com.example.utils.Formatters
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class CreateSpreadsheetRequest(
    val properties: SpreadsheetProperties,
    val sheets: List<SheetDescriptor>
)

@JsonClass(generateAdapter = true)
data class SpreadsheetProperties(val title: String)

@JsonClass(generateAdapter = true)
data class SheetDescriptor(val properties: SheetTitleProperties)

@JsonClass(generateAdapter = true)
data class SheetTitleProperties(val title: String)

@JsonClass(generateAdapter = true)
data class CreateSpreadsheetResponse(
    val spreadsheetId: String,
    val spreadsheetUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class ValueRangeBody(
    val range: String,
    val majorDimension: String = "ROWS",
    val values: List<List<Any>>
)

@JsonClass(generateAdapter = true)
data class ClearValuesResponse(val clearedRange: String? = null)

@JsonClass(generateAdapter = true)
data class UpdateValuesResponse(
    val updatedRows: Int? = null,
    val updatedCells: Int? = null
)

interface GoogleSheetsRestApi {
    @POST("v4/spreadsheets")
    suspend fun createSpreadsheet(
        @Header("Authorization") bearerToken: String,
        @Body body: CreateSpreadsheetRequest
    ): CreateSpreadsheetResponse

    @POST("v4/spreadsheets/{spreadsheetId}/values/{range}:clear")
    suspend fun clearRange(
        @Header("Authorization") bearerToken: String,
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("range", encoded = true) range: String
    ): ClearValuesResponse

    @PUT("v4/spreadsheets/{spreadsheetId}/values/{range}")
    suspend fun updateRange(
        @Header("Authorization") bearerToken: String,
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("range", encoded = true) range: String,
        @Query("valueInputOption") valueInputOption: String = "USER_ENTERED",
        @Body body: ValueRangeBody
    ): UpdateValuesResponse
}

sealed class SheetsAuthResult {
    data class Token(val accessToken: String) : SheetsAuthResult()
    data class NeedsResolution(val pendingIntent: PendingIntent) : SheetsAuthResult()
    data class Failed(val message: String) : SheetsAuthResult()
}

class GoogleSheetsSyncService {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val api = Retrofit.Builder()
        .baseUrl("https://sheets.googleapis.com/")
        .client(httpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(GoogleSheetsRestApi::class.java)

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        /**
         * Ready-to-paste Google Apps Script Web App bridge.
         * Solves Google OAuth "Error 403: access_blocked" permanently with zero verification barrier.
         */
        val APPS_SCRIPT_TEMPLATE = """
function doPost(e) {
  try {
    var payload = JSON.parse(e.postData.contents);
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    if (payload.mode === 'ping') {
      return ContentService.createTextOutput(JSON.stringify({ ok: true, sheet: ss.getName() }))
        .setMimeType(ContentService.MimeType.JSON);
    }
    if (payload.mode === 'append_voice' && payload.rows) {
      var sh = ss.getSheetByName('Voice_Messages') || ss.insertSheet('Voice_Messages');
      if (sh.getLastRow() === 0) {
        sh.appendRow(['شناسه','تاریخ و ساعت','متن پیام صوتی','عملیات','نوع طلا / ارز','نوع قطعه','وزن (گرم)','عیار','دلار','مبلغ نقدی','مشتری / همکار','فروشنده','ثبت آفلاین']);
      }
      payload.rows.forEach(function(r) { sh.appendRow(r); });
      return ContentService.createTextOutput(JSON.stringify({ ok: true, added: payload.rows.length }))
        .setMimeType(ContentService.MimeType.JSON);
    }
    if (payload.tabs) {
      Object.keys(payload.tabs).forEach(function(name) {
        var rows = payload.tabs[name];
        var sh = ss.getSheetByName(name) || ss.insertSheet(name);
        sh.clearContents();
        if (rows && rows.length > 0) {
          sh.getRange(1, 1, rows.length, rows[0].length).setValues(rows);
        }
      });
    }
    return ContentService.createTextOutput(JSON.stringify({ ok: true }))
      .setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({ ok: false, error: err.toString() }))
      .setMimeType(ContentService.MimeType.JSON);
  }
}
        """.trimIndent()

        fun isNetworkAvailable(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }

    /**
     * Tests connectivity to the user's Google Apps Script Web App URL (no OAuth Access Blocked error).
     */
    suspend fun testWebhookConnection(webhookUrl: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUrl = validateWebhookUrl(webhookUrl)
            val body = JSONObject().put("mode", "ping").toString().toRequestBody(JSON_MEDIA)
            val req = Request.Builder().url(cleanUrl).post(body).build()
            httpClient.newCall(req).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    throw IllegalStateException("کد خطای سرور گوگل: ${res.code}")
                }
                if (text.contains("<!DOCTYPE html", ignoreCase = true) || text.contains("accounts.google.com")) {
                    throw IllegalStateException("دسترسی وب‌اپ باید روی Anyone تنظیم شود.")
                }
                val json = runCatching { JSONObject(text) }.getOrNull()
                if (json?.optBoolean("ok") == true) {
                    json.optString("sheet", "گوگل شیت زرنگار")
                } else {
                    throw IllegalStateException(json?.optString("error") ?: "پاسخ نامعتبر از اسکریپت گوگل شیت")
                }
            }
        }
    }

    /**
     * Appends queued offline/online voice messages immediately to the Voice_Messages sheet.
     */
    suspend fun appendVoiceNotesViaWebhook(
        webhookUrl: String,
        notes: List<VoiceNoteRecord>
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (notes.isEmpty()) return@withContext Result.success(0)
        runCatching {
            val cleanUrl = validateWebhookUrl(webhookUrl)
            val rowsArray = JSONArray()
            notes.forEach { n ->
                rowsArray.put(
                    JSONArray().apply {
                        put(n.id)
                        put(n.recordedDateTimeFa.ifBlank { Formatters.formatMillis(n.recordedAtMillis) })
                        put(n.rawTranscript)
                        put(n.actionEnum.labelFa)
                        put(n.categoryEnum.labelFa)
                        put(n.pieceTypeEnum.labelFa)
                        put(n.weightGrams)
                        put(n.karat)
                        put(n.usdAmount)
                        put(n.cashToman)
                        put(n.counterpartyName)
                        put(n.sellerName ?: "-")
                        put(if (n.createdOffline) "آفلاین (ارسال خودکار)" else "آنلاین")
                    }
                )
            }
            val payload = JSONObject()
                .put("mode", "append_voice")
                .put("rows", rowsArray)
                .toString()
            val req = Request.Builder().url(cleanUrl).post(payload.toRequestBody(JSON_MEDIA)).build()
            httpClient.newCall(req).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) throw IllegalStateException("خطای ارسال (${res.code})")
                if (text.contains("<!DOCTYPE html", ignoreCase = true)) {
                    throw IllegalStateException("دسترسی Web App گوگل شیت روی Anyone تنظیم نشده است.")
                }
                val json = runCatching { JSONObject(text) }.getOrNull()
                if (json?.optBoolean("ok") != true) {
                    throw IllegalStateException(json?.optString("error") ?: "خطا در ثبت ردیف پیام صوتی")
                }
                notes.size
            }
        }
    }

    /**
     * Full online sync of all ledgers to Google Sheets via Apps Script Web App URL (zero Access Blocked).
     */
    suspend fun syncAllViaWebhook(
        webhookUrl: String,
        transactions: List<GoldTransaction>,
        accounts: List<ShopAccount>,
        customers: List<Customer>,
        investors: List<Investor>,
        inventory: List<InventoryItem>,
        sellers: List<ShopSeller>,
        sellerMovements: List<SellerMovement>,
        expenses: List<ShopExpense>,
        voiceNotes: List<VoiceNoteRecord>,
        gold18kPrice: Double,
        usdPrice: Double
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUrl = validateWebhookUrl(webhookUrl)
            val tabs = buildAllTabs(
                transactions, accounts, customers, investors, inventory,
                sellers, sellerMovements, expenses, voiceNotes, gold18kPrice, usdPrice
            )
            val tabsJson = JSONObject()
            tabs.forEach { (tabName, rows) ->
                val sheetArr = JSONArray()
                rows.forEach { row ->
                    val rowArr = JSONArray()
                    row.forEach { cell -> rowArr.put(cell) }
                    sheetArr.put(rowArr)
                }
                tabsJson.put(tabName, sheetArr)
            }
            val payload = JSONObject()
                .put("mode", "full_sync")
                .put("tabs", tabsJson)
                .toString()
            val req = Request.Builder().url(cleanUrl).post(payload.toRequestBody(JSON_MEDIA)).build()
            httpClient.newCall(req).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) throw IllegalStateException("خطای اتصال به گوگل شیت (${res.code})")
                if (text.contains("<!DOCTYPE html", ignoreCase = true)) {
                    throw IllegalStateException("لطفاً در Apps Script گزینه Who has access را روی Anyone بگذارید.")
                }
                val json = runCatching { JSONObject(text) }.getOrNull()
                if (json?.optBoolean("ok") != true) {
                    throw IllegalStateException(json?.optString("error") ?: "همگام‌سازی شیت با خطا مواجه شد")
                }
                transactions.size + expenses.size + voiceNotes.size
            }
        }
    }

    private fun validateWebhookUrl(url: String): String {
        val clean = url.trim()
        require(clean.startsWith("https://script.google.com/")) {
            "لینک وب‌اپ گوگل شیت باید با https://script.google.com/ شروع شود."
        }
        return clean
    }

    // --- Direct OAuth Google Sheets API (Secondary option) ---
    suspend fun authorizeSheets(context: Context): SheetsAuthResult {
        return try {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(
                    listOf(
                        Scope("https://www.googleapis.com/auth/spreadsheets"),
                        Scope("https://www.googleapis.com/auth/drive.file")
                    )
                )
                .build()
            val result = Identity.getAuthorizationClient(context).authorize(request).await()
            if (result.hasResolution() && result.pendingIntent != null) {
                SheetsAuthResult.NeedsResolution(result.pendingIntent!!)
            } else {
                val token = result.accessToken
                if (token.isNullOrBlank()) {
                    SheetsAuthResult.Failed("خطای محدودیت OAuth گوگل (Access Blocked). لطفاً از اتصال مستقیم لینک اسکریپت گوگل شیت استفاده کنید.")
                } else {
                    SheetsAuthResult.Token(token)
                }
            }
        } catch (e: Exception) {
            SheetsAuthResult.Failed("اتصال OAuth توسط گوگل مسدود شد (Access Blocked). از اتصال مستقیم وب‌اپ گوگل شیت در همین صفحه استفاده کنید.")
        }
    }

    fun extractTokenFromIntent(context: Context, data: Intent?): String? {
        if (data == null) return null
        return try {
            Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data).accessToken
        } catch (_: Exception) {
            null
        }
    }

    suspend fun ensureSpreadsheet(accessToken: String, existingId: String?, shopName: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                if (!existingId.isNullOrBlank()) return@withContext Result.success(existingId)
                val tabNames = listOf(
                    "Summary_Stats", "Voice_Messages", "Transactions", "Accounts",
                    "Sellers", "Seller_Movements", "Expenses", "Inventory", "Customers", "Investors"
                )
                val req = CreateSpreadsheetRequest(
                    properties = SpreadsheetProperties(title = "Zarnegar Gold Accounting - $shopName"),
                    sheets = tabNames.map { SheetDescriptor(SheetTitleProperties(it)) }
                )
                val res = api.createSpreadsheet("Bearer $accessToken", req)
                Result.success(res.spreadsheetId)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun syncAllLedgersOAuth(
        accessToken: String,
        spreadsheetId: String,
        transactions: List<GoldTransaction>,
        accounts: List<ShopAccount>,
        customers: List<Customer>,
        investors: List<Investor>,
        inventory: List<InventoryItem>,
        sellers: List<ShopSeller>,
        sellerMovements: List<SellerMovement>,
        expenses: List<ShopExpense>,
        voiceNotes: List<VoiceNoteRecord>,
        gold18kPrice: Double,
        usdPrice: Double
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val auth = "Bearer $accessToken"
            val tabs = buildAllTabs(
                transactions, accounts, customers, investors, inventory,
                sellers, sellerMovements, expenses, voiceNotes, gold18kPrice, usdPrice
            )
            tabs.forEach { (tabName, rows) ->
                pushTabOAuth(auth, spreadsheetId, tabName, rows)
            }
            Result.success(transactions.size + expenses.size + voiceNotes.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun pushTabOAuth(auth: String, spreadsheetId: String, tabName: String, rows: List<List<Any>>) {
        val range = "$tabName!A1:Z1000"
        runCatching { api.clearRange(auth, spreadsheetId, range) }
        api.updateRange(
            bearerToken = auth,
            spreadsheetId = spreadsheetId,
            range = "$tabName!A1",
            body = ValueRangeBody(range = "$tabName!A1", values = rows)
        )
    }

    private fun buildAllTabs(
        transactions: List<GoldTransaction>,
        accounts: List<ShopAccount>,
        customers: List<Customer>,
        investors: List<Investor>,
        inventory: List<InventoryItem>,
        sellers: List<ShopSeller>,
        sellerMovements: List<SellerMovement>,
        expenses: List<ShopExpense>,
        voiceNotes: List<VoiceNoteRecord>,
        gold18kPrice: Double,
        usdPrice: Double
    ): LinkedHashMap<String, List<List<Any>>> {
        val tabs = LinkedHashMap<String, List<List<Any>>>()

        tabs["Summary_Stats"] = listOf(
            listOf("شاخص آماری زرنگار", "مقدار / فرمول زنده", "توضیح"),
            listOf("تعداد کل تراکنش‌ها", "=COUNTA(Transactions!A2:A)", "تعداد تراکنش‌های ثبت‌شده"),
            listOf("مجموع فروش (تومان)", "=SUMIF(Transactions!C2:C, \"فروش روزانه\", Transactions!M2:M)", "کل فروش خرده فروشی"),
            listOf("سود ناخالص فروش (تومان)", "=SUMIF(Transactions!C2:C, \"فروش روزانه\", Transactions!N2:N)", "سود محاسبه‌شده معاملات"),
            listOf("مجموع مخارج مغازه (تومان)", "=SUM(Expenses!D2:D)", "کل هزینه‌های ثبت‌شده مغازه"),
            listOf("سود خالص پس از کسر مخارج", "=B4-B5", "سود واقعی مغازه"),
            listOf("جمع موجودی نقدی حساب‌ها (تومان)", "=SUM(Accounts!D2:D)", "کل نقدینگی حساب‌ها و کارت‌ها"),
            listOf("جمع معادل گرمی حساب‌ها (گرم ۱۸)", "=SUM(Accounts!G2:G)", "معادل گرمی تمام حساب‌ها و تنخواه"),
            listOf("جمع وزن طلای انبار (گرم)", "=SUM(Inventory!F2:F)", "کل وزن طلای موجود در انبار"),
            listOf("تعداد پیام‌های صوتی تحلیل‌شده", "=COUNTA(Voice_Messages!A2:A)", "پیام‌های صوتی ثبت‌شده")
        )

        tabs["Voice_Messages"] = listOf(
            listOf("شناسه", "تاریخ و ساعت", "متن پیام صوتی", "عملیات", "نوع طلا / ارز", "نوع قطعه", "وزن (گرم)", "عیار", "دلار", "مبلغ نقدی", "مشتری / همکار", "فروشنده", "حالت ثبت")
        ) + voiceNotes.map { n ->
            listOf(
                n.id,
                n.recordedDateTimeFa.ifBlank { Formatters.formatMillis(n.recordedAtMillis) },
                n.rawTranscript,
                n.actionEnum.labelFa,
                n.categoryEnum.labelFa,
                n.pieceTypeEnum.labelFa,
                n.weightGrams,
                n.karat,
                n.usdAmount,
                n.cashToman,
                n.counterpartyName,
                n.sellerName ?: "-",
                if (n.createdOffline) "آفلاین" else "آنلاین"
            )
        }

        tabs["Transactions"] = listOf(
            listOf(
                "شناسه", "شماره فاکتور", "نوع تراکنش", "دسته‌بندی", "تاریخ",
                "مشتری / طرف حساب", "فروشنده", "حساب مغازه", "وزن (گرم)", "عیار",
                "نرخ واحد (تومان)", "اجرت (%)", "مبلغ کل (تومان)", "سود خالص (تومان)",
                "روش پرداخت", "وضعیت", "توضیحات"
            )
        ) + transactions.map { t ->
            listOf(
                t.id, t.invoiceNumber, t.txTypeEnum.labelFa, t.categoryEnum.labelFa,
                Formatters.formatMillis(t.effectiveTimeMillis), t.customerName ?: "-",
                t.sellerName ?: "-", t.accountName, t.weightGrams, t.karat,
                t.unitPriceToman, t.wagePercent, t.totalAmountToman, t.calculatedProfitToman,
                t.paymentMethodEnum.labelFa, t.paymentStatusEnum.labelFa, t.notes ?: ""
            )
        }

        tabs["Accounts"] = listOf(
            listOf("شناسه", "نام حساب / کارت", "نوع", "موجودی تومان", "موجودی دلار", "ذخیره طلا (گرم ۱۸)", "معادل کل گرمی طلا (۱۸)", "شماره کارت / شبا")
        ) + accounts.map { a ->
            listOf(
                a.id, a.name, a.typeEnum.labelFa, a.balanceToman, a.balanceUsd, a.goldGrams18k,
                a.goldEquivalent18kGrams(gold18kPrice, usdPrice), a.accountNumber ?: "-"
            )
        }

        tabs["Sellers"] = listOf(
            listOf("شناسه", "نام فروشنده", "سمت", "تلفن", "طلای ورودی (گرم)", "طلای خروجی (گرم)", "خالص طلا (گرم)", "دلار ورودی", "دلار خروجی", "خالص دلار")
        ) + sellers.map { s ->
            listOf(s.id, s.name, s.roleTitle, s.phone, s.goldInGrams, s.goldOutGrams, s.netGoldGrams, s.usdIn, s.usdOut, s.netUsd)
        }

        tabs["Seller_Movements"] = listOf(
            listOf("شناسه", "تاریخ و ساعت", "فروشنده", "جهت", "نوع طلا / ارز", "وزن (گرم)", "عیار", "دلار", "مبلغ نقدی", "توضیحات")
        ) + sellerMovements.map { m ->
            listOf(m.id, Formatters.formatMillis(m.timestampMillis), m.sellerName, m.directionEnum.labelFa, m.categoryEnum.labelFa, m.weightGrams, m.karat, m.usdAmount, m.cashToman, m.description)
        }

        tabs["Expenses"] = listOf(
            listOf("شناسه", "تاریخ و ساعت", "عنوان هزینه", "مبلغ (تومان)", "معادل طلای ۱۸ عیار (گرم)", "دسته‌بندی", "حساب پرداخت", "ثبت‌کننده", "توضیحات")
        ) + expenses.map { e ->
            listOf(e.id, Formatters.formatMillis(e.timestampMillis), e.title, e.amountToman, e.goldEquivalentGrams, e.categoryEnum.labelFa, e.accountName, e.sellerName ?: "-", e.notes ?: "")
        }

        tabs["Inventory"] = listOf(
            listOf("شناسه", "عنوان کالا", "دسته", "نوع قطعه", "عیار", "وزن موجود (گرم)", "تعداد", "میانگین قیمت خرید", "اجرت پیش‌فرض (%)", "کد انگ")
        ) + inventory.map { s ->
            listOf(s.id, s.title, s.categoryEnum.labelFa, s.pieceTypeEnum.labelFa, s.karat, s.weightGrams, s.quantity, s.avgBuyPricePerUnit, s.defaultWagePercent, s.labCode ?: "-")
        }

        tabs["Customers"] = listOf(
            listOf("شناسه", "نام مشتری", "تلفن", "کد ملی", "تراز طلا (گرم)", "تراز نقدی (تومان)", "جمع خرید (تومان)", "یادداشت")
        ) + customers.map { c ->
            listOf(c.id, c.fullName, c.phone, c.nationalId ?: "-", c.goldBalanceGrams, c.cashBalanceToman, c.totalPurchasedToman, c.notes ?: "")
        }

        tabs["Investors"] = listOf(
            listOf("شناسه", "نام سرمایه‌گذار", "تلفن", "نوع طلا", "وزن سرمایه‌گذاری (گرم)", "سرمایه نقدی (تومان)", "سهم سود (%)", "سود پرداخت‌شده", "وضعیت")
        ) + investors.map { i ->
            listOf(i.id, i.fullName, i.phone, i.categoryEnum.labelFa, i.investedGrams, i.investedCashToman, i.profitSharePercent, i.realizedProfitToman, i.statusEnum.labelFa)
        }

        return tabs
    }

    fun buildCsvExport(
        transactions: List<GoldTransaction>,
        expenses: List<ShopExpense>,
        voiceNotes: List<VoiceNoteRecord>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("--- تراکنش‌ها ---")
        sb.appendLine("شماره فاکتور,نوع,دسته,تاریخ,مشتری,فروشنده,وزن(گرم),عیار,مبلغ کل(تومان),سود(تومان)")
        transactions.forEach { t ->
            sb.appendLine("${t.invoiceNumber},${t.txTypeEnum.labelFa},${t.categoryEnum.labelFa},${Formatters.formatMillis(t.effectiveTimeMillis)},${t.customerName ?: "-"},${t.sellerName ?: "-"},${t.weightGrams},${t.karat},${t.totalAmountToman.toLong()},${t.calculatedProfitToman.toLong()}")
        }
        sb.appendLine()
        sb.appendLine("--- مخارج مغازه ---")
        sb.appendLine("تاریخ,عنوان,دسته,مبلغ(تومان),معادل طلا(گرم),حساب")
        expenses.forEach { e ->
            sb.appendLine("${Formatters.formatMillis(e.timestampMillis)},${e.title},${e.categoryEnum.labelFa},${e.amountToman.toLong()},${e.goldEquivalentGrams},${e.accountName}")
        }
        sb.appendLine()
        sb.appendLine("--- پیام‌های صوتی ---")
        sb.appendLine("تاریخ و ساعت,متن,عملیات,نوع,وزن(گرم),دلار,مشتری/همکار")
        voiceNotes.forEach { v ->
            sb.appendLine("${v.recordedDateTimeFa},\"${v.rawTranscript.replace("\"", "'")}\",${v.actionEnum.labelFa},${v.categoryEnum.labelFa},${v.weightGrams},${v.usdAmount},${v.counterpartyName}")
        }
        return sb.toString()
    }
}
