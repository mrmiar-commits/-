package com.example.data

import android.util.Log
import com.example.model.ManualPricingConfig
import com.example.model.MarketSnapshot
import com.example.utils.Formatters
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GoldApiPriceResponse(
    val name: String? = null,
    val price: Double? = null,
    val symbol: String? = null,
    val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class OpenErApiResponse(
    val result: String? = null,
    @Json(name = "base_code") val baseCode: String? = null,
    val rates: Map<String, Double>? = null
)

interface GoldSpotApi {
    @GET("price/XAU")
    suspend fun getXauUsd(): GoldApiPriceResponse
}

interface FxRatesApi {
    @GET("v6/latest/USD")
    suspend fun getUsdRates(): OpenErApiResponse
}

class MarketRateRepository {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    private val goldApi = Retrofit.Builder()
        .baseUrl("https://api.gold-api.com/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(GoldSpotApi::class.java)

    private val fxApi = Retrofit.Builder()
        .baseUrl("https://open.er-api.com/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(FxRatesApi::class.java)

    suspend fun fetchLiveMarketSnapshot(
        calibratedUsdToman: Double?,
        manualPricing: ManualPricingConfig = ManualPricingConfig()
    ): MarketSnapshot = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        var xauUsd = 2650.0
        var eurPerUsd = 0.93
        var isLive = false
        var officialIrrPerUsd = 420000.0

        try {
            val spot = goldApi.getXauUsd()
            if ((spot.price ?: 0.0) > 500.0) {
                xauUsd = spot.price!!
                isLive = true
            }
        } catch (e: Exception) {
            Log.w("MarketRateRepo", "Gold spot fetch failed: ${e.message}")
        }

        try {
            val fx = fxApi.getUsdRates()
            val rates = fx.rates
            if (rates != null) {
                eurPerUsd = rates["EUR"] ?: eurPerUsd
                officialIrrPerUsd = rates["IRR"] ?: officialIrrPerUsd
                isLive = true
            }
        } catch (e: Exception) {
            Log.w("MarketRateRepo", "FX fetch failed: ${e.message}")
        }

        val effectiveUsdToman = when {
            manualPricing.useManualPrice && manualPricing.manualUsdToman > 1000.0 -> manualPricing.manualUsdToman
            calibratedUsdToman != null && calibratedUsdToman > 1000.0 -> calibratedUsdToman
            else -> (officialIrrPerUsd / 10.0).coerceAtLeast(70000.0)
        }
        val eurToToman = effectiveUsdToman / eurPerUsd.coerceAtLeast(0.5)

        val apiGold24k = (xauUsd / 31.1034768) * effectiveUsdToman
        val apiGold18k = apiGold24k * 0.75
        val apiMesghal = apiGold24k * 4.608 * 0.705

        if (manualPricing.useManualPrice && manualPricing.manualGold18kToman > 10000.0) {
            val gold18k = manualPricing.manualGold18kToman
            val gold24k = if (manualPricing.manualBullion24kToman > 10000.0) {
                manualPricing.manualBullion24kToman
            } else {
                gold18k / 0.75
            }
            val mesghal = if (manualPricing.manualMeltedMesghalToman > 10000.0) {
                manualPricing.manualMeltedMesghalToman
            } else {
                gold24k * 4.608 * 0.705
            }
            return@withContext MarketSnapshot(
                xauOunceUsd = xauUsd,
                usdToToman = effectiveUsdToman,
                eurToToman = eurToToman,
                gold24kGramToman = gold24k,
                gold18kGramToman = gold18k,
                meltedMesghalToman = mesghal,
                lastUpdatedText = Formatters.formatTimeOnly(now),
                isLiveNetwork = isLive,
                isManualOverride = true,
                sourceNote = "نرخ دستی مغازه (قابل تغییر)"
            )
        }

        MarketSnapshot(
            xauOunceUsd = xauUsd,
            usdToToman = effectiveUsdToman,
            eurToToman = eurToToman,
            gold24kGramToman = apiGold24k,
            gold18kGramToman = apiGold18k,
            meltedMesghalToman = apiMesghal,
            lastUpdatedText = Formatters.formatTimeOnly(now),
            isLiveNetwork = isLive,
            isManualOverride = false,
            sourceNote = if (isLive) "استعلام آنلاین API (انس جهانی + نرخ دلار)" else "نرخ محاسباتی آفلاین"
        )
    }
}
