//! 对配置 JSON 树做结构校验。这里是 xime.yaml schema 知识的唯一存放点：
//! 新增/修改字段时只需要改这个文件与 UI 层。
use crate::model::{ValidationResult, ValidationError};
use serde_json::Value;

/// 手势动作枚举（与 Xime 3.0 的 GestureAction / KeyActionRegistry 对应）。
pub const GESTURE_ACTIONS: &[&str] = &[
    "commit", "send_rime", "command", "select_all", "copy", "cut", "paste",
    "line_start", "line_end", "undo", "none", "repeat",
    "switch_route", "toggle_ascii", "delete", "toggle_symbols",
    "enter", "newline", "space", "repeat_space",
    "clear_all", "undo_clear", "toggle_shift", "voice",
];

/// command 动作可用的命令值（Xime 3.0：键盘按键路由命令）。
pub const COMMAND_VALUES: &[&str] = &[
    "clear_composition", "show_ime_picker",
    "shift_single", "shift_caps", "toggle_shift",
    "mode_change", "mode_change_number", "mode_change_common_symbol",
];

/// 特化键盘（t9 等）内置键 id：组件与行为内置、无需在 keys 中定义
/// （功能键 + 九键数字键 "0"~"9"，未配置时走内置行为；shift/comma/mode_change
/// 为标准功能键，3.0 起在 keys 定义，旧配置缺省时同样走内置行为）。
pub const BUILTIN_FUNCTION_KEYS: &[&str] = &[
    "candidates", "symbol", "number", "space", "earth", "delete", "clear", "enter",
    "shift", "comma", "mode_change",
    "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
];

/// switch_route 可用的面板路由值。
pub const SWITCH_ROUTE_VALUES: &[&str] = &["emoji", "symbol", "clipboard"];

/// 手势类型（键级字段）。Xime 3.0 支持点按/双击/长按/四向滑动/列宽。
pub const GESTURE_FIELDS: &[&str] = &[
    "tap", "double_tap", "long_press", "swipe_up", "swipe_down", "swipe_left", "swipe_right",
];

const DISPLAY_MODES: &[&str] = &["key", "bubble", "both"];
const BACKGROUND_FITS: &[&str] = &["cover", "contain", "fill", "fit_width", "fit_height", "none"];
const BACKGROUND_TYPES: &[&str] = &["solid", "gradient", "image"];

const SCHEME_COLOR_FIELDS: &[&str] = &[
    "primary_color", "keyboard_bg_color",
    "key_bg_color", "key_bg_color_dark",
    "special_key_bg_color", "special_key_bg_color_dark",
    "candidate_bar_bg_color",
    "key_text_color", "key_text_color_dark",
    "candidate_text_color", "candidate_text_color_dark",
    "candidate_selected_text_color", "candidate_selected_text_color_dark",
];

const KEYBOARD_COLOR_FIELDS: &[&str] = &[
    "key_bg_color", "key_bg_color_dark",
    "special_key_bg_color", "special_key_bg_color_dark",
    "candidate_bar_bg_color", "candidate_bar_bg_color_dark",
    "key_text_color", "key_text_color_dark",
    "candidate_text_color", "candidate_text_color_dark",
    "keyboard_bg_color", "keyboard_bg_color_dark",
];

pub fn validate(config: &Value) -> ValidationResult {
    let mut errors = Vec::new();
    let mut warnings = Vec::new();

    if !config.is_object() {
        errors.push(err("", "根节点必须是映射"));
        return finish(errors, warnings);
    }

    validate_metadata(config, &mut errors, &mut warnings);
    validate_index(config, &mut errors, &mut warnings);
    validate_style(config, &mut errors, &mut warnings);
    validate_color_schemes(config, &mut errors, &mut warnings);
    validate_keyboard(config, &mut errors, &mut warnings);

    finish(errors, warnings)
}

fn finish(mut errors: Vec<ValidationError>, mut warnings: Vec<String>) -> ValidationResult {
    errors.sort_by(|a, b| a.path.cmp(&b.path));
    warnings.sort();
    warnings.dedup();
    ValidationResult {
        valid: errors.is_empty(),
        errors,
        warnings,
    }
}

