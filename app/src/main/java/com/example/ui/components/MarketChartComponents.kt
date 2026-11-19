package com.example.ui.components

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.PaymentGatewayCheckoutResult
import com.example.model.GoldTransaction
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalZarnegarColors
import com.example.utils.Formatters
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

@Composable
fun MinimalCard(
    modifier: Modifier = Modifier,
    containerColor: Color = LocalZarnegarColors.current.card,
    borderColor: Color = LocalZarnegarColors.current.border,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
fun MinimalSectionHeader(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalZarnegarColors.current.mutedText
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun ZarnegarEmptyState(
    title: String,
    subtitle: String,
    icon: ImageVector = Icons.Outlined.Inbox,
    modifier: Modifier = Modifier
) {
    val ext = LocalZarnegarColors.current
    MinimalCard(
        modifier = modifier,
        contentPadding = PaddingValues(28.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = ext.mutedText,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun generateCheckoutQrBitmap(content: String, sizePx: Int = 340): ImageBitmap? {
    return try {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bmp = createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bmp[x, y] = if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            }
        }
        bmp.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}

@Composable
fun GatewayCheckoutDialog(
    tx: GoldTransaction,
    checkout: PaymentGatewayCheckoutResult,
    onOpenGatewayBrowser: (String) -> Unit,
    onConfirmPaid: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val ext = LocalZarnegarColors.current
    var refIdInput by remember { mutableStateOf(checkout.authority.takeLast(8)) }
    val qrBitmap = remember(checkout.checkoutUrl) { generateCheckoutQrBitmap(checkout.checkoutUrl, 340) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ext.card,
        title = {
            Text(
                text = "درگاه پرداخت آنلاین",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "فاکتور ${tx.invoiceNumber} به مبلغ ${Formatters.formatToman(tx.totalAmountToman)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = checkout.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = ext.profit
                )
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap,
                        contentDescription = "QR لینک پرداخت",
                        modifier = Modifier
                            .size(160.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .padding(6.dp)
                    )
                }
                OutlinedButton(
                    onClick = { onOpenGatewayBrowser(checkout.checkoutUrl) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.OpenInBrowser, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("باز کردن صفحه درگاه پرداخت")
                }
                OutlinedTextField(
                    value = refIdInput,
                    onValueChange = { refIdInput = it },
                    label = { Text("کد رهگیری پرداخت (RefID)") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmPaid(refIdInput.ifBlank { checkout.authority }) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("تایید پرداخت")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("نگهداری در انتظار", color = ext.mutedText)
            }
        }
    )
}
