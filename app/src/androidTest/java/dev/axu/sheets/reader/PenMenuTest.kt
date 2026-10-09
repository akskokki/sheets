package dev.axu.sheets.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The pen menu over a page that turns on taps, stacked the way the reader stacks them. */
@RunWith(AndroidJUnit4::class)
class PenMenuTest {
    @get:Rule
    val rule = createComposeRule()

    private val menu = PenMenuState()
    private val taps = mutableListOf<String>()

    @Before
    fun setUp() {
        rule.setContent {
            Box(Modifier.size(400.dp)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .testTag("page")
                        .pointerInput(Unit) { detectTaps(onTap = { taps += "tap" }, onMultiFingerTap = {}) },
                )
                if (menu.isOpen) ClosePenMenuOnTouch(menu)
            }
        }
    }

    @Test
    fun aTouchOutsideTheOpenPenMenuOnlyClosesIt() {
        rule.runOnIdle { menu.open() }
        rule.onNodeWithTag("page").performTouchInput { click(centerRight) }
        rule.runOnIdle {
            assertFalse(menu.isOpen)
            assertEquals(emptyList<String>(), taps)
        }

        rule.onNodeWithTag("page").performTouchInput { click(centerRight) }
        rule.runOnIdle { assertEquals(listOf("tap"), taps) }
    }
}
