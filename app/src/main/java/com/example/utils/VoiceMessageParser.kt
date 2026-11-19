package com.example.utils

import com.example.model.GoldCategory
import com.example.model.GoldPieceType
import com.example.model.VoiceActionType
import com.example.model.VoiceNoteRecord

/**
 * Deterministic offline/online Persian voice-message keyword parser for gold retail shops.
 * Example: "1.5 گرم آبشده به رضا" -> weightGrams=1.5, category=MELTED_GOLD, counterparty="رضا", action=SALE_OUT
 */
object VoiceMessageParser {

    private val wordNumbers = mapOf(
        "نیم" to 0.5,
        "نصف" to 0.5,
        "ربع" to 0.25,
        "یک" to 1.0,
        "یه" to 1.0,
        "دو" to 2.0,
        "سه" to 3.0,
        "چهار" to 4.0,
        "پنج" to 5.0,
        "شش" to 6.0,
        "شیش" to 6.0,
        "هفت" to 7.0,
        "هشت" to 8.0,
        "نه" to 9.0,
        "ده" to 10.0,
        "یازده" to 11.0,
        "دوازده" to 12.0,
        "پانزده" to 15.0,
        "بیست" to 20.0,
        "سی" to 30.0,
        "چهل" to 40.0,
        "پنجاه" to 50.0,
        "صد" to 100.0
    )

    fun parse(
        rawText: String,
        timestampMillis: Long = System.currentTimeMillis(),
        activeSellerName: String? = null,
        isOffline: Boolean = false
    ): VoiceNoteRecord {
        val cleaned = normalizeText(rawText)

        // 1. Detect action direction
        val actionType = detectAction(cleaned)

        // 2. Detect GoldCategory & GoldPieceType from keywords
        val (category, pieceType, detectedKarat) = detectCategoryAndPiece(cleaned)

        // 3. Extract numeric weight (grams / mesghal / soot), USD, or Toman cash
        val (weightGrams, usdAmount, cashToman) = extractAmounts(cleaned, category, actionType)

        // 4. Detect explicit karat override if spoken (e.g. "عیار 705", "۲۴ عیار")
        val karat = extractKarat(cleaned) ?: detectedKarat

        // 5. Extract counterparty (customer / colleague)
        val counterparty = extractCounterparty(cleaned)

        return VoiceNoteRecord(
            rawTranscript = rawText.trim(),
            actionType = actionType.name,
            goldCategory = category.name,
            pieceType = pieceType.name,
            weightGrams = weightGrams,
            karat = karat,
            usdAmount = usdAmount,
            cashToman = cashToman,
            counterpartyName = counterparty,
            sellerName = activeSellerName,
            recordedAtMillis = timestampMillis,
            recordedDateTimeFa = Formatters.formatMillis(timestampMillis),
            sheetsSynced = false,
            createdOffline = isOffline
        )
    }

    private fun normalizeText(input: String): String {
        var text = Formatters.normalizeAsciiDigits(input)
            .replace("ي", "ی")
            .replace("ك", "ک")
            .replace("‌", " ")
            .replace("/", ".")
            .replace(" ممیز ", ".")
            .replace(" نقطه ", ".")
            .replace(Regex("\\s+"), " ")
            .trim()

        // Convert spoken Persian compound fractions like "یک و نیم" -> "1.5"
        text = text.replace(Regex("(یک|یه|دو|سه|چهار|پنج|شش|شیش|هفت|هشت|نه|ده|بیست)\\s+و\\s+نیم")) { m ->
            val base = wordNumbers[m.groupValues[1]] ?: 0.0
            (base + 0.5).toString()
        }
        text = text.replace(Regex("(یک|یه|دو|سه|چهار|پنج|شش|شیش|هفت|هشت|نه|ده)\\s+و\\s+ربع")) { m ->
            val base = wordNumbers[m.groupValues[1]] ?: 0.0
            (base + 0.25).toString()
        }
        text = text.replace(Regex("(\\d+)\\s+و\\s+نیم")) { m ->
            val base = m.groupValues[1].toDoubleOrNull() ?: 0.0
            (base + 0.5).toString()
        }
        // Replace standalone word numbers before units
        for ((word, num) in wordNumbers) {
            text = text.replace(Regex("(^|\\s)$word(?=\\s+(گرم|گرمی|مثقال|سوت|دلار|تومان|میلیون))")) { m ->
                "${m.groupValues[1]}$num"
            }
        }
        return text
    }