fn err(path: &str, message: impl Into<String>) -> ValidationError {
    ValidationError {
        path: path.into(),
        message: message.into(),
        severity: "error".into(),
    }
}

fn warn(warnings: &mut Vec<String>, message: impl Into<String>) {
    warnings.push(message.into());
}

fn validate_metadata(config: &Value, errors: &mut Vec<ValidationError>, warnings: &mut Vec<String>) {
    let Some(meta) = config.get("metadata") else {
        // xime.custom.yaml 常常只覆盖部分段，缺失时 Xime 走内置默认值
        warn(warnings, "配置中没有 metadata 段，将使用内置默认值");
        return;
    };
    if !meta.is_object() {
        errors.push(err("metadata", "metadata 必须是映射"));
        return;
    }

    match meta.get("app_name") {
        Some(Value::String(s)) if !s.trim().is_empty() => {}
        Some(_) => errors.push(err("metadata/app_name", "应用名必须是非空字符串")),
        None => errors.push(err("metadata/app_name", "缺少应用名")),
    }

    if let Some(v) = meta.get("app_version") {
        match v.as_str() {
            Some(s) if is_version_constraint(s) => {}
            Some(s) => errors.push(err("metadata/app_version", format!("版本约束格式非法: {}（示例 >=2.5.0）", s))),
            None => errors.push(err("metadata/app_version", "版本约束必须是字符串")),
        }
    }

    if let Some(p) = meta.get("platform") {
        if p.as_str() != Some("android") {
            warn(warnings, format!("metadata/platform 通常为 android，当前为 {}", p));
        }
    }

    if let Some(v) = meta.get("config_version") {
        if v.as_u64().is_none() {
            errors.push(err("metadata/config_version", "配置版本必须是整数"));
        }
    }

    if let Some(t) = meta.get("modified_time").and_then(Value::as_str) {
        if !t.is_empty() && !is_date(t) {
            warn(warnings, format!("metadata/modified_time 建议 YYYY-MM-DD 格式，当前为 {}", t));
        }
    }
}

fn is_version_constraint(s: &str) -> bool {
    let body = s
        .strip_prefix(">=")
        .or_else(|| s.strip_prefix("<="))
        .or_else(|| s.strip_prefix('>'))
        .or_else(|| s.strip_prefix('<'))
        .or_else(|| s.strip_prefix('^'))
        .or_else(|| s.strip_prefix('~'))
        .unwrap_or(s);
    !body.is_empty()
        && body.split('.').all(|part| !part.is_empty() && part.chars().all(|c| c.is_ascii_digit()))
}

fn is_date(s: &str) -> bool {
    let bytes = s.as_bytes();
    bytes.len() == 10
        && bytes[4] == b'-'
        && bytes[7] == b'-'
        && s.chars().filter(|c| *c != '-').all(|c| c.is_ascii_digit())
}

fn validate_index(config: &Value, errors: &mut Vec<ValidationError>, warnings: &mut Vec<String>) {
    let Some(index) = config.get("xime_index") else {
        return;
    };
    let Some(urls) = index.get("base_urls") else {
        return;
    };
    let Some(list) = urls.as_array() else {
        errors.push(err("xime_index/base_urls", "base_urls 必须是字符串数组"));
        return;
    };
    if list.is_empty() {
        warn(warnings, "xime_index/base_urls 为空，市场下载将不可用");
    }
    for (i, url) in list.iter().enumerate() {
        match url.as_str() {
            Some(s) if s.is_empty() => errors.push(err(&format!("xime_index/base_urls/{}", i), "URL 不能为空")),
            Some(s) if !s.ends_with('/') => warn(warnings, format!("xime_index/base_urls/{} 末尾建议带 /：{}", i, s)),
            Some(_) => {}
            None => errors.push(err(&format!("xime_index/base_urls/{}", i), "必须是字符串")),
        }
    }
}

