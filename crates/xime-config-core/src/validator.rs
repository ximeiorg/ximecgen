use crate::model::{ValidationResult, XimeConfig};
use crate::parser;

pub fn validate_config(config: &XimeConfig) -> ValidationResult {
    let field_errors = parser::validate_all_fields(config);

    let mut warnings = Vec::new();
    let errors = field_errors;

    if let Some(ref keyboard) = config.keyboard {
        if let Some(ref colors) = keyboard.colors {
            let color_fields = [
                ("keyboard.colors.keyboard_bg_color", &colors.keyboard_bg_color),
                ("keyboard.colors.keyboard_bg_color_dark", &colors.keyboard_bg_color_dark),
                ("keyboard.colors.key_bg_color", &colors.key_bg_color),
                ("keyboard.colors.key_bg_color_dark", &colors.key_bg_color_dark),
                ("keyboard.colors.special_key_bg_color", &colors.special_key_bg_color),
                ("keyboard.colors.special_key_bg_color_dark", &colors.special_key_bg_color_dark),
                ("keyboard.colors.candidate_bar_bg_color", &colors.candidate_bar_bg_color),
                ("keyboard.colors.candidate_bar_bg_color_dark", &colors.candidate_bar_bg_color_dark),
                ("keyboard.colors.key_text_color", &colors.key_text_color),
                ("keyboard.colors.key_text_color_dark", &colors.key_text_color_dark),
                ("keyboard.colors.candidate_text_color", &colors.candidate_text_color),
                ("keyboard.colors.candidate_text_color_dark", &colors.candidate_text_color_dark),
            ];

            for (path, value) in &color_fields {
                check_color_range(path, value, &mut warnings);
            }
        }

        if keyboard.qwerty.is_none() && keyboard.qwerty_en.is_none() {
            warnings.push("keyboard has no qwerty or qwerty_en layout defined".into());
        }
    }

    ValidationResult {
        valid: errors.is_empty(),
        errors,
        warnings,
    }
}

pub fn validate_yaml_file(yaml_str: &str) -> ValidationResult {
    let raw = parser::validate(yaml_str);
    if !raw.valid {
        return raw;
    }

    match parser::parse_yaml(yaml_str) {
        Ok(config) => {
            let deep = validate_config(&config);
            ValidationResult {
                valid: deep.valid && raw.valid,
                errors: [raw.errors, deep.errors].concat(),
                warnings: [raw.warnings, deep.warnings].concat(),
            }
        }
        Err(_) => raw,
    }
}

fn check_color_range(
    path: &str,
    value: &Option<serde_json::Value>,
    warnings: &mut Vec<String>,
) {
    if let Some(v) = value {
        if let Some(n) = v.as_u64() {
            if n > 0xFFFFFF {
                warnings.push(format!("{}: color value 0x{:X} exceeds 0xFFFFFF", path, n));
            }
        }
    }
}
