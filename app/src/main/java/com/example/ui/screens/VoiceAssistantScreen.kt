package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.GoogleSheetsSyncService
import com.example.model.*
import com.example.ui.components.MinimalCard
import com.example.ui.components.MinimalSectionHeader
import com.example.ui.components.ZarnegarEmptyState
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalZarnegarColors
import com.example.utils.Formatters
import com.example.utils.VoiceMessageParser
import com.example.viewmodel.ZarnegarViewModel

@Composable
fun VoiceAssistantScreen(
    viewModel: ZarnegarViewModel,
    onOpenSheetsSetup: () -> Unit
) {
    val context = LocalContext.current
    val ext = LocalZarnegarColors.current

    val voiceNotes by viewModel.voiceNotes.collectAsStateWithLifecycle()
    val sellers by viewModel.sellers.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncingSheets.collectAsStateWithLifecycle()

    var transcript by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var micStatusText by remember { mutableStateOf("دکمه میکروفون را بزنید یا متن پیام را بنویسید") }
    var selectedSeller by remember { mutableStateOf<ShopSeller?>(null) }
    var alsoRecordAccounting by remember { mutableStateOf(false) }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }

    fun startVoiceListening() {
        val recognizer = speechRecognizer
        if (recognizer == null) {
            micStatusText = "سرویس تبدیل گفتار روی این دستگاه فعال نیست؛ می‌توانید متن را تایپ کنید."
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        isListening = true
        micStatusText = "در حال گوش دادن... (مثلاً: ۱.۵ گرم آبشده به رضا)"
        recognizer.startListening(intent)
    }

    DisposableEffect(speechRecognizer) {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                micStatusText = "گوش می‌کنم... صحبت کنید"
            }

            override fun onBeginningOfSpeech() {
                micStatusText = "در حال دریافت صدا..."
            }

            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                isListening = false
                micStatusText = "در حال تحلیل کلیدواژه‌ها..."
            }

            override fun onError(error: Int) {
                isListening = false
                micStatusText = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "صدایی تشخیص داده نشد؛ دوباره تلاش کنید یا متن را تایپ کنید."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "اینترنت قطع است؛ اگر بسته آفلاین فارسی نصب نیست، پیام را تایپ کنید تا پس از اتصال خودکار در گوگل شیت ثبت شود."
                    SpeechRecognizer.ERROR_AUDIO -> "خطا در دسترسی به میکروفون دستگاه."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "دسترسی میکروفون داده نشده است."
                    else -> "آماده دریافت پیام صوتی یا متنی"
                }
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!spoken.isNullOrBlank()) {
                    transcript = spoken
                    micStatusText = "پیام صوتی دریافت و تحلیل شد"
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!partial.isNullOrBlank()) {
                    transcript = partial
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        onDispose {
            runCatching { speechRecognizer?.stopListening() }
            runCatching { speechRecognizer?.destroy() }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            startVoiceListening()
        } else {
            micStatusText = "برای ضبط صوتی، دسترسی میکروفون لازم است. همچنین می‌توانید پیام را بنویسید."
        }
    }

    val isOnlineNow = GoogleSheetsSyncService.isNetworkAvailable(context)
    val parsedPreview = remember(transcript, selectedSeller, isOnlineNow) {
        if (transcript.isBlank()) null
        else VoiceMessageParser.parse(
            rawText = transcript,
            timestampMillis = System.currentTimeMillis(),
            activeSellerName = selectedSeller?.name,
            isOffline = !isOnlineNow
        )
    }

    val pendingCount = remember(voiceNotes) { voiceNotes.count { !it.sheetsSynced } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Header & Online/Offline Status
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("پیامگیر صوتی هوشمند", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "تحلیل کلیدواژه‌ها و ثبت خودکار در گوگل شیت با تاریخ و ساعت",
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.mutedText
                    )
                }
                Surface(
                    color = if (isOnlineNow) ext.profitContainer else ext.lossContainer,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = if (isOnlineNow) "آنلاین" else "آفلاین (ذخیره در صف)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOnlineNow) ext.profit else ext.loss,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // 2. Sheets Connection Warning if webhook missing
        if (settings.sheetsWebhookUrl.isNullOrBlank()) {
            item {
                MinimalCard(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("لینک اتصال گوگل شیت تنظیم نشده", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "پیام‌ها آفلاین ذخیره می‌شوند و پس از ثبت لینک در بخش «گزارش و شیت» خودکار ارسال می‌شوند.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.mutedText
                            )
                        }
                        TextButton(onClick = onOpenSheetsSetup) { Text("تنظیم شیت") }
                    }
                }
            }
        }

        // 3. Mic Recorder Card + Editable Transcript
        item {
            val micBg by animateColorAsState(
                targetValue = if (isListening) ext.loss else MaterialTheme.colorScheme.primary,
                label = "micBg"
            )
            MinimalCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        onClick = {
                            if (isListening) {
                                speechRecognizer?.stopListening()
                                isListening = false
                                micStatusText = "ضبط متوقف شد"
                            } else if (hasMicPermission) {
                                startVoiceListening()
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        shape = CircleShape,
                        color = micBg,
                        modifier = Modifier
                            .size(72.dp)
                            .testTag("voice_record_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                                contentDescription = if (isListening) "توقف ضبط" else "شروع ضبط پیام صوتی",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Text(
                        text = micStatusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isListening) ext.loss else ext.mutedText
                    )

                    OutlinedTextField(
                        value = transcript,
                        onValueChange = { transcript = it },
                        label = { Text("متن پیام صوتی (قابل ویرایش)") },
                        placeholder = { Text("مثال: 1.5 گرم آبشده به رضا") },
                        minLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("voice_transcript_input")
                    )

                    // Quick Sample Phrases
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val samples = listOf(
                            "1.5 گرم آبشده به رضا",
                            "۳ گرم طلای نو انگشتر به خانم احمدی",
                            "۵۰۰ دلار از کریم",
                            "۲.۴ گرم طلای کارکرده بدون اجرت از محمد",
                            "۸۰۰ هزار تومان خرج پیک مغازه"
                        )
                        items(samples) { sample ->
                            AssistChip(
                                onClick = { transcript = sample },
                                label = { Text(sample, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
        }

        // 4. Live Keyword Analysis Preview Card
        if (parsedPreview != null) {
            item {
                MinimalCard(
                    containerColor = ext.card,
                    borderColor = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = "تحلیل هوشمند کلیدواژه‌ها",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider(color = ext.border)

                    if (parsedPreview.weightGrams > 0) {
                        ParsedRow("وزن طلا:", "${Formatters.formatGrams(parsedPreview.weightGrams)} (عیار ${Formatters.toPersianDigits(parsedPreview.karat.toString())})")
                    }
                    if (parsedPreview.usdAmount > 0) {
                        ParsedRow("مقدار دلار:", Formatters.formatUsd(parsedPreview.usdAmount))
                    }
                    if (parsedPreview.cashToman > 0) {
                        ParsedRow("مبلغ نقدی:", Formatters.formatToman(parsedPreview.cashToman))
                    }
                    ParsedRow("نوع طلا / ارز:", "${parsedPreview.categoryEnum.labelFa} (${parsedPreview.pieceTypeEnum.labelFa})")
                    ParsedRow("نوع عملیات:", parsedPreview.actionEnum.labelFa)
                    ParsedRow("مشتری / همکار:", parsedPreview.counterpartyName)
                    ParsedRow("تاریخ و ساعت:", parsedPreview.recordedDateTimeFa)

                    if (sellers.isNotEmpty()) {
                        Text("فروشنده ثبت‌کننده:", style = MaterialTheme.typography.labelSmall, color = ext.mutedText)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item {
                                FilterChip(
                                    selected = selectedSeller == null,
                                    onClick = { selectedSeller = null },
                                    label = { Text("مدیر مغازه") }
                                )
                            }
                            items(sellers) { s ->
                                FilterChip(
                                    selected = selectedSeller?.id == s.id,
                                    onClick = { selectedSeller = s },
                                    label = { Text(s.name) }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = alsoRecordAccounting,
                            onCheckedChange = { alsoRecordAccounting = it }
                        )
                        Text(
                            text = "ثبت همزمان در تراکنش‌ها / مخارج مغازه",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Button(
                        onClick = {
                            val now = System.currentTimeMillis()
                            viewModel.saveVoiceNote(
                                context = context,
                                parsed = parsedPreview.copy(
                                    recordedAtMillis = now,
                                    recordedDateTimeFa = Formatters.formatMillis(now)
                                ),
                                alsoRecordAsAccountingEntry = alsoRecordAccounting
                            )
                            transcript = ""
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("save_voice_note_button")
                    ) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isOnlineNow) "ثبت و ارسال به گوگل شیت"
                            else "ذخیره آفلاین (ارسال خودکار با اتصال نت)"
                        )
                    }
                }
            }
        }

        // 5. Pending Offline Queue Flush Button
        if (pendingCount > 0) {
            item {
                MinimalCard(
                    containerColor = ext.elevated,
                    borderColor = ext.amber.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${Formatters.toPersianDigits(pendingCount.toString())} پیام در صف ارسال به گوگل شیت",
                                style = MaterialTheme.typography.titleSmall,
                                color = ext.amber
                            )
                            Text(
                                text = "به محض اتصال اینترنت به‌صورت خودکار ارسال می‌شوند",
                                style = MaterialTheme.typography.labelSmall,
                                color = ext.mutedText
                            )
                        }
                        FilledTonalButton(
                            onClick = { viewModel.retryPendingVoiceSync(context) },
                            enabled = !isSyncing,
                            modifier = Modifier.testTag("flush_voice_queue_button")
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text("ارسال اکنون")
                            }
                        }
                    }
                }
            }
        }

        // 6. Saved Voice Notes History
        item {
            MinimalSectionHeader(
                title = "تاریخچه پیام‌های صوتی",
                subtitle = "${Formatters.toPersianDigits(voiceNotes.size.toString())} پیام ثبت شده"
            )
        }

        if (voiceNotes.isEmpty()) {
            item {
                ZarnegarEmptyState(
                    title = "پیام صوتی ثبت نشده است",
                    subtitle = "کافیست بگویید «۱.۵ گرم آبشده به رضا» تا وزن، نوع، طرف حساب و ساعت دقیق استخراج و در گوگل شیت ثبت شود.",
                    icon = Icons.Outlined.MicNone
                )
            }
        } else {
            items(voiceNotes, key = { it.id }) { note ->
                MinimalCard(contentPadding = PaddingValues(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "«${note.rawTranscript}»",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = if (note.sheetsSynced) ext.profitContainer else ext.elevated,
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = if (note.sheetsSynced) "ثبت در شیت" else "در صف ارسال",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (note.sheetsSynced) ext.profit else ext.amber,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.deleteVoiceNote(note.id) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "حذف پیام", tint = ext.mutedText, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    HorizontalDivider(color = ext.border)
                    val details = buildList {
                        if (note.weightGrams > 0) add("وزن: ${Formatters.formatGrams(note.weightGrams)}")
                        if (note.usdAmount > 0) add("دلار: ${Formatters.formatUsd(note.usdAmount)}")
                        if (note.cashToman > 0) add("مبلغ: ${Formatters.formatToman(note.cashToman, false)}")
                        add("نوع: ${note.categoryEnum.labelFa}")
                        add("طرف حساب: ${note.counterpartyName}")
                    }.joinToString("  •  ")
                    Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        text = "${note.actionEnum.labelFa} • ${note.recordedDateTimeFa}${if (!note.sellerName.isNullOrBlank()) " • فروشنده: ${note.sellerName}" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ext.mutedText
                    )
                }
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun ParsedRow(label: String, value: String) {
    val ext = LocalZarnegarColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = ext.mutedText)
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
    }
}
