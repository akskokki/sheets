package dev.axu.sheets.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ButtonTest {
    @get:Rule
    val rule = createComposeRule()

    private var clicks = 0

    @Before
    fun setUp() {
        rule.setContent {
            Box(Modifier.size(400.dp)) {
                Button(onClick = { clicks++ }, Modifier.size(44.dp).testTag("button")) {}
            }
        }
    }

    @Test
    fun aButtonActsWhenReleased() {
        rule.onNodeWithTag("button").performTouchInput { down(center) }
        rule.runOnIdle { assertEquals(0, clicks) }
        rule.onNodeWithTag("button").performTouchInput { up() }
        rule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun slidingOffAButtonCancelsIt() {
        rule.onNodeWithTag("button").performTouchInput {
            down(center)
            moveTo(Offset(width * 4f, center.y))
            up()
        }
        rule.runOnIdle { assertEquals(0, clicks) }
    }
}
