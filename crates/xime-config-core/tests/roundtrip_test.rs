use serde_json::Value;
use std::fs;
use std::path::Path;
use xime_config_core::{ops, parser, validator};

fn samples_dir() -> &'static Path {
    Path::new(env!("CARGO_MANIFEST_DIR")).join("tests/samples").leak()
}

fn load_sample(name: &str) -> String {
    let path = samples_dir().join(name);
    fs::read_to_string(&path).unwrap_or_else(|e| panic!("读取 {} 失败: {}", path.display(), e))
}

#[test]
fn default_yaml_roundtrip_is_semantically_equal() {
    let yaml = load_sample("xime.default.yaml");
    let original: Value = parser::yaml_to_value(&yaml).unwrap();
    let yaml_out = parser::value_to_yaml(&original).unwrap();
    let reparsed: Value = parser::yaml_to_value(&yaml_out).unwrap();
    assert_eq!(original, reparsed, "往返后配置树发生语义变化");
}

#[test]
fn default_yaml_key_fields_are_understood() {
    let config: Value = parser::yaml_to_value(&load_sample("xime.default.yaml")).unwrap();

    // 十六进制整数字面量
    assert_eq!(
        config["color_schemes"]["lavender_purple"]["primary_color"].as_u64(),
        Some(0x8F73E2)
    );
    // 带 alpha 的 ARGB 颜色
    assert_eq!(
        config["keyboard"]["colors"]["key_bg_color_dark"].as_u64(),
        Some(0x60FF_FFFF)
    );
    // 浮点字段
    assert_eq!(
        config["keyboard"]["key"]["spacing_y"].as_f64(),
        Some(4.25)
    );
    assert_eq!(
        config["keyboard"]["shadow"]["elevation"].as_f64(),
        Some(0.5)
    );
    // 行内 flow mapping 手势
    assert_eq!(config["keyboard"]["qwerty"]["keys"]["q"]["swipe_up"], Value::String("1".into()));
    assert_eq!(
        config["keyboard"]["qwerty"]["keys"]["earth"]["tap"]["action"],
        Value::String("toggle_ascii".into())
    );
    // 标量/对象双写的 color_scheme
    assert_eq!(
        config["style"]["color_scheme"]["dark"],
        Value::String("slate_gray".into())
    );
}

#[test]
fn unknown_fields_survive_roundtrip() {
    let yaml = r#"
metadata:
  app_name: Xime
  a_future_field: "未来版本新增的字段"
style:
  color_scheme:
    light: lavender_purple
    dark: slate_gray
    another_new_thing: 42
keyboard:
  some_new_section:
    nested: [1, 2, 3]
"#;
    let original: Value = parser::yaml_to_value(yaml).unwrap();
    let yaml_out = parser::value_to_yaml(&original).unwrap();
    let reparsed: Value = parser::yaml_to_value(&yaml_out).unwrap();
    assert_eq!(reparsed["metadata"]["a_future_field"], Value::String("未来版本新增的字段".into()));
    assert_eq!(reparsed["style"]["color_scheme"]["another_new_thing"], Value::Number(42.into()));
    assert_eq!(reparsed["keyboard"]["some_new_section"]["nested"], serde_json::json!([1, 2, 3]));
}

#[test]
fn ops_edit_gesture_end_to_end() {
    let yaml = load_sample("xime.default.yaml");
    let mut config: Value = parser::yaml_to_value(&yaml).unwrap();

    let ops_json = r#"[
        {"op":"set","path":"/keyboard/qwerty/keys/a/swipe_up","value":{"label":"～","action":"commit","value":"~","display":"bubble"}},
        {"op":"set","path":"/color_schemes/lavender_purple/primary_color","value":15132390},
        {"op":"add","path":"/keyboard/t9/side_symbols/-","value":"、"},
        {"op":"remove","path":"/keyboard/qwerty/keys/z/swipe_down"}
    ]"#;
    let parsed = ops::parse_ops(ops_json).unwrap();
    ops::apply_ops(&mut config, &parsed).unwrap();

    let yaml_out = parser::value_to_yaml(&config).unwrap();
    let reparsed: Value = parser::yaml_to_value(&yaml_out).unwrap();

    assert_eq!(reparsed["keyboard"]["qwerty"]["keys"]["a"]["swipe_up"]["label"], Value::String("～".into()));
    assert_eq!(
        reparsed["color_schemes"]["lavender_purple"]["primary_color"].as_u64(),
        Some(15132390)
    );
    let symbols = reparsed["keyboard"]["t9"]["side_symbols"].as_array().unwrap();
    assert_eq!(symbols.last().unwrap(), &Value::String("、".into()));
    assert!(reparsed["keyboard"]["qwerty"]["keys"]["z"].get("swipe_down").is_none());
}