fn validate_style(config: &Value, errors: &mut Vec<ValidationError>, warnings: &mut Vec<String>) {
    let Some(style) = config.get("style") else {
        return;
    };
    if !style.is_object() {
        errors.push(err("style", "style 必须是映射"));
        return;
    }

    match style.get("dark_mode") {
        Some(Value::Number(n)) if (0..=2).contains(&n.as_i64().unwrap_or(-1)) => {}
        Some(Value::Number(_)) => errors.push(err("style/dark_mode", "dark_mode 取值必须为 0/1/2")),
        Some(_) => errors.push(err("style/dark_mode", "dark_mode 必须是整数")),
        None => {}
    }

    match style.get("color_scheme") {
        Some(Value::String(s)) => {
            if s.is_empty() {
                errors.push(err("style/color_scheme", "主题 id 不能为空"));
            } else {
                warn(warnings, "style/color_scheme 建议改用 {light, dark} 对象写法以区分深浅模式".to_string());
            }
        }
        Some(Value::Object(map)) => {
            for key in ["light", "dark"] {
                match map.get(key) {
                    Some(Value::String(s)) if !s.is_empty() => {}
                    Some(_) => errors.push(err(&format!("style/color_scheme/{}", key), "主题 id 必须是非空字符串")),
                    None => {}
                }
            }
        }
        Some(_) => errors.push(err("style/color_scheme", "color_scheme 必须是字符串或 {light, dark} 映射")),
        None => {}
    }
}

fn validate_color_schemes(config: &Value, errors: &mut Vec<ValidationError>, warnings: &mut Vec<String>) {
    let Some(schemes) = config.get("color_schemes") else {
        warn(warnings, "配置中没有 color_schemes，将全部使用内置主题");
        return;
    };
    let Some(map) = schemes.as_object() else {
        errors.push(err("color_schemes", "color_schemes 必须是映射"));
        return;
    };
    if map.is_empty() {
        warn(warnings, "color_schemes 为空");
    }

    for (id, entry) in map {
        let prefix = format!("color_schemes/{}", id);
        let Some(obj) = entry.as_object() else {
            errors.push(err(&prefix, "配色方案必须是映射"));
            continue;
        };

        match obj.get("name") {
            Some(Value::String(s)) if !s.is_empty() => {}
            _ => errors.push(err(&format!("{}/name", prefix), "缺少方案显示名")),
        }

        let dynamic = obj.get("dynamic_color").and_then(Value::as_bool);
        match obj.get("dynamic_color") {
            Some(Value::Bool(_)) => {}
            Some(_) => errors.push(err(&format!("{}/dynamic_color", prefix), "dynamic_color 必须是布尔值")),
            None => {}
        }
        if dynamic == Some(true) {
            continue; // 动态配色忽略静态颜色字段
        }

        for &field in SCHEME_COLOR_FIELDS {
            let path = format!("{}/{}", prefix, field);
            match obj.get(field) {
                Some(Value::Null) | None => {}
                Some(v) => check_color_value(&path, v, errors),
            }
        }

        for field in ["keyboard_background", "key_background", "candidate_bar_background"] {
            let path = format!("{}/{}", prefix, field);
            match obj.get(field) {
                Some(Value::Null) | None => {}
                Some(bg) => validate_background(&path, bg, errors, warnings),
            }
        }
    }
}

fn check_color_value(path: &str, v: &Value, errors: &mut Vec<ValidationError>) {
    match v.as_u64() {
        Some(n) if n <= 0xFFFF_FFFF => {}
        Some(_) => errors.push(err(path, "颜色值超出 0xFFFFFFFF 范围")),
        None => errors.push(err(path, format!("颜色必须是整数（0x 字面量或十进制），当前为 {}", v))),
    }
}

