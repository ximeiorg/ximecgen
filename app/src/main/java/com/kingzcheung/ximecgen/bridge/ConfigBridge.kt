package com.kingzcheung.ximecgen.bridge

import org.json.JSONArray
import org.json.JSONObject

object ConfigBridge {
    private var nativeLoaded = false

    init {
        try {
            System.loadLibrary("xime_config_core")
            nativeLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            nativeLoaded = false
        }
    }

    fun isNativeAvailable(): Boolean = nativeLoaded

    fun parse(yaml: String): JSONObject? {
        return if (nativeLoaded) {
            val json = nativeParse(yaml)
            if (json.isNotEmpty()) JSONObject(json) else null
        } else {
            fallbackParse(yaml)
        }
    }

    fun toYaml(configJson: String): String {
        return if (nativeLoaded) {
            nativeToYaml(configJson)
        } else {
            fallbackToYaml(configJson)
        }
    }

    fun validate(yaml: String): JSONObject {
        return if (nativeLoaded) {
            val result = nativeValidate(yaml)
            if (result.isNotEmpty()) JSONObject(result) else JSONObject()
        } else {
            JSONObject().apply {
                put("valid", true)
                put("errors", JSONArray())
                put("warnings", JSONArray())
            }
        }
    }

    fun getFieldDescriptors(): JSONArray {
        return if (nativeLoaded) {
            val json = nativeGetFieldDescriptors()
            if (json.isNotEmpty()) JSONArray(json) else JSONArray()
        } else {
            fallbackFieldDescriptors()
        }
    }

    fun updateField(configJson: String, fieldPath: String, newValue: String): String {
        return if (nativeLoaded) {
            nativeUpdateField(configJson, fieldPath, newValue)
        } else {
            fallbackUpdateField(configJson, fieldPath, newValue)
        }
    }

    // JNI native declarations
    private external fun nativeParse(yamlStr: String): String
    private external fun nativeToYaml(jsonStr: String): String
    private external fun nativeValidate(yamlStr: String): String
    private external fun nativeGetFieldDescriptors(): String
    private external fun nativeUpdateField(jsonStr: String, fieldPath: String, newValue: String): String

