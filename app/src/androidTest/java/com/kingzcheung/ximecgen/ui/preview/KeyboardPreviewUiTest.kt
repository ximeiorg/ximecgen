package com.kingzcheung.ximecgen.ui.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** 键盘预览渲染：数字行模板 5 行、功能键可寻址、合并键点击回传 id。 */
class KeyboardPreviewUiTest {

    @get:Rule
    val rule = createComposeRule()

    private fun numberRowPreview(): PreviewData {
        val config = JSONObject(
            """
            {"keyboard":{"qwerty":{"layout":{"rows":[
                ["1","2","3","4","5","6","7","8","9","0"],
                [["q","w"],"e","r","t","y","u","i","o","p"],
                ["a","s","d","f","g","h","j","k","l"],
                ["shift","z","x","c","v","b","n","m","delete"],
                ["mode_change","comma","space","earth","enter"]
            ]}}}}
            """.trimIndent(),
        )
        return buildPreviewData(config, dark = false)
    }

    @Test
    fun rendersFiveRowsWithFunctionKeysAndMergedKey() {
        var clicked: String? = null
        rule.setContent {
            MaterialTheme {
                Box(Modifier.size(width = 420.dp, height = 760.dp)) {
                    KeyboardPreview(
                        preview = numberRowPreview(),
                        dark = false,
                        onKeyClick = { clicked = it },
                    )
                }
            }
        }

        // 功能键按 id 分派：图标键有 contentDescription，文本键有键面文字
        rule.onNodeWithContentDescription("Shift").assertExists()
        rule.onNodeWithContentDescription("删除").assertExists()
        rule.onNodeWithContentDescription("中英切换").assertExists()
        rule.onNodeWithText("?123").assertExists()
        rule.onNodeWithText("换行").assertExists()
        rule.onNodeWithText("，").assertExists()
        rule.onNodeWithText("空格").assertExists()

        // 每行抽样：数字行、合并键行（q 并入 QW，取 E）、字母行、控制行
        rule.onNodeWithText("1").assertExists()
        rule.onNodeWithText("E").assertExists()
        rule.onNodeWithText("A").assertExists()

        // 合并键 [q, w] 显示为 "QW"，点击回传拼接 id
        rule.onNodeWithText("QW").assertExists().performClick()
        rule.waitForIdle()
        assertEquals("qw", clicked)
    }

    @Test
    fun singleLetterKeysAreUniquePerRow() {
        rule.setContent {
            MaterialTheme {
                Box(Modifier.size(width = 420.dp, height = 760.dp)) {
                    KeyboardPreview(preview = numberRowPreview(), dark = false)
                }
            }
        }
        // 无手势配置时数字键只在数字行出现一次
        rule.onAllNodesWithText("5").assertCountEquals(1)
        rule.onAllNodesWithText("T").assertCountEquals(1)
    }
}
