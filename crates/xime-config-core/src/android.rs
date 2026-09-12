use jni::errors::{Result as JniResult, ThrowRuntimeExAndDefault};
use jni::objects::{JClass, JString};
use jni::sys::jstring;
use jni::EnvUnowned;

use crate::model::ApiResult;
use crate::{ops, parser, validator};

/// 输入一个 Java 字符串、输出一个 Java 字符串的通用包装：
/// 在 with_env 内完成读取→处理→new_string，JNI 异常由 ThrowRuntimeExAndDefault 兜底。
fn map_java_string<'local, F>(
    env: &mut EnvUnowned<'local>,
    input: JString<'local>,
    f: F,
) -> jstring
where
    F: FnOnce(&str) -> String,
{
    env.with_env(|e| -> JniResult<jstring> {
        let input_str = e.get_string(&input)?.to_string();
        let output = f(&input_str);
        Ok(e.new_string(output)?.into_raw())
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

/// YAML → 配置 JSON。
#[no_mangle]
pub extern "system" fn Java_com_kingzcheung_ximecgen_bridge_ConfigBridge_nativeParse<'local>(
    mut env: EnvUnowned<'local>,
    _class: JClass<'local>,
    yaml_str: JString<'local>,
) -> jstring {
    map_java_string(&mut env, yaml_str, |yaml| {
        parser::yaml_to_value(yaml)
            .map(|v| ApiResult::ok(v).to_json())
            .unwrap_or_else(|e| ApiResult::err(e.to_string()).to_json())
    })
}

/// 配置 JSON → YAML 文本。
#[no_mangle]
pub extern "system" fn Java_com_kingzcheung_ximecgen_bridge_ConfigBridge_nativeToYaml<'local>(
    mut env: EnvUnowned<'local>,
    _class: JClass<'local>,
    json_str: JString<'local>,
) -> jstring {
    map_java_string(&mut env, json_str, |json| {
        parser::json_to_yaml(json)
            .map(|yaml| ApiResult::ok(yaml.into()).to_json())
            .unwrap_or_else(|e| ApiResult::err(e.to_string()).to_json())
    })
}

/// 对配置 JSON 做结构校验。
#[no_mangle]
pub extern "system" fn Java_com_kingzcheung_ximecgen_bridge_ConfigBridge_nativeValidate<'local>(
    mut env: EnvUnowned<'local>,
    _class: JClass<'local>,
    json_str: JString<'local>,
) -> jstring {
    map_java_string(&mut env, json_str, |json| match parser::json_to_value(json) {
        Ok(value) => ApiResult::ok(serde_json::to_value(validator::validate(&value)).unwrap_or_default())
            .to_json(),
        Err(e) => ApiResult::err(e.to_string()).to_json(),
    })
}

/// 字段描述符列表（驱动通用表单渲染）。
#[no_mangle]
pub extern "system" fn Java_com_kingzcheung_ximecgen_bridge_ConfigBridge_nativeGetFieldDescriptors<'local>(
    mut env: EnvUnowned<'local>,
    _class: JClass<'local>,
) -> jstring {
    env.with_env(|e| -> JniResult<jstring> {
        let json = ApiResult::ok(serde_json::to_value(parser::get_field_descriptors()).unwrap_or_default())
            .to_json();
        Ok(e.new_string(json)?.into_raw())
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

/// 批量应用配置修改操作，返回新的配置 JSON。
#[no_mangle]
pub extern "system" fn Java_com_kingzcheung_ximecgen_bridge_ConfigBridge_nativeApplyOps<'local>(
    mut env: EnvUnowned<'local>,
    _class: JClass<'local>,
    json_str: JString<'local>,
    ops_str: JString<'local>,
) -> jstring {
    env.with_env(|e| -> JniResult<jstring> {
        let json = e.get_string(&json_str)?.to_string();
        let ops_json = e.get_string(&ops_str)?.to_string();

        let result = (|| -> Result<String, String> {
            let mut value = parser::json_to_value(&json).map_err(|e| e.to_string())?;
            let parsed = ops::parse_ops(&ops_json)?;
            ops::apply_ops(&mut value, &parsed)?;
            Ok(ApiResult::ok(value).to_json())
        })();

        let out = match result {
            Ok(s) => s,
            Err(msg) => ApiResult::err(msg).to_json(),
        };
        Ok(e.new_string(out)?.into_raw())
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}
