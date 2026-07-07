package com.kingzcheung.ximecgen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.kingzcheung.ximecgen.bridge.ConfigBridge
import com.kingzcheung.ximecgen.ui.editor.EditorScreen
import com.kingzcheung.ximecgen.ui.theme.XimecgenTheme
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Ensure ConfigBridge JNI is initialized
        ConfigBridge.isNativeAvailable()

        setContent {
            XimecgenTheme {
                EditorApp(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
fun EditorApp(modifier: Modifier = Modifier) {
    var yamlText by remember {
        mutableStateOf(
            """metadata:
  app_name: Xime
  app_version: ">=2.5.0"
  platform: android
  config_version: 1
  modified_time: "2026-07-05"

style:
  color_scheme: lavender_purple

keyboard:
  colors:
    keyboard_bg_color: 0xE3E4E8
    keyboard_bg_color_dark: 0x202020
    key_bg_color: 0xFFFFFF
    key_bg_color_dark: 0x4A4A4A
    candidate_bar_bg_color: 0xE3E4E8
    candidate_bar_bg_color_dark: 0x202020
    key_text_color: 0x202124
    key_text_color_dark: 0xE8EAED
    candidate_text_color: 0x202124
    candidate_text_color_dark: 0xE8EAED
  key:
    corner_radius: 8
  shadow:
    enabled: true
    elevation: 1
"""
        )
    }

    var configJson by remember { mutableStateOf("") }
    var validationJson by remember { mutableStateOf("{\"valid\":true,\"errors\":[],\"warnings\":[]}") }

    // Parse & validate on yaml change
    LaunchedEffect(yamlText) {
        val parsed = ConfigBridge.parse(yamlText)
        configJson = parsed?.toString() ?: "{}"

        val validation = ConfigBridge.validate(yamlText)
        validationJson = validation.toString()
    }

    val handleFieldUpdate: (String, String) -> Unit = { path, value ->
        val updated = ConfigBridge.updateField(configJson, path, value)
        if (updated.isNotEmpty() && updated != configJson) {
            val yaml = ConfigBridge.toYaml(updated)
            if (yaml.isNotEmpty()) {
                yamlText = yaml
            }
        }
    }

    val handleAddColor: (String, String) -> Unit = { key, value ->
        try {
            val obj = JSONObject(configJson)
            val keyboard = obj.optJSONObject("keyboard")
                ?: JSONObject().also { obj.put("keyboard", it) }
            val colors = keyboard.optJSONObject("colors")
                ?: JSONObject().also { keyboard.put("colors", it) }
            colors.put(key, value)
            val yaml = ConfigBridge.toYaml(obj.toString())
            if (yaml.isNotEmpty()) {
                yamlText = yaml
            }
        } catch (_: Exception) { }
    }

    val handleRemoveColor: (String) -> Unit = { key ->
        try {
            val obj = JSONObject(configJson)
            val colors = obj.optJSONObject("keyboard")?.optJSONObject("colors")
            if (colors != null) {
                colors.remove(key)
                val yaml = ConfigBridge.toYaml(obj.toString())
                if (yaml.isNotEmpty()) {
                    yamlText = yaml
                }
            }
        } catch (_: Exception) { }
    }

    EditorScreen(
        configJson = configJson,
        validationJson = validationJson,
        onFieldUpdate = handleFieldUpdate,
        onAddColor = handleAddColor,
        onRemoveColor = handleRemoveColor,
    )
}