    private fun detectAction(text: String): VoiceActionType {
        return when {
            listOf("خرج", "هزینه", "اجاره", "قبض", "حقوق", "پیک", "پذیرایی", "مخارج").any { text.contains(it) } ->
                VoiceActionType.EXPENSE
            listOf("خرید", "خریدم", "گرفتم", "دریافت", "ورود", "آورد", "از ").any { text.contains(it) } ->
                VoiceActionType.PURCHASE_IN
            listOf("فروش", "فروختم", "دادم", "تحویل", "ارسال", "خروج", "به ").any { text.contains(it) } ->
                VoiceActionType.SALE_OUT
            else -> VoiceActionType.NOTE
        }
    }

    private fun detectCategoryAndPiece(text: String): Triple<GoldCategory, GoldPieceType, Int> {
        return when {
            text.contains("دلار") || text.contains("ارز") || text.contains("$") ->
                Triple(GoldCategory.USD_CURRENCY, GoldPieceType.CURRENCY_UNIT, 0)
            text.contains("آبشده") || text.contains("ابشده") || text.contains("آب شده") || text.contains("اب شده") ->
                Triple(GoldCategory.MELTED_GOLD, GoldPieceType.MELTED_BLOCK, 750)
            text.contains("شمش") || text.contains("24 عیار") || text.contains("بیست و چهار") ->
                Triple(GoldCategory.BULLION, GoldPieceType.BULLION_BAR, 999)
            text.contains("کارکرده") || text.contains("بدون اجرت") || text.contains("دست دوم") || text.contains("شکسته") ->
                Triple(GoldCategory.USED_NO_WAGE, detectPieceOnly(text, GoldPieceType.OTHER) ?: GoldPieceType.OTHER, 750)
            text.contains("سکه") || text.contains("امامی") || text.contains("بهار آزادی") ->
                Triple(GoldCategory.OTHER_GOLD, GoldPieceType.COIN, 900)
            text.contains("ویترینی") || text.contains("نو") || detectPieceOnly(text, null) != null ->
                Triple(GoldCategory.NEW_GOLD, detectPieceOnly(text, GoldPieceType.RING) ?: GoldPieceType.RING, 750)
            else -> Triple(GoldCategory.OTHER_GOLD, GoldPieceType.OTHER, 750)
        }
    }

    private fun detectPieceOnly(text: String, fallback: GoldPieceType?): GoldPieceType? {
        return when {
            text.contains("انگشتر") || text.contains("حلقه") -> GoldPieceType.RING
            text.contains("گردنبند") || text.contains("زنجیر") -> GoldPieceType.NECKLACE
            text.contains("النگو") -> GoldPieceType.BANGLE
            text.contains("دستبند") -> GoldPieceType.BRACELET
            text.contains("گوشواره") -> GoldPieceType.EARRING
            text.contains("سرویس") || text.contains("نیم ست") -> GoldPieceType.SET
            text.contains("پلاک") || text.contains("آویز") || text.contains("اویز") -> GoldPieceType.PENDANT
            text.contains("سکه") -> GoldPieceType.COIN
            text.contains("شمش") -> GoldPieceType.BULLION_BAR
            else -> fallback
        }
    }

