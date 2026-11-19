package com.example.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.ManualPricingConfig
import com.example.model.PaymentProvider
import com.example.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.zarnegarDataStore: DataStore<Preferences> by preferencesDataStore(name = "zarnegar_prefs")

class SecurityPreferences(private val context: Context) {
    private val darkThemeKey = booleanPreferencesKey("dark_theme_enabled")
    private val calibratedUsdKey = doublePreferencesKey("calibrated_usd_global")
    private val useManualPriceKey = booleanPreferencesKey("use_manual_gold_price")
    private val manualGold18kKey = doublePreferencesKey("manual_gold_18k_toman")
    private val manualMesghalKey = doublePreferencesKey("manual_melted_mesghal_toman")
    private val manualBullion24kKey = doublePreferencesKey("manual_bullion_24k_toman")
    private val manualUsdKey = doublePreferencesKey("manual_usd_toman")
    private val shopNameKey = stringPreferencesKey("shop_name")
    private val ownerNameKey = stringPreferencesKey("owner_name")
    private val sheetsWebhookUrlKey = stringPreferencesKey("sheets_webhook_url")
    private val spreadsheetIdKey = stringPreferencesKey("spreadsheet_id")
    private val paymentProviderKey = stringPreferencesKey("payment_provider")
    private val merchantIdKey = stringPreferencesKey("merchant_id")

    fun observeDarkTheme(): Flow<Boolean> =
        context.zarnegarDataStore.data.map { it[darkThemeKey] ?: false }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.zarnegarDataStore.edit { it[darkThemeKey] = enabled }
    }

    fun observeCalibratedUsdToman(): Flow<Double?> =
        context.zarnegarDataStore.data.map { it[calibratedUsdKey] }

    suspend fun saveCalibratedUsdToman(rateToman: Double) {
        context.zarnegarDataStore.edit { it[calibratedUsdKey] = rateToman }
    }

    fun observeManualPricing(): Flow<ManualPricingConfig> =
        context.zarnegarDataStore.data.map { prefs ->
            ManualPricingConfig(
                useManualPrice = prefs[useManualPriceKey] ?: false,
                manualGold18kToman = prefs[manualGold18kKey] ?: 0.0,
                manualMeltedMesghalToman = prefs[manualMesghalKey] ?: 0.0,
                manualBullion24kToman = prefs[manualBullion24kKey] ?: 0.0,
                manualUsdToman = prefs[manualUsdKey] ?: 0.0
            )
        }

    suspend fun saveManualPricing(config: ManualPricingConfig) {
        context.zarnegarDataStore.edit { prefs ->
            prefs[useManualPriceKey] = config.useManualPrice
            prefs[manualGold18kKey] = config.manualGold18kToman
            prefs[manualMesghalKey] = config.manualMeltedMesghalToman
            prefs[manualBullion24kKey] = config.manualBullion24kToman
            prefs[manualUsdKey] = config.manualUsdToman
            if (config.manualUsdToman > 1000.0) {
                prefs[calibratedUsdKey] = config.manualUsdToman
            }
        }
    }

    fun observeLocalSettings(): Flow<UserSettings> =
        context.zarnegarDataStore.data.map { prefs ->
            UserSettings(
                id = "local_settings",
                userId = "local",
                shopName = prefs[shopNameKey] ?: "طلافروشی زرنگار",
                ownerName = prefs[ownerNameKey] ?: "مدیر مغازه",
                totpEnabled = false,
                biometricEnabled = false,
                spreadsheetId = prefs[spreadsheetIdKey],
                sheetsWebhookUrl = prefs[sheetsWebhookUrlKey],
                paymentProvider = prefs[paymentProviderKey] ?: PaymentProvider.ZARINPAL.name,
                merchantId = prefs[merchantIdKey]
            )
        }

    suspend fun getSheetsWebhookUrlNow(): String? =
        context.zarnegarDataStore.data.first()[sheetsWebhookUrlKey]

    suspend fun saveShopProfile(
        shopName: String,
        ownerName: String,
        paymentProvider: PaymentProvider,
        merchantId: String?,
        spreadsheetId: String?,
        sheetsWebhookUrl: String?
    ) {
        context.zarnegarDataStore.edit { prefs ->
            prefs[shopNameKey] = shopName.trim().ifBlank { "طلافروشی زرنگار" }
            prefs[ownerNameKey] = ownerName.trim().ifBlank { "مدیر مغازه" }
            prefs[paymentProviderKey] = paymentProvider.name
            if (merchantId.isNullOrBlank()) prefs.remove(merchantIdKey) else prefs[merchantIdKey] = merchantId.trim()
            if (spreadsheetId.isNullOrBlank()) prefs.remove(spreadsheetIdKey) else prefs[spreadsheetIdKey] = spreadsheetId.trim()
            if (sheetsWebhookUrl.isNullOrBlank()) prefs.remove(sheetsWebhookUrlKey) else prefs[sheetsWebhookUrlKey] = sheetsWebhookUrl.trim()
        }
    }

    suspend fun saveSheetsWebhookUrl(url: String) {
        context.zarnegarDataStore.edit { prefs ->
            val trimmed = url.trim()
            if (trimmed.isEmpty()) prefs.remove(sheetsWebhookUrlKey) else prefs[sheetsWebhookUrlKey] = trimmed
        }
    }

    suspend fun saveSpreadsheetId(id: String) {
        context.zarnegarDataStore.edit { prefs ->
            val trimmed = id.trim()
            if (trimmed.isEmpty()) prefs.remove(spreadsheetIdKey) else prefs[spreadsheetIdKey] = trimmed
        }
    }
}
