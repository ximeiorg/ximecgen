package com.kingzcheung.ximecgen.ui.editor

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kingzcheung.ximecgen.vm.ConfigUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 预览面板：深浅切换回调与预览渲染。 */
class PreviewPaneUiTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun darkToggleFiresCallbackAndPreviewKeepsRendering() {
        val dark = mutableStateOf(false)
        var callbackValue: Boolean? = null
        rule.setContent {
            PreviewPane(
                state = ConfigUiState(configJson = "{}"),
                dark = dark.value,
                onDarkChange = {
                    dark.value = it
                    callbackValue = it
                },
                onKeyClick = {},
            )
        }

        rule.onNodeWithText("?123").assertExists()
        assertFalse(dark.value)

        rule.onNodeWithText("深色").performClick()
        rule.waitForIdle()
        assertEquals(true, callbackValue)
        assertTrue(dark.value)

        // 切换后预览仍完整渲染（深色分支不崩溃）
        rule.onNodeWithText("?123").assertExists()
        rule.onNodeWithText("浅色").performClick()
        rule.waitForIdle()
        assertEquals(false, callbackValue)
    }
}
