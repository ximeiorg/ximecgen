use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::collections::HashMap;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct XimeConfig {
    #[serde(default)]
    pub metadata: Metadata,

    #[serde(default, rename = "xime_index")]
    pub xime_index: Option<XimeIndex>,

    #[serde(default)]
    pub style: Option<Style>,

    #[serde(default, rename = "color_schemes")]
    pub color_schemes: Option<HashMap<String, ColorScheme>>,

    #[serde(default)]
    pub keyboard: Option<KeyboardConfig>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Metadata {
    #[serde(default)]
    pub app_name: String,
    #[serde(default)]
    pub app_version: String,
    #[serde(default)]
    pub platform: String,
    #[serde(default, rename = "config_version")]
    pub config_version: u32,
    #[serde(default)]
    pub generator: String,
    #[serde(default, rename = "modified_time")]
    pub modified_time: String,
}

impl Default for Metadata {
    fn default() -> Self {
        Self {
            app_name: "Xime".into(),
            app_version: ">=2.5.0".into(),
            platform: "android".into(),
            config_version: 1,
            generator: String::new(),
            modified_time: String::new(),
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct XimeIndex {
    #[serde(default, rename = "base_urls")]
    pub base_urls: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct Style {
    #[serde(default, rename = "color_scheme")]
    pub color_scheme: String,
    #[serde(default, rename = "font_size")]
    pub font_size: Option<u32>,
    #[serde(default, rename = "candidate_count")]
    pub candidate_count: Option<u32>,
    #[serde(default, rename = "show_code_hint")]
    pub show_code_hint: Option<bool>,
    #[serde(default)]
    pub horizontal: Option<bool>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ColorScheme {
    pub name: String,
    #[serde(default, rename = "primary_color")]
    pub primary_color: Value,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct KeyboardConfig {
    #[serde(default)]
    pub colors: Option<KeyboardColors>,
    #[serde(default)]
    pub key: Option<KeySettings>,
    #[serde(default)]
    pub shadow: Option<ShadowSettings>,
    #[serde(default)]
    pub qwerty: Option<KeyboardLayout>,
    #[serde(default, rename = "qwerty_en")]
    pub qwerty_en: Option<KeyboardLayout>,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct KeyboardColors {
    #[serde(default, rename = "keyboard_bg_color")]
    pub keyboard_bg_color: Option<Value>,
    #[serde(default, rename = "keyboard_bg_color_dark")]
    pub keyboard_bg_color_dark: Option<Value>,
    #[serde(default, rename = "key_bg_color")]
    pub key_bg_color: Option<Value>,
    #[serde(default, rename = "key_bg_color_dark")]
    pub key_bg_color_dark: Option<Value>,
    #[serde(default, rename = "special_key_bg_color")]
    pub special_key_bg_color: Option<Value>,
    #[serde(default, rename = "special_key_bg_color_dark")]
    pub special_key_bg_color_dark: Option<Value>,
    #[serde(default, rename = "candidate_bar_bg_color")]
    pub candidate_bar_bg_color: Option<Value>,
    #[serde(default, rename = "candidate_bar_bg_color_dark")]
    pub candidate_bar_bg_color_dark: Option<Value>,
    #[serde(default, rename = "key_text_color")]
    pub key_text_color: Option<Value>,
    #[serde(default, rename = "key_text_color_dark")]
    pub key_text_color_dark: Option<Value>,
    #[serde(default, rename = "candidate_text_color")]
    pub candidate_text_color: Option<Value>,
    #[serde(default, rename = "candidate_text_color_dark")]
    pub candidate_text_color_dark: Option<Value>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct KeySettings {
    #[serde(default, rename = "corner_radius")]
    pub corner_radius: Option<u32>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ShadowSettings {
    #[serde(default)]
    pub enabled: Option<bool>,
    #[serde(default)]
    pub elevation: Option<u32>,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct KeyboardLayout {
    #[serde(default, rename = "button_layout")]
    pub button_layout: Option<ButtonLayout>,

    #[serde(default)]
    pub keys: Option<KeyMapping>,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct KeyMapping {
    #[serde(default, rename = "q")]
    pub key_q: Option<KeyBinding>,
    #[serde(default, rename = "w")]
    pub key_w: Option<KeyBinding>,
    #[serde(default, rename = "e")]
    pub key_e: Option<KeyBinding>,
    #[serde(default, rename = "r")]
    pub key_r: Option<KeyBinding>,
    #[serde(default, rename = "t")]
    pub key_t: Option<KeyBinding>,
    #[serde(default, rename = "y")]
    pub key_y: Option<KeyBinding>,
    #[serde(default, rename = "u")]
    pub key_u: Option<KeyBinding>,
    #[serde(default, rename = "i")]
    pub key_i: Option<KeyBinding>,
    #[serde(default, rename = "o")]
    pub key_o: Option<KeyBinding>,
    #[serde(default, rename = "p")]
    pub key_p: Option<KeyBinding>,

    #[serde(default, rename = "a")]
    pub key_a: Option<KeyBinding>,
    #[serde(default, rename = "s")]
    pub key_s: Option<KeyBinding>,
    #[serde(default, rename = "d")]
    pub key_d: Option<KeyBinding>,
    #[serde(default, rename = "f")]
    pub key_f: Option<KeyBinding>,
    #[serde(default, rename = "g")]
    pub key_g: Option<KeyBinding>,
    #[serde(default, rename = "h")]
    pub key_h: Option<KeyBinding>,
    #[serde(default, rename = "j")]
    pub key_j: Option<KeyBinding>,
    #[serde(default, rename = "k")]
    pub key_k: Option<KeyBinding>,
    #[serde(default, rename = "l")]
    pub key_l: Option<KeyBinding>,

    #[serde(default, rename = "z")]
    pub key_z: Option<KeyBinding>,
    #[serde(default, rename = "x")]
    pub key_x: Option<KeyBinding>,
    #[serde(default, rename = "c")]
    pub key_c: Option<KeyBinding>,
    #[serde(default, rename = "v")]
    pub key_v: Option<KeyBinding>,
    #[serde(default, rename = "b")]
    pub key_b: Option<KeyBinding>,
    #[serde(default, rename = "n")]
    pub key_n: Option<KeyBinding>,
    #[serde(default, rename = "m")]
    pub key_m: Option<KeyBinding>,

    #[serde(default, rename = "'")]
    pub key_quote: Option<KeyBinding>,
    #[serde(default, rename = "shift_l")]
    pub key_shift_l: Option<KeyBinding>,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct KeyBinding {
    #[serde(default)]
    pub tap: Option<GestureAction>,
    #[serde(default, rename = "swipe_up")]
    pub swipe_up: Option<GestureAction>,
    #[serde(default, rename = "swipe_down")]
    pub swipe_down: Option<GestureAction>,
    #[serde(default, rename = "long_press")]
    pub long_press: Option<LongPressAction>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum DisplayMode {
    Bubble,
    Key,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum ButtonLayout {
    Standard,
    Compact,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(untagged)]
pub enum GestureAction {
    Simple(String),
    Detailed {
        #[serde(default)]
        label: Option<String>,
        #[serde(default)]
        action: Option<String>,
        #[serde(default)]
        value: Option<String>,
        #[serde(default)]
        display: Option<DisplayMode>,
    },
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LongPressAction {
    #[serde(default)]
    pub display: Option<DisplayMode>,
    #[serde(default)]
    pub values: Vec<GestureAction>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ValidationResult {
    pub valid: bool,
    pub errors: Vec<ValidationError>,
    pub warnings: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ValidationError {
    pub path: String,
    pub message: String,
    pub severity: String,
}