fn validate_background(path: &str, bg: &Value, errors: &mut Vec<ValidationError>, warnings: &mut Vec<String>) {
    let Some(obj) = bg.as_object() else {
        errors.push(err(path, "背景配置必须是映射"));
        return;
    };
    match obj.get("type").and_then(Value::as_str) {
        Some(t) if BACKGROUND_TYPES.contains(&t) => {}
        Some(t) => {
            errors.push(err(&format!("{}/type", path), format!("未知背景类型: {}（可选 solid/gradient/image）", t)));
            return;
        }
        None => {
            errors.push(err(&format!("{}/type", path), "缺少背景类型 type"));
            return;
        }
    }

    let kind = obj.get("type").and_then(Value::as_str).unwrap_or("");
    for dark_key in ["color_dark", "colors_dark", "overlay_alpha_dark"] {
        if dark_key != expected_dark_key(kind) && obj.get(dark_key).is_some() {
            warn(warnings, format!("{}/{} 对当前背景类型无效果", path, dark_key));
        }
    }

    match kind {
        "solid" => match obj.get("color") {
            Some(v) => check_color_value(&format!("{}/color", path), v, errors),
            None => errors.push(err(&format!("{}/color", path), "solid 背景缺少 color")),
        },
        "gradient" => {
            match obj.get("colors").and_then(Value::as_array) {
                Some(list) if list.len() >= 2 => {
                    for (i, c) in list.iter().enumerate() {
                        check_color_value(&format!("{}/colors/{}", path, i), c, errors);
                    }
                }
                Some(_) => errors.push(err(&format!("{}/colors", path), "渐变至少需要 2 个颜色断点")),
                None => errors.push(err(&format!("{}/colors", path), "gradient 背景缺少 colors 数组")),
            }
            if let Some(list) = obj.get("colors_dark").and_then(Value::as_array) {
                for (i, c) in list.iter().enumerate() {
                    check_color_value(&format!("{}/colors_dark/{}", path, i), c, errors);
                }
            }
            match obj.get("angle") {
                Some(Value::Number(n)) => {
                    let a = n.as_f64().unwrap_or(f64::NAN);
                    if !(0.0..=360.0).contains(&a) {
                        warn(warnings, format!("{}/angle 建议取 0~360，当前为 {}", path, a));
                    }
                }
                Some(_) => errors.push(err(&format!("{}/angle", path), "angle 必须是数字")),
                None => {}
            }
        }
        "image" => {
            match obj.get("src") {
                Some(Value::String(s)) if !s.is_empty() => {}
                Some(_) => errors.push(err(&format!("{}/src", path), "src 必须是非空字符串")),
                None => errors.push(err(&format!("{}/src", path), "image 背景缺少 src")),
            }
            if let Some(f) = obj.get("fit") {
                match f.as_str() {
                    Some(s) if BACKGROUND_FITS.contains(&s) => {}
                    Some(s) => errors.push(err(&format!("{}/fit", path), format!("未知 fit 模式: {}", s))),
                    None => errors.push(err(&format!("{}/fit", path), "fit 必须是字符串")),
                }
            }
            for key in ["overlay_alpha", "overlay_alpha_dark"] {
                if let Some(a) = obj.get(key) {
                    match a.as_f64() {
                        Some(v) if (0.0..=1.0).contains(&v) => {}
                        Some(_) => errors.push(err(&format!("{}/{}", path, key), "遮罩透明度取值必须为 0~1")),
                        None => errors.push(err(&format!("{}/{}", path, key), "遮罩透明度必须是数字")),
                    }
                }
            }
        }
        _ => {}
    }
}

fn expected_dark_key(kind: &str) -> &'static str {
    match kind {
        "solid" => "color_dark",
        "gradient" => "colors_dark",
        _ => "overlay_alpha_dark",
    }
}

