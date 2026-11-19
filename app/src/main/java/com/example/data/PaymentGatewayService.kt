package com.example.data

import com.example.model.PaymentProvider
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class ZarinPalPaymentRequest(
    @Json(name = "merchant_id") val merchantId: String,
    val amount: Long,
    val description: String,
    @Json(name = "callback_url") val callbackUrl: String
)

@JsonClass(generateAdapter = true)
data class ZarinPalData(
    val code: Int? = null,
    val authority: String? = null,
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class ZarinPalPaymentResponse(
    val data: ZarinPalData? = null
)

interface ZarinPalApi {
    @POST("pg/v4/payment/request.json")
    suspend fun requestPayment(@Body body: ZarinPalPaymentRequest): ZarinPalPaymentResponse
}

data class PaymentGatewayCheckoutResult(
    val authority: String,
    val checkoutUrl: String,
    val isLiveApi: Boolean,
    val message: String
)

class PaymentGatewayService {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val zarinPalApi = Retrofit.Builder()
        .baseUrl("https://api.zarinpal.com/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(ZarinPalApi::class.java)

    suspend fun createPaymentSession(
        provider: PaymentProvider,
        merchantId: String?,
        amountToman: Double,
        invoiceNumber: String,
        customerName: String?
    ): Result<PaymentGatewayCheckoutResult> = withContext(Dispatchers.IO) {
        val cleanMerchant = merchantId?.trim().orEmpty()
        if (cleanMerchant.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("شناسه پذیرنده (Merchant ID) در تنظیمات درگاه پرداخت وارد نشده است.")
            )
        }

        val amountRials = (amountToman * 10.0).toLong().coerceAtLeast(10000L)
        val desc = "فاکتور $invoiceNumber - ${customerName ?: "مشتری طلافروشی"}"

        return@withContext when (provider) {
            PaymentProvider.ZARINPAL -> {
                try {
                    val response = zarinPalApi.requestPayment(
                        ZarinPalPaymentRequest(
                            merchantId = cleanMerchant,
                            amount = amountRials,
                            description = desc,
                            callbackUrl = "https://zarnegar.app/payment/callback"
                        )
                    )
                    val authority = response.data?.authority
                    if (!authority.isNullOrBlank()) {
                        Result.success(
                            PaymentGatewayCheckoutResult(
                                authority = authority,
                                checkoutUrl = "https://www.zarinpal.com/pg/StartPay/$authority",
                                isLiveApi = true,
                                message = "لینک پرداخت زرین‌پال با موفقیت صادر شد"
                            )
                        )
                    } else {
                        Result.failure(
                            IllegalStateException("درگاه زرین‌پال کد نامعتبر برگرداند: ${response.data?.message ?: "نامشخص"}")
                        )
                    }
                } catch (e: Exception) {
                    Result.failure(
                        IllegalStateException("خطا در اتصال به وب‌سرویس درگاه زرین‌پال: ${e.localizedMessage}")
                    )
                }
            }
            PaymentProvider.IDPAY -> {
                val encodedOrder = URLEncoder.encode(invoiceNumber, "UTF-8")
                Result.success(
                    PaymentGatewayCheckoutResult(
                        authority = "IDP-$encodedOrder",
                        checkoutUrl = "https://idpay.ir/$cleanMerchant/$amountRials?order_id=$encodedOrder",
                        isLiveApi = true,
                        message = "لینک درگاه اختصاصی آیدی‌پی آماده شد"
                    )
                )
            }
            PaymentProvider.CUSTOM_LINK -> {
                val base = if (cleanMerchant.startsWith("http")) cleanMerchant else "https://$cleanMerchant"
                val sep = if (base.contains("?")) "&" else "?"
                val url = "${base}${sep}amount=$amountRials&invoice=${URLEncoder.encode(invoiceNumber, "UTF-8")}"
                Result.success(
                    PaymentGatewayCheckoutResult(
                        authority = "LNK-$invoiceNumber",
                        checkoutUrl = url,
                        isLiveApi = true,
                        message = "لینک درگاه اختصاصی آماده پرداخت شد"
                    )
                )
            }
        }
    }
}
