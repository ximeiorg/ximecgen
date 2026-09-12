use wasm_bindgen::prelude::*;

use crate::model::ApiResult;
use crate::{ops, parser, validator};

/// YAML → 配置 JSON（信封 JSON 字符串）。
#[wasm_bindgen]
pub fn parse(yaml_str: &str) -> String {
    match parser::yaml_to_value(yaml_str) {
        Ok(value) => ApiResult::ok(value).to_json(),
        Err(e) => ApiResult::err(e.to_string()).to_json(),
    }
}

/// 配置 JSON → YAML 文本。
#[wasm_bindgen]
pub fn to_yaml(json_str: &str) -> String {
    match parser::json_to_yaml(json_str) {
        Ok(yaml) => ApiResult::ok(yaml.into()).to_json(),
        Err(e) => ApiResult::err(e.to_string()).to_json(),
    }
}

/// 对配置 JSON 做结构校验。
#[wasm_bindgen]
pub fn validate(json_str: &str) -> String {
    match parser::json_to_value(json_str) {
        Ok(value) => {
            let result = validator::validate(&value);
            match serde_json::to_value(&result) {
                Ok(data) => ApiResult::ok(data).to_json(),
                Err(e) => ApiResult::err(e.to_string()).to_json(),
            }
        }
        Err(e) => ApiResult::err(e.to_string()).to_json(),
    }
}

/// 字段描述符列表。
#[wasm_bindgen]
pub fn get_field_descriptors() -> String {
    match serde_json::to_value(parser::get_field_descriptors()) {
        Ok(data) => ApiResult::ok(data).to_json(),
        Err(e) => ApiResult::err(e.to_string()).to_json(),
    }
}

/// 批量应用配置修改操作，返回新的配置 JSON。
#[wasm_bindgen]
pub fn apply_ops(json_str: &str, ops_str: &str) -> String {
    let mut value = match parser::json_to_value(json_str) {
        Ok(v) => v,
        Err(e) => return ApiResult::err(e.to_string()).to_json(),
    };
    let parsed = match ops::parse_ops(ops_str) {
        Ok(o) => o,
        Err(e) => return ApiResult::err(e).to_json(),
    };
    match ops::apply_ops(&mut value, &parsed) {
        Ok(()) => ApiResult::ok(value).to_json(),
        Err(e) => ApiResult::err(e).to_json(),
    }
}