fn validate_keyboard(config: &Value, errors: &mut Vec<ValidationError>, warnings: &mut Vec<String>) {
    let Some(keyboard) = config.get("keyboard") else {
        warn(warnings, "配置中没有 keyboard 段，将使用内置默认值");
        return;
    };
    let Some(kb) = keyboard.as_object() else {
        errors.push(err("keyboard", "keyboard 必须是映射"));
        return;
    };

    // ── keyboard.actions：可复用动作预设（手势对象 {use: 预设名} 引用）──
    // 未定义 actions 时 presets 为空表——手势里的 use 引用一律无法解析，同样要告警
    let mut presets: Vec<String> = Vec::new();
    if let Some(actions) = kb.get("actions") {
        match actions.as_object() {
            Some(map) => {
                presets = map.keys().cloned().collect();
                for (name, def) in map {
                    validate_gesture(&format!("keyboard/actions/{}", name), def, errors, warnings);
                }
            }
            None => errors.push(err("keyboard/actions", "actions 必须是映射（预设名 → 动作定义）")),
        }
    }
    // use 引用存在性：逐键盘段检查手势里的 {use: xxx}
    {
        for layout_name in ["qwerty", "qwerty_en", "qwerty_14", "qwerty_17", "qwerty_18", "t9", "stroke"] {
            let Some(keys) = kb.get(layout_name).and_then(|l| l.get("keys")).and_then(Value::as_object) else {
                continue;
            };
            for (key_id, binding) in keys {
                let Some(bind_obj) = binding.as_object() else { continue };
                for slot in ["tap", "double_tap", "swipe_up", "swipe_down", "swipe_left", "swipe_right"] {
                    if let Some(use_name) = bind_obj.get(slot).and_then(|g| g.get("use")).and_then(Value::as_str) {
                        if !presets.iter().any(|n| n == use_name) {
                            warn(warnings, format!(
                                "keyboard/{}/keys/{}/{}：引用了未知动作预设 \"{}\"（keyboard.actions 未定义），该手势将不生效",
                                layout_name, key_id, slot, use_name
                            ));
                        }
                    }
                }
                if let Some(values) = binding.get("long_press").and_then(|lp| lp.get("values")).and_then(Value::as_array) {
                    for (i, item) in values.iter().enumerate() {
                        if let Some(use_name) = item.get("use").and_then(Value::as_str) {
                            if !presets.iter().any(|n| n == use_name) {
                                warn(warnings, format!(
                                    "keyboard/{}/keys/{}/long_press/values/{}：引用了未知动作预设 \"{}\"，该项不生效",
                                    layout_name, key_id, i, use_name
                                ));
                            }
                        }
                    }
                }
            }
        }
    }

        if let Some(colors) = kb.get("colors") {
            if let Some(map) = colors.as_object() {
                for &field in KEYBOARD_COLOR_FIELDS {
                    let path = format!("keyboard/colors/{}", field);
                    match map.get(field) {
                    Some(Value::Null) | None => {}
                    Some(v) => check_color_value(&path, v, errors),
                }
            }
        } else if !colors.is_null() {
            errors.push(err("keyboard/colors", "colors 必须是映射"));
        }
    }

    if let Some(key) = kb.get("key") {
        if let Some(obj) = key.as_object() {
            for (name, v) in obj {
                let path = format!("keyboard/key/{}", name);
                match name.as_str() {
                    "corner_radius" => check_number_in(&path, v, 0.0, 64.0, errors),
                    "spacing_x" | "spacing_y" => check_number_in(&path, v, 0.0, 24.0, errors),
                    "qwerty" | "t9" | "number" | "stroke" | "symbol" => {
                        if let Some(over) = v.as_object() {
                            for (k, sv) in over {
                                if k == "spacing_x" || k == "spacing_y" {
                                    check_number_in(&format!("{}/{}", path, k), sv, 0.0, 24.0, errors);
                                } else {
                                    warn(warnings, format!("{}/{} 是未知的间距字段", path, k));
                                }
                            }
                        } else {
                            errors.push(err(&path, "间距覆盖必须是映射"));
                        }
                    }
                    other => warn(warnings, format!("keyboard/key/{} 是未知字段", other)),
                }
            }
        } else if !key.is_null() {
            errors.push(err("keyboard/key", "key 必须是映射"));
        }
    }

    if let Some(shadow) = kb.get("shadow") {
        if let Some(obj) = shadow.as_object() {
            if let Some(v) = obj.get("enabled") {
                if !v.is_boolean() {
                    errors.push(err("keyboard/shadow/enabled", "enabled 必须是布尔值"));
                }
            }
            for name in ["elevation", "shape_radius"] {
                if let Some(v) = obj.get(name) {
                    check_number_in(&format!("keyboard/shadow/{}", name), v, 0.0, 64.0, errors);
                }
            }
        } else if !shadow.is_null() {
            errors.push(err("keyboard/shadow", "shadow 必须是映射"));
        }
    }

    if let Some(fonts) = kb.get("fonts") {
        if let Some(obj) = fonts.as_object() {
            for (name, v) in obj {
                if !v.is_string() {
                    errors.push(err(&format!("keyboard/fonts/{}", name), "字体路径必须是字符串"));
                }
            }
        } else if !fonts.is_null() {
            errors.push(err("keyboard/fonts", "fonts 必须是映射"));
        }
    }

    // Xime 3.0 布局 section：qwerty / qwerty_en 为标准布局；其余为特化键盘，
    // 均含 schemas（方案绑定）、layout.rows（t9 另有 left/right 列）与 keys（按键级覆盖）
    for layout_name in ["qwerty", "qwerty_en", "qwerty_14", "qwerty_17", "qwerty_18", "t9", "stroke", "handwriting", "number", "symbol", "comma_period"] {
        if let Some(layout) = kb.get(layout_name) {
            validate_layout(&format!("keyboard/{}", layout_name), layout, errors, warnings);
        }
    }
    if kb.get("qwerty").is_none() && kb.get("qwerty_en").is_none() {
        warn(warnings, "keyboard 未定义 qwerty / qwerty_en 布局");
    }

    for side_name in ["t9", "stroke"] {
        if let Some(kb_section) = kb.get(side_name).and_then(|s| s.as_object()) {
            if let Some(symbols) = kb_section.get("side_symbols") {
                match symbols.as_array() {
                    Some(list) => {
                        for (i, s) in list.iter().enumerate() {
                            if s.as_str().is_none() {
                                errors.push(err(&format!("keyboard/{}/side_symbols/{}", side_name, i), "快捷符号必须是字符串"));
                            }
                        }
                    }
                    None => errors.push(err(&format!("keyboard/{}/side_symbols", side_name), "side_symbols 必须是字符串数组")),
                }
            }
        }
    }
}

