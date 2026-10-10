package dev.axu.sheets.reader

import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ToolPressTest {
    @get:Rule
    val rule = createComposeRule()

    private var selected = false
    private var clicks = 0
    private val presses = MutableInteractionSource()
    private val shown = mutableListOf<Interaction>()

    @Before
    fun setUp() {
        rule.setContent {
            LaunchedEffect(Unit) { presses.interactions.collect { shown += it } }
            Box(Modifier.size(400.dp)) {
                Box(
                    Modifier
                        .size(44.dp)
                        .testTag("tool")
                        .toolPress(presses, isSelected = { selected }, onSelect = { selected = true }) { clicks++ },
                )
            }
        }
    }

    @Test
    fun aToolIsSelectedTheMomentItIsTouchedAndShowsNoPress() {
        rule.onNodeWithTag("tool").performTouchInput { down(center) }
        rule.runOnIdle { assertTrue(selected) }
        rule.onNodeWithTag("tool").performTouchInput { up() }
        rule.runOnIdle {
            assertEquals(0, clicks)
            assertEquals(emptyList<Interaction>(), shown)
        }
    }

    @Test
    fun pressingTheSelectedToolActsWhenReleased() {
        selected = true
        rule.onNodeWithTag("tool").performTouchInput { down(center) }
        rule.runOnIdle {
            assertEquals(0, clicks)
            assertTrue(shown.single() is PressInteraction.Press)
        }
        rule.onNodeWithTag("tool").performTouchInput { up() }
        rule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun slidingOffTheSelectedToolCancelsThePress() {
        selected = true
        rule.onNodeWithTag("tool").performTouchInput {
            down(center)
            moveTo(Offset(width * 4f, center.y))
            up()
        }
        rule.runOnIdle {
            assertEquals(0, clicks)
            assertTrue(shown.last() is PressInteraction.Cancel)
        }
        rule.onNodeWithTag("tool").performTouchInput { click() }
        rule.runOnIdle { assertEquals(1, clicks) }
    }
}
