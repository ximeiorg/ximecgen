//! 编辑器操作流集成测试：模拟 Android/Web 编辑器的真实路径——
//! 解析 YAML → applyOps 批量修改（含 layout.rows 整体写回、合并键子数组、行内增删）→ toYaml → 回读校验。

use serde_json::{json, Value};
use xime_config_core::{ops, parser, validator};

fn sample(name: &str) -> Value {
    let path = std::path::Path::new(env!("CARGO_MANIFEST_DIR"))
        .join("tests/samples")
        .join(name);
    let yaml = std::fs::read_to_string(&path).unwrap_or_else(|e| panic!("读取 {} 失败: {}", path.display(), e));
    parser::yaml_to_value(&yaml).unwrap()
}

fn apply(config: &mut Value, ops_json: &str) {
    let parsed = ops::parse_ops(ops_json).unwrap();
    ops::apply_ops(config, &parsed).unwrap();
}

/// 无 rows 的双拼类配置（如小鹤双拼模板）：编辑器写入完整 rows 后
/// YAML 往返保持结构，且功能键行合法。
#[test]
fn set_rows_on_config_without_layout() {
    let mut config = json!({
        "metadata": {"app_name": "Xime"},
        "keyboard": {"qwerty": {"button_layout": "compact", "keys": {"q": {"tap": "q"}}}}
    });
    apply(
        &mut config,
        r#"[
            {"op":"set","path":"/keyboard/qwerty/layout/rows","value":[
                ["q","w","e","r","t","y","u","i","o","p"],
                ["a","s","d","f","g","h","j","k","l"],
                ["shift","z","x","c","v","b","n","m","delete"],
                ["mode_change","comma","space","earth","enter"]
            ]}
        ]"#,
    );
    let rows = config["keyboard"]["qwerty"]["layout"]["rows"].as_array().unwrap();
    assert_eq!(rows.len(), 4);
    // 第三行含功能键 shift/delete
    assert_eq!(rows[2][0], json!("shift"));
    assert_eq!(rows[2][8], json!("delete"));

    // YAML 往返后结构不变
    let yaml = parser::value_to_yaml(&config).unwrap();
    let reparsed: Value = parser::yaml_to_value(&yaml).unwrap();
    assert_eq!(config, reparsed);
    let r = validator::validate(&reparsed);
    assert!(r.valid, "写回 rows 后应通过校验: {:?}", r.errors);
}

/// 合并键子数组 [[q, w]] 在 ops 写回 + YAML 往返后原样保留。
#[test]
fn merged_key_subarrays_survive_ops_and_roundtrip() {
    let mut config = sample("xime.default.yaml");
    apply(
        &mut config,
        r#"[{"op":"set","path":"/keyboard/qwerty/layout/rows/0","value":[["q","w"],["e","r"],"t","y","u","i","o","p"]}]"#,
    );
    let yaml = parser::value_to_yaml(&config).unwrap();
    let back: Value = parser::yaml_to_value(&yaml).unwrap();
    let row0 = back["keyboard"]["qwerty"]["layout"]["rows"][0].as_array().unwrap();
    assert_eq!(row0[0], json!(["q", "w"]), "合并键子数组应原样保留");
    assert_eq!(row0[2], json!("t"));

    let r = validator::validate(&back);
    assert!(r.valid, "合并键行应合法: {:?}", r.errors);
}

/// 行内增删：add 追加键位、remove 移除，往返后与直接构造等价。
#[test]
fn row_add_and_remove_keys() {
    let mut config = sample("xime.default.yaml");
    apply(
        &mut config,
        r#"[
            {"op":"add","path":"/keyboard/qwerty/layout/rows/3/-","value":"voice"},
            {"op":"remove","path":"/keyboard/qwerty/layout/rows/3/0"}
        ]"#,
    );
    let yaml = parser::value_to_yaml(&config).unwrap();
    let back: Value = parser::yaml_to_value(&yaml).unwrap();
    let row3 = back["keyboard"]["qwerty"]["layout"]["rows"][3].as_array().unwrap();
    // 原行 [mode_change, comma, space, earth, enter]：移除 mode_change 后追加 voice
    assert_eq!(row3.first(), Some(&json!("comma")));
    assert_eq!(row3.last(), Some(&json!("voice")));
    assert_eq!(row3.len(), 5);
}