fn check_number_in(path: &str, v: &Value, min: f64, max: f64, errors: &mut Vec<ValidationError>) {
    match v.as_f64() {
        Some(n) if (min..=max).contains(&n) => {}
        Some(n) => errors.push(err(path, format!("取值应在 {}~{} 之间，当前为 {}", min, max, n))),
        None => errors.push(err(path, format!("必须是数字，当前为 {}", v))),
    }
}

fn validate_layout(prefix: &str, layout: &Value, errors: &mut Vec<ValidationError>, warnings: &mut Vec<String>) {
    let Some(obj) = layout.as_object() else {
        errors.push(err(prefix, "布局必须是映射"));
        return;
    };

    if let Some(bl) = obj.get("button_layout") {
        match bl.as_str() {
            Some(s) if s == "standard" || s == "compact" => {}
            Some(s) => errors.push(err(&format!("{}/button_layout", prefix), format!("未知布局模式: {}（可选 standard/compact）", s))),
            None => errors.push(err(&format!("{}/button_layout", prefix), "button_layout 必须是字符串")),
        }
    }

    // schemas：声明使用此布局的输入方案（schema_id 数组），可选
    if let Some(schemas) = obj.get("schemas") {
        match schemas.as_array() {
            Some(list) => {
                for (i, s) in list.iter().enumerate() {
                    if s.as_str().map(|v| !v.is_empty()) != Some(true) {
                        errors.push(err(&format!("{}/schemas/{}", prefix, i), "schema id 必须是非空字符串"));
                    }
                }
            }
            None => errors.push(err(&format!("{}/schemas", prefix), "schemas 必须是字符串数组")),
        }
    }

    let mut defined_keys: Option<Vec<String>> = None;
    if let Some(keys) = obj.get("keys") {
        match keys.as_object() {
            Some(map) => {
                defined_keys = Some(map.keys().cloned().collect());
                for (key_id, binding) in map {
                    validate_key_binding(&format!("{}/keys/{}", prefix, key_id), binding, errors, warnings);
                }
            }
            None => errors.push(err(&format!("{}/keys", prefix), "keys 必须是映射")),
        }
    }

    if let Some(rows) = obj.get("layout").and_then(|l| l.get("rows")).and_then(Value::as_array) {
        if rows.len() > 5 {
            warn(warnings, format!("{}/layout/rows 超过 5 行，多出的行会被忽略", prefix));
        }
        for (ri, row) in rows.iter().enumerate() {
            let Some(key_ids) = row.as_array() else {
                errors.push(err(&format!("{}/layout/rows/{}", prefix, ri), "行必须是键位数组"));
                continue;
            };
            for (ki, id) in key_ids.iter().enumerate() {
                match id {
                    // Xime 3.0 支持子数组：[[q, w], ...] 表示合并键（键 id 为组内字母拼接）
                    Value::Array(group) => {
                        if group.is_empty() {
                            errors.push(err(&format!("{}/layout/rows/{}/{}", prefix, ri, ki), "合并键组不能为空"));
                        }
                        for (gi, g) in group.iter().enumerate() {
                            if g.as_str().map(|s| !s.is_empty()) != Some(true) {
                                errors.push(err(&format!("{}/layout/rows/{}/{}", prefix, ri, ki), "合并键组内必须是字符串"));
                            }
                            let _ = gi;
                        }
                    }
                    Value::String(s) if !s.is_empty() => {
                        if let Some(defined) = &defined_keys {
                            if !defined.iter().any(|k| k == s) && !BUILTIN_FUNCTION_KEYS.contains(&s.as_str()) {
                                warn(warnings, format!("{}/layout/rows/{}/{}：键 {} 未在 keys 中定义，将使用默认行为", prefix, ri, ki, s));
                            }
                        }
                    }
                    _ => errors.push(err(&format!("{}/layout/rows/{}/{}", prefix, ri, ki), "键位必须是字符串")),
                }
            }
        }
    }
}

