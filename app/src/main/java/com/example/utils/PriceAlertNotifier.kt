package com.example.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.model.AlertAssetSymbol
import com.example.model.AlertCondition
import com.example.model.MarketSnapshot
import com.example.model.PriceAlert

object PriceAlertNotifier {
    private const val CHANNEL_ID = "zarnegar_price_alerts"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "هشدارهای قیمتی طلا و ارز",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "اعلان لحظه‌ای رسیدن نرخ طلا و دلار به حد هدف تعیین‌شده"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun evaluateAndNotify(
        context: Context,
        alerts: List<PriceAlert>,
        snapshot: MarketSnapshot,
        onAlertTriggered: (String) -> Unit
    ) {
        ensureChannel(context)
        val hasPerm = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        alerts.filter { it.enabled && !it.triggered }.forEach { alert ->
            val currentPrice = when (alert.assetEnum) {
                AlertAssetSymbol.GOLD_18K -> snapshot.gold18kGramToman
                AlertAssetSymbol.MELTED_MESGHAL -> snapshot.meltedMesghalToman
                AlertAssetSymbol.BULLION_24K -> snapshot.gold24kGramToman
                AlertAssetSymbol.XAU_OUNCE -> snapshot.xauOunceUsd
                AlertAssetSymbol.USD_RATE -> snapshot.usdToToman
            }
            if (currentPrice <= 0.0) return@forEach

            val hit = when (alert.conditionEnum) {
                AlertCondition.ABOVE -> currentPrice >= alert.targetPrice
                AlertCondition.BELOW -> currentPrice <= alert.targetPrice
            }
            if (hit) {
                onAlertTriggered(alert.id)
                if (hasPerm) {
                    val valueFormatted = if (alert.assetEnum == AlertAssetSymbol.XAU_OUNCE) {
                        Formatters.formatUsd(currentPrice)
                    } else {
                        Formatters.formatToman(currentPrice)
                    }
                    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle("هشدار قیمت زرنگار: ${alert.assetEnum.labelFa}")
                        .setContentText("نرخ لحظه‌ای به $valueFormatted رسید (${alert.conditionEnum.labelFa})")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .build()
                    NotificationManagerCompat.from(context).notify(alert.id.hashCode(), notification)
                }
            }
        }
    }
}