#[test]
fn ops_color_scheme_crud() {
    let yaml = load_sample("xime.default.yaml");
    let mut config: Value = parser::yaml_to_value(&yaml).unwrap();

    // 复制一个方案 = add + 原值；重命名 = remove + add
    let original = config["color_schemes"]["ocean_blue"].clone();
    let ops_json = serde_json::json!([
        {"op": "add", "path": "/color_schemes/my_scheme", "value": original},
        {"op": "remove", "path": "/color_schemes/ocean_blue"}
    ])
    .to_string();
    ops::apply_ops(&mut config, &ops::parse_ops(&ops_json).unwrap()).unwrap();

    assert!(config["color_schemes"].get("ocean_blue").is_none());
    assert_eq!(config["color_schemes"]["my_scheme"]["name"], Value::String("海洋蔚蓝".into()));

    let result = validator::validate(&config);
    assert!(result.valid, "增删方案后应仍然合法: {:?}", result.errors);
}

#[test]
fn validate_default_yaml_has_no_errors() {
    let config: Value = parser::yaml_to_value(&load_sample("xime.default.yaml")).unwrap();
    let result = validator::validate(&config);
    assert!(result.valid, "默认配置不应有错误: {:?}", result.errors);
    assert!(result.warnings.is_empty(), "默认配置不应有警告: {:?}", result.warnings);
}

#[test]
fn validate_xime30_features() {
    // 3.0 新手势类型（左右滑/双击/repeat_space/switch_route/bubble）与新布局 section 校验
    let config: Value = serde_json::json!({
        "metadata": {"app_name": "Xime", "app_version": ">=3.0.0"},
        "keyboard": {
            "qwerty": {
                "layout": {"rows": [
                    ["q", "w"],
                    [ ["a", "s"], "d" ],
                    [ "shift", "z", "delete" ],
                    ["mode_change", "space", "enter"]
                ]},
                "keys": {
                    "q": { "tap": {"label": "q", "action": "send_rime"}, "swipe_left": {"label": "删词", "action": "command", "value": "clear_composition"}, "long_press": {"display": "bubble", "values": ["q", "Q"]} },
                    "w": { "swipe_up": {"value": "2", "display": "key", "bubble": false} },
                    "d": { "double_tap": {"action": "toggle_shift"}, "swipe_right": {"label": "粘贴", "action": "paste"} },
                    "space": { "long_press": {"values": [{"action": "repeat_space", "value": 3}]} },
                    "shift": { "width": 1.4, "tap": {"action": "command", "value": "shift_single"} },
                    "z": { "tap": "z" },
                    "mode_change": { "tap": {"action": "command", "value": "mode_change"} },
                    "2": { "tap": {"label": "ABC"} },
                    "3": { "tap": {"label": "DEF"} }
                }
            },
            "t9": {
                "schemas": ["t9_pinyin"],
                "layout": {"left": ["candidates"], "rows": [["1", "2", "3"]], "right": ["delete", "clear", "enter"]},
                "keys": {"1": {"swipe_up": {"value": "1", "display": "key", "bubble": false}}}
            },
            "qwerty_14": {"schemas": ["pinyin_14jian"]}
        }
    });
    let result = validator::validate(&config);
    assert!(result.valid, "3.0 特性不应报错: {:?}", result.errors);
    let real: Vec<_> = result.warnings.iter().filter(|w| !w.contains("color_schemes")).collect();
    assert!(real.is_empty(), "3.0 内置键/命令不应告警: {:?}", real);

    // 旧版遗留字段（when_composing）与非法 width 应有告警/错误
    let legacy: Value = serde_json::json!({
        "metadata": {"app_name": "Xime"},
        "keyboard": {"qwerty": {"keys": {
            "q": {"tap": "q", "when_composing": true},
            "w": {"tap": "w", "width": 0}
        }}}
    });
    let r2 = validator::validate(&legacy);
    assert!(r2.warnings.iter().any(|w| w.contains("when_composing")));
    assert!(!r2.valid, "width=0 应报错");
}