fn validate_key_binding(prefix: &str, binding: &Value, errors: &mut Vec<ValidationError>, warnings: &mut Vec<String>) {
    let Some(obj) = binding.as_object() else {
        errors.push(err(prefix, "按键定义必须是映射"));
        return;
    };

    // Xime 3.0：tap / double_tap / 四向滑动都是手势，long_press 单独是 {display, values}
    for gesture in GESTURE_FIELDS.iter().filter(|g| **g != "long_press") {
        if let Some(v) = obj.get(*gesture) {
            validate_gesture(&format!("{}/{}", prefix, gesture), v, errors, warnings);
        }
    }

    // 键级可选 width：列宽比例（数字）
    if let Some(w) = obj.get("width") {
        match w.as_f64() {
            Some(n) if n > 0.0 && n <= 10.0 => {}
            _ => errors.push(err(&format!("{}/width", prefix), "width 必须是 0~10 之间的数字（列宽比例）")),
        }
    }

    if let Some(lp) = obj.get("long_press") {
        match lp {
            Value::Array(_) => {
                warn(warnings, format!("{}：long_press 建议使用 {{display, values}} 对象写法", prefix));
                validate_long_press_values(prefix, &Value::Object(
                    [("values".to_string(), lp.clone())].into_iter().collect(),
                ), errors, warnings);
            }
            Value::Object(_) => validate_long_press_values(prefix, lp, errors, warnings),
            _ => errors.push(err(&format!("{}/long_press", prefix), "long_press 必须是数组或对象")),
        }
    }

    // 未知手势字段提示（如 2.x 遗留的 when_composing / sticky / repeat）
    for k in obj.keys() {
        if !GESTURE_FIELDS.contains(&k.as_str()) && k != "width" {
            warn(warnings, format!("{}/{} 是未知的按键字段（Xime 3.0 已移除 when_composing/sticky/repeat 等旧字段）", prefix, k));
        }
    }
}

fn validate_long_press_values(prefix: &str, lp: &Value, errors: &mut Vec<ValidationError>, warnings: &mut Vec<String>) {
    if let Some(d) = lp.get("display") {
        match d.as_str() {
            Some(s) if DISPLAY_MODES.contains(&s) => {}
            Some(s) => errors.push(err(&format!("{}/long_press/display", prefix), format!("未知 display 模式: {}", s))),
            None => errors.push(err(&format!("{}/long_press/display", prefix), "display 必须是字符串")),
        }
    }
    if let Some(values) = lp.get("values").and_then(Value::as_array) {
        if values.len() > 10 {
            warn(warnings, format!("{}/long_press/values 超过 10 项，超出部分会被忽略", prefix));
        }
        for (i, v) in values.iter().enumerate() {
            validate_gesture(&format!("{}/long_press/values/{}", prefix, i), v, errors, warnings);
        }
    }
}

