use crate::model::{FieldDescriptor, FieldType, ValidationRule};
use serde_json::Value;

#[derive(Debug, thiserror::Error)]
pub enum ConfigError {
    #[error("YAML 解析错误: {0}")]
    YamlParse(String),

    #[error("JSON 解析错误: {0}")]
    JsonParse(String),

    #[error("YAML 序列化错误: {0}")]
    YamlSerialize(String),

    #[error("校验错误: {0}")]
    Validation(String),
}

/// 与 Xime 输入法的 kaml（snakeyaml-engine，YAML 1.2 core schema）对齐的解析选项：
/// 只有 true/false 是布尔，`y`/`n`/`yes`/`no`/`on`/`off` 保持字符串——
/// 布局行里的键位 id `y`、`n` 才不会被误判成布尔值。
fn yaml_options() -> serde_saphyr::Options {
    serde_saphyr::options! {
        strict_booleans: true,
        duplicate_keys: serde_saphyr::DuplicateKeyPolicy::LastWins,
    }
}

/// YAML 文本 → serde_json::Value（保留字段顺序与全部未知字段，无类型化模型丢数据问题）。
pub fn yaml_to_value(yaml_str: &str) -> Result<Value, ConfigError> {
    let value: Value = serde_saphyr::from_str_with_options(yaml_str, yaml_options())
        .map_err(|e| ConfigError::YamlParse(e.to_string()))?;
    match value {
        Value::Object(_) => Ok(value),
        Value::Null => Err(ConfigError::YamlParse("配置为空".into())),
        _ => Err(ConfigError::YamlParse("根节点必须是映射".into())),
    }
}

pub fn yaml_to_json(yaml_str: &str) -> Result<String, ConfigError> {
    let value = yaml_to_value(yaml_str)?;
    serde_json::to_string(&value).map_err(|e| ConfigError::JsonParse(e.to_string()))
}

pub fn json_to_value(json_str: &str) -> Result<Value, ConfigError> {
    serde_json::from_str(json_str).map_err(|e| ConfigError::JsonParse(e.to_string()))
}

/// 配置 JSON → YAML 文本（仅在导入/导出边界调用）。
/// 保持 saphyr 默认 2 空格缩进：其 emitter 在 indent_step≠2 时对嵌套序列
/// （如 layout.rows 的二维数组）会输出错误缩进，只能用默认值保证正确性。
pub fn value_to_yaml(value: &Value) -> Result<String, ConfigError> {
    let mut yaml = serde_saphyr::to_string(value)
        .map_err(|e| ConfigError::YamlSerialize(e.to_string()))?;
    if !yaml.ends_with('\n') {
        yaml.push('\n');
    }
    Ok(yaml)
}

pub fn json_to_yaml(json_str: &str) -> Result<String, ConfigError> {
    let value = json_to_value(json_str)?;
    value_to_yaml(&value)
}

