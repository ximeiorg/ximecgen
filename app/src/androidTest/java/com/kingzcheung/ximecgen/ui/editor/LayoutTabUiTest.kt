package com.kingzcheung.ximecgen.ui.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.kingzcheung.ximecgen.vm.ConfigViewModel
import com.kingzcheung.ximecgen.vm.Ops
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 布局与手势 tab 交互：布局模式卡片点击穿透（回归：预览键帽曾消费点击导致只有文字可点）、
 * 行内键位点击弹手势编辑 Sheet。配置经 dispatch 纯 ops 构造，不依赖网络。
 */
class LayoutTabUiTest {

    @get:Rule
    val rule = createComposeRule()

    private fun vm(): ConfigViewModel {
        val vm = ConfigViewModel()
        // 纯 ops 构造最小配置（缺失容器自动创建；无网络、无 assets 依赖）
        vm.dispatch(
            JSONArray()
                .put(Ops.set("/metadata/app_name", "Xime"))
                .put(Ops.set("/keyboard/qwerty/button_layout", "standard"))
                .put(
                    Ops.set(
                        "/keyboard/qwerty/layout/rows",
                        JSONArray()
                            .put(JSONArray().put("q").put("w").put("e"))
                            .put(JSONArray().put("a").put("s").put("d")),
                    ),
                ),
        )
        return vm
    }

    /** dispatch 异步生效：configJson 就绪前不渲染（LayoutTab 对空配置会直接崩）。 */
    private fun setContent(vm: ConfigViewModel) {
        rule.setContent {
            MaterialTheme {
                val state by vm.state.collectAsState()
                if (state.configJson.isNotEmpty()) LayoutTab(state, vm)
            }
        }
        rule.waitUntil(timeoutMillis = 5_000) { vm.state.value.configJson.isNotEmpty() }
        rule.waitForIdle()
    }

    private fun buttonLayout(vm: ConfigViewModel): String? =
        JSONObject(vm.state.value.configJson)
            .optJSONObject("keyboard")?.optJSONObject("qwerty")
            ?.optString("button_layout", "")

    @Test
    fun tappingKeycapAreaOfCompactCardSelectsCompact() {
        val vm = vm()
        setContent(vm)

        // 点"紧凑"卡片内的键帽预览（"工" 下滑提示在紧凑预览键帽上，标准模式下不存在于卡片外）
        rule.onNodeWithText("布局模式").assertExists()
        rule.onNodeWithText("工").performClick()
        rule.waitUntil(timeoutMillis = 5_000) { buttonLayout(vm) == "compact" }
        assertEquals("compact", buttonLayout(vm))
    }

    @Test
    fun tappingCardLabelSelectsStandard() {
        val vm = vm()
        setContent(vm)
        // 先切到紧凑
        rule.onNodeWithText("工").performClick()
        rule.waitUntil(timeoutMillis = 5_000) { buttonLayout(vm) == "compact" }
        // 再通过卡片文字切回标准
        rule.onNodeWithText("标准").performClick()
        rule.waitUntil(timeoutMillis = 5_000) { buttonLayout(vm) == "standard" }
    }

    @Test
    fun tappingRowKeyOpensGestureEditorSheet() {
        val vm = vm()
        setContent(vm)
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        // 迷你键盘第 1 行的 Q 键 → 手势编辑 Sheet（ModalBottomSheet 独立窗口，用 UiAutomator 断言）
        rule.onNodeWithText("Q").performClick()
        assertTrue(
            "点按键位后应弹出手势编辑 Sheet",
            device.wait(Until.hasObject(By.textContains("点按")), 3_000),
        )
    }
}
