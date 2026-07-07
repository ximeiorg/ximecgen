use crate::model::*;
use serde::{Deserialize, Serialize};
use serde_json::Value;

#[derive(Debug, thiserror::Error)]
pub enum ConfigError {
    #[error("YAML error: {0}")]
    YamlParse(String),

    #[error("Validation error: {0}")]
    Validation(String),
}

pub fn parse_yaml(yaml_str: &str) -> Result<XimeConfig, ConfigError> {
    let config: XimeConfig =
        serde_saphyr::from_str(yaml_str).map_err(|e| ConfigError::YamlParse(e.to_string()))?;
    Ok(config)
}

pub fn to_yaml(config: &XimeConfig) -> Result<String, ConfigError> {
    let yaml_str =
        serde_saphyr::to_string(config).map_err(|e| ConfigError::YamlParse(e.to_string()))?;
    Ok(yaml_str)
}

pub fn to_yaml_pretty(config: &XimeConfig) -> Result<String, ConfigError> {
    let mut yaml_str =
        serde_saphyr::to_string(config).map_err(|e| ConfigError::YamlParse(e.to_string()))?;
    if yaml_str.ends_with('\n') {
        yaml_str.pop();
    }
    yaml_str.push('\n');
    Ok(yaml_str)
}

pub fn validate(yaml_str: &str) -> ValidationResult {
    let mut errors = Vec::new();
    let mut warnings = Vec::new();

    match serde_saphyr::from_str::<Value>(yaml_str) {
        Ok(root) => {
            if !root.is_object() {
                errors.push(ValidationError {
                    path: "".into(),
                    message: "Root must be a mapping".into(),
                    severity: "error".into(),
                });
                return ValidationResult {
                    valid: false,
                    errors,
                    warnings,
                };
            }

            if !root.get("metadata").is_some() {
                errors.push(ValidationError {
                    path: "metadata".into(),
                    message: "Missing required field: metadata".into(),
                    severity: "error".into(),
                });
            }

            if let Some(style) = root.get("style") {
                if style.get("color_scheme").and_then(|v| v.as_str()).is_none() {
                    warnings.push("style.color_scheme should be a string".into());
                }
            }

            if let Some(keyboard) = root.get("keyboard") {
                if let Some(colors) = keyboard.get("colors") {
                    if let Some(map) = colors.as_object() {
                        for (k, v) in map {
                            if k.contains("color") {
                                if v.as_u64().is_none() && v.as_str().is_none() {
                                    warnings.push(format!(
                                        "keyboard.colors.{}: expected integer or string color value",
                                        k
                                    ));
                                }
                            }
                        }
                    }
                }

                if keyboard.get("qwerty").is_none() && keyboard.get("qwerty_en").is_none() {
                    warnings.push("keyboard has no qwerty or qwerty_en layout defined".into());
                }
            }

            match serde_saphyr::from_str::<XimeConfig>(yaml_str) {
                Ok(_) => {}
                Err(e) => {
                    errors.push(ValidationError {
                        path: "".into(),
                        message: format!("Structure validation failed: {}", e),
                        severity: "error".into(),
                    });
                }
            }
        }
        Err(e) => {
            errors.push(ValidationError {
                path: "".into(),
                message: format!("Invalid YAML: {}", e),
                severity: "error".into(),
            });
        }
    }

    ValidationResult {
        valid: errors.is_empty(),
        errors,
        warnings,
    }
}

