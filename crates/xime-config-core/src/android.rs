use jni::objects::{JClass, JString};
use jni::sys::jstring;
use jni::EnvUnowned;

use crate::parser;
use crate::validator;

fn java_string(env: &mut EnvUnowned, s: &str) -> jstring {
    env.with_env(|e| e.new_string(s))
        .map(|js| js.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_com_kingzcheung_ximecgen_bridge_ConfigBridge_nativeParse<'local>(
    mut env: EnvUnowned<'local>,
    _class: JClass<'local>,
    yaml_str: JString<'local>,
) -> jstring {
    let yaml: String = match env.with_env(|e| e.get_string(&yaml_str)) {
        Ok(s) => s.into(),
        Err(_) => {
            return java_string(&mut env, r#"{"error":"Failed to read input"}"#);
        }
    };

    match parser::parse_yaml(&yaml) {
        Ok(config) => match serde_json::to_string(&config) {
            Ok(json) => java_string(&mut env, &json),
            Err(e) => java_string(&mut env, &format!(r#"{{"error":"Serialize: {}"}}"#, e)),
        },
        Err(e) => java_string(&mut env, &format!(r#"{{"error":"Parse: {}"}}"#, e)),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_kingzcheung_ximecgen_bridge_ConfigBridge_nativeToYaml<'local>(
    mut env: EnvUnowned<'local>,
    _class: JClass<'local>,
    json_str: JString<'local>,
) -> jstring {
    let json: String = match env.with_env(|e| e.get_string(&json_str)) {
        Ok(s) => s.into(),
        Err(_) => return java_string(&mut env, ""),
    };

    let config = match serde_json::from_str(&json) {
        Ok(c) => c,
        Err(_) => return java_string(&mut env, ""),
    };

    match parser::to_yaml(&config) {
        Ok(yaml) => java_string(&mut env, &yaml),
        Err(_) => java_string(&mut env, ""),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_kingzcheung_ximecgen_bridge_ConfigBridge_nativeValidate<'local>(
    mut env: EnvUnowned<'local>,
    _class: JClass<'local>,
    yaml_str: JString<'local>,
) -> jstring {
    let yaml: String = match env.with_env(|e| e.get_string(&yaml_str)) {
        Ok(s) => s.into(),
        Err(_) => {
            return java_string(
                &mut env,
                r#"{"valid":false,"errors":[{"path":"","message":"read failed","severity":"error"}],"warnings":[]}"#,
            );
        }
    };

    let result = validator::validate_yaml_file(&yaml);
    match serde_json::to_string(&result) {
        Ok(json) => java_string(&mut env, &json),
        Err(_) => java_string(&mut env, r#"{"valid":false,"errors":[],"warnings":[]}"#),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_kingzcheung_ximecgen_bridge_ConfigBridge_nativeGetFieldDescriptors<'local>(
    mut env: EnvUnowned<'local>,
    _class: JClass<'local>,
) -> jstring {
    let fields = parser::get_field_descriptors();
    match serde_json::to_string(&fields) {
        Ok(json) => java_string(&mut env, &json),
        Err(_) => java_string(&mut env, "[]"),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_kingzcheung_ximecgen_bridge_ConfigBridge_nativeUpdateField<'local>(
    mut env: EnvUnowned<'local>,
    _class: JClass<'local>,
    json_str: JString<'local>,
    field_path: JString<'local>,
    new_value: JString<'local>,
) -> jstring {
    let json: String = match env.with_env(|e| e.get_string(&json_str)) {
        Ok(s) => s.into(),
        Err(_) => return java_string(&mut env, ""),
    };
    let path: String = match env.with_env(|e| e.get_string(&field_path)) {
        Ok(s) => s.into(),
        Err(_) => return java_string(&mut env, ""),
    };
    let value: String = match env.with_env(|e| e.get_string(&new_value)) {
        Ok(s) => s.into(),
        Err(_) => return java_string(&mut env, ""),
    };

    let mut config: crate::model::XimeConfig = match serde_json::from_str(&json) {
        Ok(c) => c,
        Err(_) => return java_string(&mut env, json.as_str()),
    };

    parser::update_field_by_path(&mut config, &path, &value);

    match serde_json::to_string(&config) {
        Ok(json) => java_string(&mut env, &json),
        Err(_) => java_string(&mut env, ""),
    }
}