#[test]
fn validate_catches_schema_violations() {
    let config: Value = serde_json::json!({
        "metadata": {"app_name": "", "app_version": "abc", "config_version": 1},
        "style": {"dark_mode": 5, "color_scheme": {"light": ""}},
        "xime_index": {"base_urls": ["https://example.com"]},
        "color_schemes": {
            "bad": {"name": "坏例子", "primary_color": "0xFF0000"},
            "grad": {"name": "渐变", "keyboard_background": {"type": "gradient", "colors": [1]}},
            "img": {"name": "图片", "keyboard_background": {"type": "image", "src": "a.jpg", "fit": "zoom", "overlay_alpha": 2.0}},
            "dyn": {"name": "动态", "dynamic_color": true, "primary_color": "可以是任意"}
        },
        "keyboard": {
            "colors": {"key_bg_color": "白色"},
            "shadow": {"enabled": "yes"},
            "qwerty": {
                "button_layout": "fancy",
                "layout": {"rows": [["q", "w"], ["a"]]},
                "keys": {"q": {"tap": {"action": "fly_to_moon"}}, "a": {"long_press": {"display": "sideways", "values": ["a"]}}}
            }
        }
    });

    let result = validator::validate(&config);
    let paths: Vec<&str> = result.errors.iter().map(|e| e.path.as_str()).collect();

    for expected in [
        "metadata/app_name",
        "metadata/app_version",
        "style/dark_mode",
        "style/color_scheme/light",
        "color_schemes/bad/primary_color",
        "color_schemes/grad/keyboard_background/colors",
        "color_schemes/img/keyboard_background/fit",
        "color_schemes/img/keyboard_background/overlay_alpha",
        "keyboard/colors/key_bg_color",
        "keyboard/shadow/enabled",
        "keyboard/qwerty/button_layout",
        "keyboard/qwerty/keys/a/long_press/display",
    ] {
        assert!(paths.contains(&expected), "缺少预期错误 {}，实际: {:?}", expected, paths);
    }

    // dynamic_color: true 的方案跳过静态颜色检查
    assert!(!paths.iter().any(|p| p.contains("color_schemes/dyn")));
    // 未知动作按 Xime 的行为（只显示不执行）降级为警告
    assert!(result.warnings.iter().any(|w| w.contains("fly_to_moon") && w.contains("只显示不执行")));
    // 行内未定义的键与 URL 末尾斜杠是警告
    assert!(result.warnings.iter().any(|w| w.contains("未在 keys 中定义")));
    assert!(result.warnings.iter().any(|w| w.contains("末尾建议带 /")));
    // ARGB 颜色（带 alpha）不应被判为非法
    let mut ok_config = serde_json::json!({
        "metadata": {"app_name": "Xime"},
        "keyboard": {"colors": {"key_bg_color_dark": 0x60FF_FFFFu64}, "qwerty": {"keys": {}}}
    });
    let ok = validator::validate(&mut ok_config);
    assert!(ok.valid, "ARGB 颜色应合法: {:?}", ok.errors);
}

#[test]
fn all_vendored_samples_roundtrip() {
    let dir = samples_dir();
    let mut names: Vec<_> = fs::read_dir(dir)
        .expect("samples 目录存在")
        .map(|e| e.unwrap().file_name().to_string_lossy().into_owned())
        .filter(|n| n.ends_with(".yaml"))
        .collect();
    names.sort();

    assert!(names.len() >= 8, "应至少有 8 个样本，实际: {:?}", names);

    for name in names {
        let yaml = load_sample(&name);
        let original: Value = parser::yaml_to_value(&yaml)
            .unwrap_or_else(|e| panic!("{}: 解析失败: {}", name, e));
        let yaml_out = parser::value_to_yaml(&original)
            .unwrap_or_else(|e| panic!("{}: 序列化失败: {}", name, e));
        let reparsed: Value = parser::yaml_to_value(&yaml_out)
            .unwrap_or_else(|e| panic!("{}: 回读失败: {}\n--- 生成的 YAML ---\n{}", name, e, yaml_out));
        assert_eq!(original, reparsed, "{}: 往返语义不等价", name);

        let result = validator::validate(&reparsed);
        assert!(result.valid, "{}: 校验失败: {:?}", name, result.errors);
    }
}

#[test]
fn validate_xime30_presets_and_label_array() {
    // label 数组（多行显示）与 keyboard.actions 预设 / use 引用是 3.0 合法写法
    let config: Value = serde_json::json!({
        "metadata": {"app_name": "Xime"},
        "keyboard": {
            "actions": {"my_copy": {"label": ["复制", "到剪贴板"], "action": "copy"}},
            "qwerty": {"keys": {
                "q": {"tap": {"use": "my_copy"}},
                "delete": {"long_press": {"values": [{"action": "delete", "repeat": true}]}}
            }}
        }
    });
    let r = validator::validate(&config);
    let real: Vec<_> = r.warnings.iter().filter(|w| !w.contains("color_schemes")).collect();
    assert!(r.valid, "label 数组/use/repeat 不应报错: {:?}", r.errors);
    assert!(real.is_empty(), "合法写法不应告警: {:?}", real);

    // use 引用未知预设 → 警告（Xime 运行时该手势不生效）
    let bad: Value = serde_json::json!({
        "metadata": {"app_name": "Xime"},
        "keyboard": {"qwerty": {"keys": {"q": {"tap": {"use": "ghost"}}}}}
    });
    let rb = validator::validate(&bad);
    assert!(rb.warnings.iter().any(|w| w.contains("未知动作预设") && w.contains("ghost")));

    // repeat 非布尔 → 错误
    let bad2: Value = serde_json::json!({
        "metadata": {"app_name": "Xime"},
        "keyboard": {"qwerty": {"keys": {"q": {"tap": {"repeat": "yes"}}}}}
    });
    let r2 = validator::validate(&bad2);
    assert!(!r2.valid, "repeat 非布尔应报错");
}

#[test]
fn field_descriptors_cover_sections() {
    let descriptors = parser::get_field_descriptors();
    let sections: std::collections::HashSet<_> = descriptors.iter().map(|d| d.section.as_str()).collect();
    for expected in ["metadata", "style", "keyboard_colors", "keyboard_key", "keyboard_shadow", "keyboard_fonts", "keyboard_layout", "color_scheme"] {
        assert!(sections.contains(&expected), "描述符缺少分区 {}", expected);
    }
    // 配色方案字段使用 {id} 占位
    assert!(descriptors.iter().any(|d| d.path == "/color_schemes/{id}/primary_color"));
}