pub fn get_field_descriptors() -> Vec<FieldDescriptor> {
    vec![
        FieldDescriptor::new("metadata.app_name", "App Name", FieldType::Text, "Application display name", "Xime")
            .with_validation(ValidationRule::NonEmpty),
        FieldDescriptor::new("metadata.app_version", "App Version", FieldType::Text, "Minimum supported app version", ">=2.5.0")
            .with_validation(ValidationRule::Version),
        FieldDescriptor::new("metadata.platform", "Platform", FieldType::Select, "Target platform", "android")
            .with_options(vec!["android".into(), "ios".into(), "web".into()])
            .with_validation(ValidationRule::NonEmpty),
        FieldDescriptor::new("style.color_scheme", "Color Scheme", FieldType::Select, "Active color scheme name", "lavender_purple")
            .with_options(vec![
                "lavender_purple".into(), "ocean_blue".into(), "forest_green".into(),
                "sunset_orange".into(), "coral_red".into(), "slate_gray".into(),
                "rose_pink".into(), "teal_cyan".into(),
            ])
            .with_validation(ValidationRule::NonEmpty),
        FieldDescriptor::new("keyboard.key.corner_radius", "Key Corner Radius", FieldType::Number, "Key corner radius in dp", "8")
            .with_validation(ValidationRule::RangeInt(0, 32)),
        FieldDescriptor::new("keyboard.shadow.enabled", "Shadow Enabled", FieldType::Boolean, "Enable key shadow", "true"),
        FieldDescriptor::new("keyboard.shadow.elevation", "Shadow Elevation", FieldType::Number, "Shadow height in dp", "1")
            .with_validation(ValidationRule::RangeInt(0, 24)),
        FieldDescriptor::new("keyboard.colors.keyboard_bg_color", "Keyboard BG Color (Light)", FieldType::Color, "Keyboard background color in light mode", "0xE3E4E8")
            .with_validation(ValidationRule::Color),
        FieldDescriptor::new("keyboard.colors.keyboard_bg_color_dark", "Keyboard BG Color (Dark)", FieldType::Color, "Keyboard background color in dark mode", "0x202020")
            .with_validation(ValidationRule::Color),
        FieldDescriptor::new("keyboard.colors.key_bg_color", "Key BG Color (Light)", FieldType::Color, "Key background color in light mode", "0xFFFFFF")
            .with_validation(ValidationRule::Color),
        FieldDescriptor::new("keyboard.colors.key_bg_color_dark", "Key BG Color (Dark)", FieldType::Color, "Key background color in dark mode", "0x4A4A4A")
            .with_validation(ValidationRule::Color),
        FieldDescriptor::new("keyboard.colors.candidate_bar_bg_color", "Candidate Bar BG Color (Light)", FieldType::Color, "Candidate bar background in light mode", "0xE3E4E8")
            .with_validation(ValidationRule::Color),
        FieldDescriptor::new("keyboard.colors.candidate_bar_bg_color_dark", "Candidate Bar BG Color (Dark)", FieldType::Color, "Candidate bar background in dark mode", "0x202020")
            .with_validation(ValidationRule::Color),
        FieldDescriptor::new("keyboard.colors.key_text_color", "Key Text Color (Light)", FieldType::Color, "Key text color in light mode", "0x202124")
            .with_validation(ValidationRule::Color),
        FieldDescriptor::new("keyboard.colors.key_text_color_dark", "Key Text Color (Dark)", FieldType::Color, "Key text color in dark mode", "0xE8EAED")
            .with_validation(ValidationRule::Color),
        FieldDescriptor::new("keyboard.colors.candidate_text_color", "Candidate Text Color (Light)", FieldType::Color, "Candidate text color in light mode", "0x202124")
            .with_validation(ValidationRule::Color),
        FieldDescriptor::new("keyboard.colors.candidate_text_color_dark", "Candidate Text Color (Dark)", FieldType::Color, "Candidate text color in dark mode", "0xE8EAED")
            .with_validation(ValidationRule::Color),
        FieldDescriptor::new("keyboard.qwerty.button_layout", "QWERTY Button Layout", FieldType::Select, "QWERTY layout mode", "standard")
            .with_options(vec!["standard".into(), "compact".into()])
            .with_validation(ValidationRule::NonEmpty),
        FieldDescriptor::new("keyboard.qwerty_en.button_layout", "QWERTY EN Button Layout", FieldType::Select, "English QWERTY layout mode", "standard")
            .with_options(vec!["standard".into(), "compact".into()])
            .with_validation(ValidationRule::NonEmpty),
    ]
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum ValidationRule {
    NonEmpty,
    Version,
    Color,
    RangeInt(i64, i64),
    Date,
    SingleChar,
    MaxLength(usize),
    MaxItems(usize),
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct FieldDescriptor {
    pub path: String,
    pub label: String,
    #[serde(rename = "fieldType")]
    pub field_type: FieldType,
    pub description: String,
    pub default: String,
    #[serde(default)]
    pub options: Option<Vec<String>>,
    #[serde(default, rename = "validation")]
    pub validation: Option<ValidationRule>,
}

impl FieldDescriptor {
    pub fn new(
        path: &str,
        label: &str,
        field_type: FieldType,
        description: &str,
        default: &str,
    ) -> Self {
        Self {
            path: path.into(),
            label: label.into(),
            field_type,
            description: description.into(),
            default: default.into(),
            options: None,
            validation: None,
        }
    }

    pub fn with_options(mut self, options: Vec<String>) -> Self {
        self.options = Some(options);
        self
    }

    pub fn with_validation(mut self, rule: ValidationRule) -> Self {
        self.validation = Some(rule);
        self
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub enum FieldType {
    #[default]
    Text,
    Number,
    Boolean,
    Select,
    Color,
    KeyBinding,
}

pub fn validate_field_value(path: &str, value: &str) -> Option<ValidationError> {
    let descriptors = get_field_descriptors();
    let desc = descriptors.iter().find(|d| d.path == path)?;
    let rule = desc.validation.as_ref()?;

    let msg = match rule {
        ValidationRule::NonEmpty => {
            if value.trim().is_empty() {
                Some(format!("{} must not be empty", desc.label))
            } else {
                None
            }
        }
        ValidationRule::Version => {
            if value.trim().is_empty() {
                Some(format!("{} must not be empty", desc.label))
            } else if !value.contains(|c: char| c.is_ascii_digit()) {
                Some(format!("{} must contain a version number", desc.label))
            } else {
                None
            }
        }
        ValidationRule::Color => {
            let trimmed = value.trim();
            if trimmed.is_empty() {
                None
            } else if let Some(hex) = trimmed.strip_prefix("0x").or_else(|| trimmed.strip_prefix("0X")) {
                if hex.len() > 6 || hex.is_empty() {
                    Some(format!("{}: invalid hex color '{}'", desc.label, value))
                } else if u64::from_str_radix(hex, 16).is_err() {
                    Some(format!("{}: invalid hex color '{}'", desc.label, value))
                } else {
                    None
                }
            } else if let Some(hex) = trimmed.strip_prefix('#') {
                if hex.len() > 6 || hex.is_empty() || u64::from_str_radix(hex, 16).is_err() {
                    Some(format!("{}: invalid color format '{}'", desc.label, value))
                } else {
                    None
                }
            } else if trimmed.parse::<u64>().is_ok() {
                None
            } else {
                Some(format!("{}: '{}' should be 0xRRGGBB or #RRGGBB", desc.label, value))
            }
        }
        ValidationRule::RangeInt(min, max) => {
            match value.parse::<i64>() {
                Ok(n) if n < *min => Some(format!("{} must be >= {}, got {}", desc.label, min, n)),
                Ok(n) if n > *max => Some(format!("{} must be <= {}, got {}", desc.label, max, n)),
                Ok(_) => None,
                Err(_) => Some(format!("{} must be a number", desc.label)),
            }
        }
        ValidationRule::Date => {
            let trimmed = value.trim();
            if trimmed.is_empty() {
                None
            } else if !trimmed.chars().all(|c| c.is_ascii_digit() || c == '-') || trimmed.len() != 10 {
                Some(format!("{} must be YYYY-MM-DD format", desc.label))
            } else {
                None
            }
        }
        ValidationRule::SingleChar => {
            let trimmed = value.trim();
            if trimmed.is_empty() {
                None
            } else if trimmed.chars().count() != 1 {
                Some(format!("{} must be a single character", desc.label))
            } else {
                None
            }
        }
        ValidationRule::MaxLength(max) => {
            let len = value.chars().count();
            if len > *max {
                Some(format!("{} must be at most {} characters, got {}", desc.label, max, len))
            } else {
                None
            }
        }
        ValidationRule::MaxItems(max) => {
            let count = value.matches(',').count() + 1;
            if count > *max {
                Some(format!("{} must have at most {} items, got {}", desc.label, max, count))
            } else {
                None
            }
        }
    };

    msg.map(|m| ValidationError {
        path: path.into(),
        message: m,
        severity: "error".into(),
    })
}

pub fn validate_all_fields(config: &XimeConfig) -> Vec<ValidationError> {
    let descriptors = get_field_descriptors();
    let mut errors = Vec::new();

    for desc in &descriptors {
        let value = get_field_value(config, &desc.path);
        if let Some(err) = validate_field_value(&desc.path, &value) {
            errors.push(err);
        }
    }

    errors
}

fn get_field_value(config: &XimeConfig, path: &str) -> String {
    match path {
        "metadata.app_name" => config.metadata.app_name.clone(),
        "metadata.app_version" => config.metadata.app_version.clone(),
        "metadata.platform" => config.metadata.platform.clone(),
        "style.color_scheme" => config.style.as_ref().map(|s| s.color_scheme.clone()).unwrap_or_default(),
        "keyboard.key.corner_radius" => config.keyboard.as_ref()
            .and_then(|k| k.key.as_ref())
            .and_then(|k| k.corner_radius)
            .map(|v| v.to_string())
            .unwrap_or_default(),
        "keyboard.shadow.enabled" => config.keyboard.as_ref()
            .and_then(|k| k.shadow.as_ref())
            .and_then(|s| s.enabled)
            .map(|v| v.to_string())
            .unwrap_or_default(),
        "keyboard.shadow.elevation" => config.keyboard.as_ref()
            .and_then(|k| k.shadow.as_ref())
            .and_then(|s| s.elevation)
            .map(|v| v.to_string())
            .unwrap_or_default(),
        "keyboard.qwerty.button_layout" => config.keyboard.as_ref()
            .and_then(|k| k.qwerty.as_ref())
            .and_then(|q| q.button_layout.as_ref())
            .map(|b| format!("{:?}", b).to_lowercase())
            .unwrap_or_default(),
        "keyboard.qwerty_en.button_layout" => config.keyboard.as_ref()
            .and_then(|k| k.qwerty_en.as_ref())
            .and_then(|q| q.button_layout.as_ref())
            .map(|b| format!("{:?}", b).to_lowercase())
            .unwrap_or_default(),
        _ => {
            if path.starts_with("keyboard.colors.") {
                let color_key = path.strip_prefix("keyboard.colors.").unwrap_or("");
                let colors = config.keyboard.as_ref().and_then(|k| k.colors.as_ref());
                match color_key {
                    "keyboard_bg_color" => fmt_opt_value(colors.and_then(|c| c.keyboard_bg_color.as_ref())),
                    "keyboard_bg_color_dark" => fmt_opt_value(colors.and_then(|c| c.keyboard_bg_color_dark.as_ref())),
                    "key_bg_color" => fmt_opt_value(colors.and_then(|c| c.key_bg_color.as_ref())),
                    "key_bg_color_dark" => fmt_opt_value(colors.and_then(|c| c.key_bg_color_dark.as_ref())),
                    "candidate_bar_bg_color" => fmt_opt_value(colors.and_then(|c| c.candidate_bar_bg_color.as_ref())),
                    "candidate_bar_bg_color_dark" => fmt_opt_value(colors.and_then(|c| c.candidate_bar_bg_color_dark.as_ref())),
                    "key_text_color" => fmt_opt_value(colors.and_then(|c| c.key_text_color.as_ref())),
                    "key_text_color_dark" => fmt_opt_value(colors.and_then(|c| c.key_text_color_dark.as_ref())),
                    "candidate_text_color" => fmt_opt_value(colors.and_then(|c| c.candidate_text_color.as_ref())),
                    "candidate_text_color_dark" => fmt_opt_value(colors.and_then(|c| c.candidate_text_color_dark.as_ref())),
                    _ => String::new(),
                }
            } else {
                String::new()
            }
        }
    }
}

fn fmt_opt_value(v: Option<&Value>) -> String {
    match v {
        Some(Value::Number(n)) => {
            if let Some(u) = n.as_u64() {
                format!("0x{:06X}", u)
            } else {
                n.to_string()
            }
        }
        Some(Value::String(s)) => s.clone(),
        Some(other) => other.to_string(),
        None => String::new(),
    }
}

pub fn parse_color_value(value: &str) -> Option<Value> {
    let trimmed = value.trim();
    if trimmed.is_empty() {
        return None;
    }
    if let Some(hex) = trimmed
        .strip_prefix("0x")
        .or_else(|| trimmed.strip_prefix("0X"))
    {
        if let Ok(val) = u64::from_str_radix(hex, 16) {
            return Some(Value::Number(serde_json::Number::from(val)));
        }
    }
    if let Some(hex) = trimmed.strip_prefix('#') {
        if let Ok(val) = u64::from_str_radix(hex, 16) {
            return Some(Value::Number(serde_json::Number::from(val)));
        }
    }
    if let Ok(num) = trimmed.parse::<u64>() {
        return Some(Value::Number(serde_json::Number::from(num)));
    }
    Some(Value::String(trimmed.into()))
}

fn parse_button_layout(value: &str) -> Option<crate::model::ButtonLayout> {
    match value.trim().to_lowercase().as_str() {
        "standard" => Some(crate::model::ButtonLayout::Standard),
        "compact" => Some(crate::model::ButtonLayout::Compact),
        _ => None,
    }
}

pub fn update_field_by_path(config: &mut XimeConfig, path: &str, value: &str) {
    match path {
        "metadata.app_name" => config.metadata.app_name = value.into(),
        "metadata.app_version" => config.metadata.app_version = value.into(),
        "metadata.platform" => config.metadata.platform = value.into(),
        "style.color_scheme" => {
            config.style.get_or_insert_default().color_scheme = value.into();
        }
        "keyboard.key.corner_radius" => {
            let kb = config.keyboard.get_or_insert_default();
            kb.key
                .get_or_insert(KeySettings {
                    corner_radius: None,
                })
                .corner_radius = value.parse().ok();
        }
        "keyboard.shadow.enabled" => {
            let kb = config.keyboard.get_or_insert_default();
            kb.shadow
                .get_or_insert(ShadowSettings {
                    enabled: None,
                    elevation: None,
                })
                .enabled = Some(value == "true");
        }
        "keyboard.shadow.elevation" => {
            let kb = config.keyboard.get_or_insert_default();
            kb.shadow
                .get_or_insert(ShadowSettings {
                    enabled: None,
                    elevation: None,
                })
                .elevation = value.parse().ok();
        }
        "keyboard.qwerty.button_layout" => {
            let kb = config.keyboard.get_or_insert_default();
            kb.qwerty.get_or_insert_default().button_layout = parse_button_layout(value);
        }
        "keyboard.qwerty_en.button_layout" => {
            let kb = config.keyboard.get_or_insert_default();
            kb.qwerty_en.get_or_insert_default().button_layout = parse_button_layout(value);
        }
        _ => {
            if path.starts_with("keyboard.colors.") {
                let color_key = path.strip_prefix("keyboard.colors.").unwrap_or("");
                let kb = config.keyboard.get_or_insert_default();
                let colors = kb.colors.get_or_insert_default();
                let color_val = parse_color_value(value);
                match color_key {
                    "keyboard_bg_color" => colors.keyboard_bg_color = color_val,
                    "keyboard_bg_color_dark" => colors.keyboard_bg_color_dark = color_val,
                    "key_bg_color" => colors.key_bg_color = color_val,
                    "key_bg_color_dark" => colors.key_bg_color_dark = color_val,
                    "special_key_bg_color" => colors.special_key_bg_color = color_val,
                    "special_key_bg_color_dark" => colors.special_key_bg_color_dark = color_val,
                    "candidate_bar_bg_color" => colors.candidate_bar_bg_color = color_val,
                    "candidate_bar_bg_color_dark" => colors.candidate_bar_bg_color_dark = color_val,
                    "key_text_color" => colors.key_text_color = color_val,
                    "key_text_color_dark" => colors.key_text_color_dark = color_val,
                    "candidate_text_color" => colors.candidate_text_color = color_val,
                    "candidate_text_color_dark" => colors.candidate_text_color_dark = color_val,
                    _ => {}
                }
            }
        }
    }
}

impl std::fmt::Display for FieldType {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            FieldType::Text => write!(f, "text"),
            FieldType::Number => write!(f, "number"),
            FieldType::Boolean => write!(f, "boolean"),
            FieldType::Select => write!(f, "select"),
            FieldType::Color => write!(f, "color"),
            FieldType::KeyBinding => write!(f, "key_binding"),
        }
    }
}
