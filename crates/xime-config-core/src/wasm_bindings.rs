use wasm_bindgen::prelude::*;

use crate::model::*;
use crate::parser;
use crate::validator;

#[wasm_bindgen]
pub fn parse(yaml_str: &str) -> String {
    match parser::parse_yaml(yaml_str) {
        Ok(config) => serde_json::to_string(&config).unwrap_or_else(|_| r#"{"error":"serialize"}"#.into()),
        Err(e) => format!(r#"{{"error":"{}"}}"#, e),
    }
}

#[wasm_bindgen]
pub fn to_yaml(json_str: &str) -> String {
    let config: XimeConfig = match serde_json::from_str(json_str) {
        Ok(c) => c,
        Err(_) => return String::new(),
    };
    parser::to_yaml(&config).unwrap_or_default()
}

#[wasm_bindgen]
pub fn validate(yaml_str: &str) -> String {
    let result = validator::validate_yaml_file(yaml_str);
    serde_json::to_string(&result).unwrap_or_default()
}

#[wasm_bindgen]
pub fn get_field_descriptors() -> String {
    let fields = parser::get_field_descriptors();
    serde_json::to_string(&fields).unwrap_or_else(|_| "[]".into())
}

#[wasm_bindgen]
pub fn update_field(json_str: &str, field_path: &str, new_value: &str) -> String {
    let mut config: XimeConfig = match serde_json::from_str(json_str) {
        Ok(c) => c,
        Err(_) => return json_str.to_string(),
    };

    parser::update_field_by_path(&mut config, field_path, new_value);

    serde_json::to_string(&config).unwrap_or_else(|_| json_str.to_string())
}
