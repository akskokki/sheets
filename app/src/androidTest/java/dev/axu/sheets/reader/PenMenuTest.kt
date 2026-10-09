package dev.axu.sheets.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The pen button and menu over a page that turns on taps, stacked the way the reader stacks them,
 * with plain boxes for the menu's choices.
 */
@RunWith(AndroidJUnit4::class)
class PenMenuTest {
    @get:Rule
    val rule = createComposeRule()

    private val menu = PenMenuState()
    private val taps = mutableListOf<String>()
    private val picked = mutableListOf<String>()
    private val choices = listOf("a", "b", "c")

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
                Box(Modifier.size(ButtonSize).testTag("button").opensPenMenu(menu))
                if (menu.isOpen) {
                    Column(Modifier.offset(y = ChoicesTop)) {
                        for (choice in choices) {
                            Box(Modifier.size(ChoiceSize).penMenuChoice(menu, choice) { picked += choice })
                        }
                    }
                }
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

    @Test
    fun tappingThePenButtonOpensTheMenuAndTappingAgainClosesIt() {
        rule.onNodeWithTag("button").performTouchInput { click() }
        rule.runOnIdle { assertTrue(menu.isOpen) }
        rule.onNodeWithTag("button").performTouchInput { click() }
        rule.runOnIdle { assertFalse(menu.isOpen) }
    }

    @Test
    fun draggingFromThePenButtonPicksTheChoiceLiftedOverAndLeavesTheMenuOpen() {
        // The menu opens on press, so its choices are laid out by the time the pen moves.
        rule.onNodeWithTag("button").performTouchInput { down(center) }
        rule.onNodeWithTag("button").performTouchInput {
            moveTo(Offset(center.x, (ChoicesTop + ChoiceSize * 1.5f).toPx()))
            up()
        }
        rule.runOnIdle {
            assertEquals(listOf("b"), picked)
            assertTrue(menu.isOpen)
            assertEquals(null, menu.highlighted)
        }
    }

    @Test
    fun liftingAwayFromTheChoicesPicksNothingAndLeavesTheMenuOpen() {
        rule.onNodeWithTag("button").performTouchInput { down(center) }
        rule.onNodeWithTag("button").performTouchInput {
            moveTo(Offset((ButtonSize * 4).toPx(), center.y))
            up()
        }
        rule.runOnIdle {
            assertEquals(emptyList<String>(), picked)
            assertTrue(menu.isOpen)
        }
    }

    private companion object {
        val ButtonSize = 50.dp
        val ChoiceSize = 50.dp
        val ChoicesTop = 100.dp
    }
}