fn validate_gesture(path: &str, gesture: &Value, errors: &mut Vec<ValidationError>, warnings: &mut Vec<String>) {
    match gesture {
        // 简单字符串 = 按槽位默认动作（tap 为 send_rime，swipe/长按为 commit），显示也是它
        Value::String(_) => {}
        Value::Object(map) => {
            // use 引用 keyboard.actions 预设：其余字段被忽略（由调用方校验预设存在性）
            if let Some(use_name) = map.get("use").and_then(Value::as_str) {
                if use_name.is_empty() {
                    errors.push(err(path, "use 不能为空（引用 keyboard.actions 预设名）"));
                }
                return;
            }
            if let Some(label) = map.get("label") {
                // label 支持字符串或字符串数组（数组按多行显示，解析为 join("\n")）
                match label {
                    Value::String(_) => {}
                    Value::Array(items) if items.iter().all(|i| i.is_string()) => {}
                    _ => errors.push(err(path, "label 必须是字符串或字符串数组（多行显示）")),
                }
            }
            if let Some(icon) = map.get("icon") {
                if !icon.is_string() {
                    errors.push(err(path, "icon 必须是字符串"));
                }
            }
            // repeat：长按项按住连发标记（如 delete 长按重复删）
            if let Some(r) = map.get("repeat") {
                if !r.is_boolean() {
                    errors.push(err(path, "repeat 必须是布尔值（长按连发标记）"));
                }
            }
            if let Some(action) = map.get("action") {
                match action.as_str() {
                    Some(a) if GESTURE_ACTIONS.contains(&a) => {
                        match a {
                            "command" => match map.get("value").and_then(Value::as_str) {
                                Some(v) if COMMAND_VALUES.contains(&v) => {}
                                Some(v) => warn(warnings, format!("{}：未知 command 值 {}（内置命令: {}）", path, v, COMMAND_VALUES.join(", "))),
                                None => errors.push(err(path, "action=command 时必须提供 value 命令名")),
                            },
                            "switch_route" => match map.get("value").and_then(Value::as_str) {
                                Some(v) if SWITCH_ROUTE_VALUES.contains(&v) => {}
                                Some(v) => warn(warnings, format!("{}：未知 switch_route 值 {}（可选 {}）", path, v, SWITCH_ROUTE_VALUES.join(", "))),
                                None => warn(warnings, format!("{}：switch_route 未指定 value 面板", path)),
                            },
                            "repeat_space" => {
                                if let Some(v) = map.get("value") {
                                    if v.as_f64().is_none() && v.as_str().map(|s| s.parse::<i64>().is_ok()) != Some(true) {
                                        errors.push(err(path, "repeat_space 的 value 必须是数字（次数，默认 5）"));
                                    }
                                }
                            }
                            _ => {}
                        }
                    }
                    // Xime 对未知动作按“仅显示不执行”降级处理，因此这里给警告而非错误
                    Some(a) => warn(warnings, format!("{}：未知动作 {}（可选 {}），该手势将只显示不执行", path, a, GESTURE_ACTIONS.join("/"))),
                    None => errors.push(err(path, "action 必须是字符串")),
                }
            }
            if let Some(d) = map.get("display") {
                match d.as_str() {
                    Some(s) if DISPLAY_MODES.contains(&s) => {}
                    Some(s) => errors.push(err(path, format!("未知 display 模式: {}（可选 key/bubble/both）", s))),
                    None => errors.push(err(path, "display 必须是字符串")),
                }
            }
            // bubble：独立控制运行时是否弹气泡（默认 true），与 display 互不影响
            if let Some(b) = map.get("bubble") {
                if !b.is_boolean() {
                    errors.push(err(path, "bubble 必须是布尔值"));
                }
            }
        }
        _ => errors.push(err(path, "手势必须是字符串或 {label, action, value, display} 对象")),
    }
}