/// 批量编辑：一次 dispatch 修改多个字段（元数据 + 手势 + 颜色），全部生效。
#[test]
fn batch_ops_on_real_sample() {
    let mut config = sample("xime.theme.yaml");
    apply(
        &mut config,
        r#"[
            {"op":"set","path":"/metadata/app_name","value":"Xime 定制版"},
            {"op":"set","path":"/keyboard/qwerty/keys/q/swipe_up","value":{"label":"！" ,"action":"commit","value":"!"}},
            {"op":"set","path":"/keyboard/key/spacing_x","value":4}
        ]"#,
    );
    assert_eq!(config["metadata"]["app_name"], json!("Xime 定制版"));
    assert_eq!(config["keyboard"]["qwerty"]["keys"]["q"]["swipe_up"]["action"], json!("commit"));
    assert_eq!(config["keyboard"]["key"]["spacing_x"], json!(4));

    let yaml = parser::value_to_yaml(&config).unwrap();
    let back: Value = parser::yaml_to_value(&yaml).unwrap();
    assert_eq!(back["keyboard"]["key"]["spacing_x"], json!(4));
    let r = validator::validate(&back);
    assert!(r.valid, "批量编辑后应通过校验: {:?}", r.errors);
}

/// 非法输入必须是 Err 而非 panic。批次中途失败会中止且不回滚——
/// 调用方（Android/Web 层）负责在 Err 时丢弃结果保留旧值。
#[test]
fn malformed_yaml_and_ops_are_errors() {
    assert!(parser::yaml_to_value("key: [unclosed").is_err());
    assert!(parser::yaml_to_value("\t: : :").is_err());

    let mut config = sample("xime.default.yaml");
    let rows_before = config["keyboard"]["qwerty"]["layout"]["rows"].clone();
    // 未知 op 类型
    let err = ops::parse_ops(r#"[{"op":"move","path":"/a","value":1}]"#).unwrap_err();
    assert!(err.contains("不支持的操作类型"));
    // ops 不是数组
    assert!(ops::parse_ops(r#"{"op":"set"}"#).is_err());
    // 数组下标越界：op[0] 已生效、op[1] 失败即中止（不回滚）
    let parsed = ops::parse_ops(
        r#"[{"op":"set","path":"/metadata/app_name","value":"改动"},
            {"op":"remove","path":"/keyboard/qwerty/layout/rows/9"}]"#,
    )
    .unwrap();
    assert!(ops::apply_ops(&mut config, &parsed).is_err());
    assert_eq!(config["metadata"]["app_name"], json!("改动"), "失败前已应用的 op 保留");
    assert_eq!(
        config["keyboard"]["qwerty"]["layout"]["rows"], rows_before,
        "失败处的 op 不应产生修改"
    );
}

/// 行数边界：5 行合法，6 行产生"超过 5 行"警告。
#[test]
fn rows_count_boundary() {
    let five = json!({
        "metadata": {"app_name": "Xime"},
        "keyboard": {"qwerty": {"layout": {"rows": [
            ["1","2","3","4","5","6","7","8","9","0"],
            ["q","w","e","r","t","y","u","i","o","p"],
            ["a","s","d","f","g","h","j","k","l"],
            ["shift","z","x","c","v","b","n","m","delete"],
            ["mode_change","comma","space","earth","enter"]
        ]}}}
    });
    let r = validator::validate(&five);
    assert!(r.warnings.iter().all(|w| !w.contains("超过 5 行")), "5 行不应告警");

    let mut six = five.clone();
    six["keyboard"]["qwerty"]["layout"]["rows"].as_array_mut().unwrap()
        .push(json!(["a"]));
    let r6 = validator::validate(&six);
    assert!(r6.warnings.iter().any(|w| w.contains("超过 5 行")));
}
