package com.example

import com.example.model.GoldCategory
import com.example.model.VoiceActionType
import com.example.utils.VoiceMessageParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceMessageParserTest {

    @Test
    fun parsesMeltedGoldExampleAccurately() {
        val result = VoiceMessageParser.parse("1.5 گرم آبشده به رضا", timestampMillis = 1730000000000L)
        assertEquals(1.5, result.weightGrams, 0.001)
        assertEquals(GoldCategory.MELTED_GOLD, result.categoryEnum)
        assertEquals(VoiceActionType.SALE_OUT, result.actionEnum)
        assertEquals("رضا", result.counterpartyName)
        assertTrue(result.recordedDateTimeFa.isNotBlank())
    }

    @Test
    fun parsesPersianSpokenFractionsAndUsd() {
        val goldRes = VoiceMessageParser.parse("یک و نیم گرم طلای کارکرده بدون اجرت از خانم احمدی")
        assertEquals(1.5, goldRes.weightGrams, 0.001)
        assertEquals(GoldCategory.USED_NO_WAGE, goldRes.categoryEnum)
        assertEquals(VoiceActionType.PURCHASE_IN, goldRes.actionEnum)
        assertEquals("احمدی", goldRes.counterpartyName)

        val usdRes = VoiceMessageParser.parse("۵۰۰ دلار به کریم")
        assertEquals(500.0, usdRes.usdAmount, 0.001)
        assertEquals(GoldCategory.USD_CURRENCY, usdRes.categoryEnum)
        assertEquals("کریم", usdRes.counterpartyName)
    }
}
