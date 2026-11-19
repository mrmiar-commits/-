package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.model.AccountType
import com.example.model.ShopAccount
import com.example.ui.components.ZarnegarEmptyState
import com.example.ui.theme.ZarnegarTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("زرنگار", appName)
    }

    @Test
    fun `account gold gram equivalent calculates personal card and petty cash accurately`() {
        val personalCard = ShopAccount(
            name = "کارت شخصی",
            accountType = AccountType.PERSONAL_CARD.name,
            balanceToman = 14_000_000.0,
            balanceUsd = 100.0,
            goldGrams18k = 1.0
        )
        // 14,000,000 / 7,000,000 = 2.0g + (100 * 70,000 / 7,000,000 = 1.0g) + 1.0g direct = 4.0g
        val equiv = personalCard.goldEquivalent18kGrams(gold18kGramPriceToman = 7_000_000.0, usdTomanPrice = 70_000.0)
        assertEquals(4.0, equiv, 0.001)
    }

    @Test
    fun `minimal light and dark themes render components without crash`() {
        composeRule.setContent {
            ZarnegarTheme(darkTheme = true) {
                ZarnegarEmptyState(
                    title = "طلافروشی زرنگار",
                    subtitle = "آماده ثبت معاملات روزانه"
                )
            }
        }
        composeRule.onNodeWithText("طلافروشی زرنگار").assertIsDisplayed()
        composeRule.onNodeWithText("آماده ثبت معاملات روزانه").assertIsDisplayed()
    }
}