/// 通用表单字段描述符。`section` 供 UI 分组；
/// 配色方案内的字段用 `{id}` 占位，UI 渲染时替换为具体方案 id。
pub fn get_field_descriptors() -> Vec<FieldDescriptor> {
    use FieldType::{Boolean, Color, Number, Select, StringList, Text};

    let mut fields = vec![
        // ── 元数据 ──
        FieldDescriptor::new("/metadata/app_name", "应用名", "metadata", Text, "应用显示名称", "Xime")
            .with_validation(ValidationRule::NonEmpty),
        FieldDescriptor::new("/metadata/app_version", "版本约束", "metadata", Text, "支持的 Xime 版本，支持 >=/<=/>/</^/~ 前缀", ">=2.5.0")
            .with_validation(ValidationRule::Version),
        FieldDescriptor::new("/metadata/platform", "平台", "metadata", Select, "目标平台", "android")
            .with_options(vec!["android".into()]),
        FieldDescriptor::new("/metadata/config_version", "配置版本", "metadata", Number, "配置结构版本号", "1")
            .with_validation(ValidationRule::RangeInt(1, 9999)),
        FieldDescriptor::new("/metadata/generator", "生成器", "metadata", Text, "生成器标识", "Xime Config Generator"),
        FieldDescriptor::new("/metadata/modified_time", "修改时间", "metadata", Text, "YYYY-MM-DD", ""),
        // ── 样式 ──
        FieldDescriptor::new("/style/dark_mode", "显示模式", "style", Select, "0=浅色, 1=深色, 2=跟随系统", "2")
            .with_options(vec!["0".into(), "1".into(), "2".into()]),
        FieldDescriptor::new("/style/color_scheme/light", "浅色主题", "style", Text, "内置主题 id / color_schemes 中的 id / dynamic", "lavender_purple"),
        FieldDescriptor::new("/style/color_scheme/dark", "深色主题", "style", Text, "深色模式下使用的主题 id", "slate_gray"),
    ];

    // ── 键盘颜色（12 项）──
    let colors: &[(&str, &str, &str)] = &[
        ("key_bg_color", "按键底色（浅色）", "0xFFFFFF"),
        ("key_bg_color_dark", "按键底色（深色）", "0x60FFFFFF"),
        ("special_key_bg_color", "特殊键底色（浅色）", ""),
        ("special_key_bg_color_dark", "特殊键底色（深色）", ""),
        ("candidate_bar_bg_color", "候选栏底色（浅色）", ""),
        ("candidate_bar_bg_color_dark", "候选栏底色（深色）", ""),
        ("key_text_color", "按键文字色（浅色）", "0x202124"),
        ("key_text_color_dark", "按键文字色（深色）", "0xE8EAED"),
        ("candidate_text_color", "候选文字色（浅色）", "0x202124"),
        ("candidate_text_color_dark", "候选文字色（深色）", "0xE8EAED"),
        ("keyboard_bg_color", "键盘背景后备色（浅色）", ""),
        ("keyboard_bg_color_dark", "键盘背景后备色（深色）", ""),
    ];
    for (name, label, default) in colors {
        fields.push(
            FieldDescriptor::new(
                &format!("/keyboard/colors/{}", name),
                label,
                "keyboard_colors",
                Color,
                "0xRRGGBB 或带 alpha 的 0xAARRGGBB",
                default,
            )
            .with_validation(ValidationRule::Color),
        );
    }

    // ── 按键形状 ──
    fields.push(
        FieldDescriptor::new("/keyboard/key/corner_radius", "按键圆角半径 (dp)", "keyboard_key", Number, "按键圆角，默认 8", "8")
            .with_validation(ValidationRule::RangeInt(0, 20)),
    );
    fields.push(
        FieldDescriptor::new("/keyboard/key/spacing_x", "按键横向间距 (dp)", "keyboard_key", Number, "默认 2", "2")
            .with_validation(ValidationRule::RangeFloat(0.0, 8.0)),
    );
    fields.push(
        FieldDescriptor::new("/keyboard/key/spacing_y", "按键纵向间距 (dp)", "keyboard_key", Number, "默认 4.25，横屏 2", "4.25")
            .with_validation(ValidationRule::RangeFloat(0.0, 10.0)),
    );

    // ── 阴影 ──
    fields.push(
        FieldDescriptor::new("/keyboard/shadow/enabled", "启用按键阴影", "keyboard_shadow", Boolean, "是否绘制按键阴影", "true"),
    );
    fields.push(
        FieldDescriptor::new("/keyboard/shadow/elevation", "阴影高度 (dp)", "keyboard_shadow", Number, "默认 0.5", "0.5")
            .with_validation(ValidationRule::RangeFloat(0.0, 16.0)),
    );

    // ── 字体 ──
    let fonts: &[(&str, &str)] = &[
        ("key_font", "按键字体"),
        ("key_label_font", "字根标签字体"),
        ("candidate_font", "候选字体"),
        ("comment_font", "注释字体"),
    ];
    for (name, label) in fonts {
        fields.push(FieldDescriptor::new(
            &format!("/keyboard/fonts/{}", name),
            label,
            "keyboard_fonts",
            Text,
            "空 = 系统默认；相对路径基于 rime/ 目录",
            "",
        ));
    }

    // ── 布局 ──
    fields.push(
        FieldDescriptor::new("/keyboard/qwerty/button_layout", "中文 26 键布局模式", "keyboard_layout", Select, "standard=主文字居中，compact=主文字左上", "standard")
            .with_options(vec!["standard".into(), "compact".into()]),
    );
    fields.push(
        FieldDescriptor::new("/keyboard/qwerty_en/button_layout", "英文 26 键布局模式", "keyboard_layout", Select, "standard=主文字居中，compact=主文字左上", "standard")
            .with_options(vec!["standard".into(), "compact".into()]),
    );
    fields.push(
        FieldDescriptor::new("/keyboard/t9/side_symbols", "九键左侧快捷符号", "keyboard_layout", StringList, "长度不限，超过 4 个可滚动", ""),
    );

    // ── 配色方案字段（{id} 由 UI 替换为具体方案 id）──
    fields.push(FieldDescriptor::new("/color_schemes/{id}/name", "方案名称", "color_scheme", Text, "主题显示名", "")
        .with_validation(ValidationRule::NonEmpty));
    fields.push(FieldDescriptor::new("/color_schemes/{id}/dynamic_color", "动态配色", "color_scheme", Boolean, "开启后忽略静态颜色，随壁纸取色（Android 12+）", "false"));
    let scheme_colors: &[(&str, &str)] = &[
        ("primary_color", "主题强调色"),
        ("keyboard_bg_color", "键盘背景后备色"),
        ("key_bg_color", "按键底色（浅色）"),
        ("key_bg_color_dark", "按键底色（深色）"),
        ("special_key_bg_color", "特殊键底色（浅色）"),
        ("special_key_bg_color_dark", "特殊键底色（深色）"),
        ("candidate_bar_bg_color", "候选栏底色"),
        ("key_text_color", "按键文字色（浅色）"),
        ("key_text_color_dark", "按键文字色（深色）"),
        ("candidate_text_color", "候选文字色（浅色）"),
        ("candidate_text_color_dark", "候选文字色（深色）"),
        ("candidate_selected_text_color", "候选选中文字色（浅色）"),
        ("candidate_selected_text_color_dark", "候选选中文字色（深色）"),
    ];
    for (name, label) in scheme_colors {
        fields.push(
            FieldDescriptor::new(
                &format!("/color_schemes/{{id}}/{}", name),
                label,
                "color_scheme",
                Color,
                "0xRRGGBB 或带 alpha 的 0xAARRGGBB",
                "",
            )
            .with_validation(ValidationRule::Color),
        );
    }

    fields
}