    private fun extractAmounts(
        text: String,
        category: GoldCategory,
        action: VoiceActionType
    ): Triple<Double, Double, Double> {
        var grams = 0.0
        var usd = 0.0
        var cashToman = 0.0

        // Match grams
        Regex("(\\d+(?:\\.\\d+)?)\\s*(?:گرم|گرمی|g\\b)").find(text)?.let {
            grams += it.groupValues[1].toDoubleOrNull() ?: 0.0
        }
        // Match soot (1 gram = 1000 soot)
        Regex("(\\d+(?:\\.\\d+)?)\\s*سوت").find(text)?.let {
            grams += (it.groupValues[1].toDoubleOrNull() ?: 0.0) / 1000.0
        }
        // Match mesghal (1 mesghal = 4.608g)
        Regex("(\\d+(?:\\.\\d+)?)\\s*مثقال").find(text)?.let {
            grams += (it.groupValues[1].toDoubleOrNull() ?: 0.0) * 4.608
        }
        // Match USD
        Regex("(\\d+(?:\\.\\d+)?)\\s*(?:دلار|\\$)").find(text)?.let {
            usd = it.groupValues[1].toDoubleOrNull() ?: 0.0
        }
        // Match Million Toman
        Regex("(\\d+(?:\\.\\d+)?)\\s*(?:میلیون|ملیون)").find(text)?.let {
            cashToman = (it.groupValues[1].toDoubleOrNull() ?: 0.0) * 1_000_000.0
        }
        // Match Thousand Toman
        if (cashToman == 0.0) {
            Regex("(\\d+(?:\\.\\d+)?)\\s*هزار").find(text)?.let {
                cashToman = (it.groupValues[1].toDoubleOrNull() ?: 0.0) * 1_000.0
            }
        }
        // Match raw Toman
        if (cashToman == 0.0) {
            Regex("(\\d+(?:\\.\\d+)?)\\s*تومان").find(text)?.let {
                cashToman = it.groupValues[1].toDoubleOrNull() ?: 0.0
            }
        }

        // Fallback: if no unit was spoken, bind first number according to category/action
        if (grams == 0.0 && usd == 0.0 && cashToman == 0.0) {
            val firstNum = Regex("(\\d+(?:\\.\\d+)?)").find(text)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            if (firstNum > 0.0) {
                when {
                    action == VoiceActionType.EXPENSE -> cashToman = firstNum
                    category == GoldCategory.USD_CURRENCY -> usd = firstNum
                    else -> grams = firstNum
                }
            }
        }

        return Triple(grams, usd, cashToman)
    }

    private fun extractKarat(text: String): Int? {
        Regex("عیار\\s*(\\d{2,4})").find(text)?.groupValues?.get(1)?.toIntOrNull()?.let { k ->
            return normalizeKaratValue(k)
        }
        Regex("(\\d{2,4})\\s*عیار").find(text)?.groupValues?.get(1)?.toIntOrNull()?.let { k ->
            return normalizeKaratValue(k)
        }
        return null
    }

    private fun normalizeKaratValue(raw: Int): Int = when (raw) {
        18 -> 750
        24 -> 999
        17 -> 705
        21 -> 875
        22 -> 916
        else -> raw.coerceIn(0, 1000)
    }

    private fun extractCounterparty(text: String): String {
        val patterns = listOf(
            Regex("(?:به|از|برای|تحویل|توسط)\\s+(?:آقای\\s+|اقای\\s+|خانم\\s+|حاج\\s+|استاد\\s+)?([\\u0600-\\u06FFa-zA-Z]+(?:\\s+[\\u0600-\\u06FFa-zA-Z]+)?)")
        )
        val stopWords = setOf(
            "مغازه", "حساب", "صندوق", "کارت", "بانک", "تومان", "ریال", "گرم", "گرمی",
            "دلار", "آبشده", "ابشده", "شمش", "طلا", "طلای", "نو", "کارکرده", "سکه",
            "امروز", "دیروز", "ساعت", "عیار", "نقد", "نقدی", "کارتخوان", "فروش", "خرید"
        )
        for (regex in patterns) {
            val matches = regex.findAll(text).toList()
            for (match in matches.asReversed()) {
                val candidate = match.groupValues[1].trim()
                val words = candidate.split(" ").filter { it.isNotBlank() && it !in stopWords }
                if (words.isNotEmpty()) {
                    return words.take(2).joinToString(" ")
                }
            }
        }
        return "ثبت نشده"
    }
}