    // Fallback: pure Kotlin YAML parsing (basic)
    private fun fallbackParse(yaml: String): JSONObject? {
        return try {
            val lines = yaml.lines()
            val obj = JSONObject()
            var currentKey = ""
            var indent = 0
            val stack = mutableListOf<JSONObject>()

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

                val currentIndent = line.length - trimmed.length
                while (stack.size > 0 && currentIndent <= indent && stack.size > 1) {
                    stack.removeAt(stack.lastIndex)
                }
                indent = currentIndent

                if (trimmed.endsWith(":") || trimmed.endsWith(": ")) {
                    val key = trimmed.removeSuffix(":").removeSuffix(": ").trim()
                    if (stack.isEmpty()) {
                        val newObj = JSONObject()
                        obj.put(key, newObj)
                        stack.add(obj)
                        stack.add(newObj)
                    } else {
                        val parent = stack.last()
                        val newObj = JSONObject()
                        parent.put(key, newObj)
                        stack.add(newObj)
                    }
                    currentKey = key
                } else if (trimmed.contains(":")) {
                    val colonIdx = trimmed.indexOf(":")
                    val key = trimmed.substring(0, colonIdx).trim()
                    val value = trimmed.substring(colonIdx + 1).trim().removeSurrounding("\"")
                    val parent = if (stack.isNotEmpty()) stack.last() else obj
                    parent.put(key, value)
                }
            }

            obj
        } catch (e: Exception) {
            null
        }
    }

    private fun fallbackFieldDescriptors(): JSONArray {
        val arr = JSONArray()
        arr.put(JSONObject().apply {
            put("path", "metadata.app_name")
            put("label", "App Name")
            put("fieldType", "text")
            put("description", "Application display name")
            put("default", "Xime")
        })
        arr.put(JSONObject().apply {
            put("path", "metadata.app_version")
            put("label", "App Version")
            put("fieldType", "text")
            put("description", "Minimum supported app version")
            put("default", ">=2.5.0")
        })
        arr.put(JSONObject().apply {
            put("path", "metadata.platform")
            put("label", "Platform")
            put("fieldType", "select")
            put("description", "Target platform")
            put("default", "android")
            put("options", JSONArray(listOf("android", "ios", "web")))
        })
        arr.put(JSONObject().apply {
            put("path", "style.color_scheme")
            put("label", "Color Scheme")
            put("fieldType", "select")
            put("description", "Active color scheme name")
            put("default", "lavender_purple")
            put("options", JSONArray(listOf(
                "lavender_purple", "ocean_blue", "forest_green",
                "sunset_orange", "coral_red", "slate_gray",
                "rose_pink", "teal_cyan"
            )))
        })
        arr.put(JSONObject().apply {
            put("path", "keyboard.key.corner_radius")
            put("label", "Key Corner Radius")
            put("fieldType", "number")
            put("description", "Key corner radius in dp")
            put("default", "8")
        })
        arr.put(JSONObject().apply {
            put("path", "keyboard.shadow.enabled")
            put("label", "Shadow Enabled")
            put("fieldType", "boolean")
            put("description", "Enable key shadow")
            put("default", "true")
        })
        arr.put(JSONObject().apply {
            put("path", "keyboard.colors.keyboard_bg_color")
            put("label", "Keyboard BG (Light)")
            put("fieldType", "color")
            put("description", "Keyboard background in light mode")
            put("default", "0xE3E4E8")
        })
        arr.put(JSONObject().apply {
            put("path", "keyboard.colors.keyboard_bg_color_dark")
            put("label", "Keyboard BG (Dark)")
            put("fieldType", "color")
            put("description", "Keyboard background in dark mode")
            put("default", "0x202020")
        })
        return arr
    }

    // Fallback: convert JSON object to YAML string
    private fun fallbackToYaml(configJson: String): String {
        return try {
            val obj = JSONObject(configJson)
            val sb = StringBuilder()
            jsonToYaml(obj, 0, sb)
            sb.toString()
        } catch (_: Exception) {
            ""
        }
    }

    private fun jsonToYaml(obj: JSONObject, indent: Int, sb: StringBuilder) {
        val prefix = "  ".repeat(indent)
        val keys = obj.keys().asSequence().toList()
        for (key in keys) {
            val value = obj.get(key)
            sb.append(prefix).append(key).append(": ")
            when (value) {
                is JSONObject -> {
                    sb.append('\n')
                    jsonToYaml(value, indent + 1, sb)
                }
                is JSONArray -> {
                    sb.append('\n')
                    for (i in 0 until value.length()) {
                        sb.append(prefix).append("  - ").append(value.get(i)).append('\n')
                    }
                }
                is Boolean -> sb.append(if (value) "true" else "false").append('\n')
                is Number -> {
                    val str = value.toString()
                    if (str.contains('.') || str.contains('e') || str.contains('E')) {
                        sb.append(str).append('\n')
                    } else {
                        sb.append(value.toLong()).append('\n')
                    }
                }
                is String -> {
                    if (value.startsWith("0x") || value.startsWith(">=") || value.contains(':') || value.contains('#') || value.isEmpty()) {
                        sb.append('"').append(value).append('"').append('\n')
                    } else {
                        sb.append(value).append('\n')
                    }
                }
                else -> sb.append(value).append('\n')
            }
        }
    }

    // Fallback: update a field in the JSON by navigating dot-separated path
    private fun fallbackUpdateField(configJson: String, fieldPath: String, newValue: String): String {
        return try {
            val obj = JSONObject(configJson)
            val parts = fieldPath.split(".")
            var current: Any = obj
            for (i in 0 until parts.size - 1) {
                val part = parts[i]
                current = if (current is JSONObject) {
                    if (current.has(part)) {
                        current.get(part)
                    } else {
                        val nested = JSONObject()
                        current.put(part, nested)
                        nested
                    }
                } else {
                    return configJson
                }
            }
            val lastPart = parts.last()
            if (current is JSONObject) {
                val parsed: Any = when {
                    newValue == "true" -> true
                    newValue == "false" -> false
                    newValue.toIntOrNull() != null -> newValue.toInt()
                    newValue.toLongOrNull() != null -> newValue.toLong()
                    newValue.toDoubleOrNull() != null -> newValue.toDouble()
                    else -> newValue
                }
                current.put(lastPart, parsed)
            }
            obj.toString()
        } catch (_: Exception) {
            configJson
        }
    }
}
