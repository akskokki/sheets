package dev.axu.sheets.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DetectTapsTest {
    @get:Rule
    val rule = createComposeRule()

    private val taps = mutableListOf<String>()

    @Before
    fun setUp() {
        rule.setContent {
            Box(
                Modifier
                    .size(400.dp)
                    .testTag("area")
                    .pointerInput(Unit) {
                        detectTaps(onTap = { taps += "tap" }, onMultiFingerTap = { taps += "$it fingers" })
                    },
            )
        }
    }

    @Test
    fun singleTap() {
        rule.onNodeWithTag("area").performTouchInput { click(center) }
        rule.runOnIdle { assertEquals(listOf("tap"), taps) }
    }

    @Test
    fun twoFingerTap() {
        rule.onNodeWithTag("area").performTouchInput {
            down(0, center)
            down(1, center + Offset(120f, 0f))
            advanceEventTime(80)
            up(0)
            up(1)
        }
        rule.runOnIdle { assertEquals(listOf("2 fingers"), taps) }
    }

    @Test
    fun threeFingerTap() {
        rule.onNodeWithTag("area").performTouchInput {
            down(0, center)
            down(1, center + Offset(120f, 0f))
            down(2, center + Offset(240f, 0f))
            advanceEventTime(80)
            up(0)
            up(1)
            up(2)
        }
        rule.runOnIdle { assertEquals(listOf("3 fingers"), taps) }
    }

    @Test
    fun swipeIsNotATap() {
        rule.onNodeWithTag("area").performTouchInput { swipeLeft() }
        rule.runOnIdle { assertEquals(emptyList<String>(), taps) }
    }

    @Test
    fun twoFingerPinchIsNotATap() {
        rule.onNodeWithTag("area").performTouchInput {
            pinch(center - Offset(50f, 0f), center - Offset(200f, 0f), center + Offset(50f, 0f), center + Offset(200f, 0f))
        }
        rule.runOnIdle { assertEquals(emptyList<String>(), taps) }
    }

    @Test
    fun longPressIsNotATap() {
        rule.onNodeWithTag("area").performTouchInput {
            down(center)
            advanceEventTime(800)
            up()
        }
        rule.runOnIdle { assertEquals(emptyList<String>(), taps) }
    }
}
