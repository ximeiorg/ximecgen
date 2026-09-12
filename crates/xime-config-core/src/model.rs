use serde::{Deserialize, Serialize};

/// 统一返回信封：JNI / WASM 绑定层用它承载结果与错误，避免跨语言异常。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ApiResult {
    pub ok: bool,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub data: Option<serde_json::Value>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub error: Option<String>,
}

impl ApiResult {
    pub fn ok(data: serde_json::Value) -> Self {
        Self {
            ok: true,
            data: Some(data),
            error: None,
        }
    }

    pub fn err(message: impl Into<String>) -> Self {
        Self {
            ok: false,
            data: None,
            error: Some(message.into()),
        }
    }

    pub fn to_json(&self) -> String {
        serde_json::to_string(self).unwrap_or_else(|_| r#"{"ok":false,"error":"serialize"}"#.into())
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ValidationResult {
    pub valid: bool,
    pub errors: Vec<ValidationError>,
    pub warnings: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ValidationError {
    pub path: String,
    pub message: String,
    pub severity: String,
}

/// 字段描述符：UI（Android / Web）据此动态渲染通用表单。
/// `path` 为 JSON Pointer（RFC 6901）；配色方案内的字段用 `{id}` 占位方案 id。
#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct FieldDescriptor {
    pub path: String,
    pub label: String,
    #[serde(rename = "section")]
    pub section: String,
    #[serde(rename = "fieldType")]
    pub field_type: FieldType,
    #[serde(default)]
    pub description: String,
    #[serde(default)]
    pub default: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub options: Option<Vec<String>>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub validation: Option<ValidationRule>,
}

impl FieldDescriptor {
    pub fn new(
        path: &str,
        label: &str,
        section: &str,
        field_type: FieldType,
        description: &str,
        default: &str,
    ) -> Self {
        Self {
            path: path.into(),
            label: label.into(),
            section: section.into(),
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

#[derive(Debug, Clone, Copy, Serialize, Deserialize, Default)]
#[serde(rename_all = "lowercase")]
pub enum FieldType {
    #[default]
    Text,
    Number,
    Boolean,
    Select,
    Color,
    StringList,
}

impl std::fmt::Display for FieldType {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            FieldType::Text => write!(f, "text"),
            FieldType::Number => write!(f, "number"),
            FieldType::Boolean => write!(f, "boolean"),
            FieldType::Select => write!(f, "select"),
            FieldType::Color => write!(f, "color"),
            FieldType::StringList => write!(f, "string_list"),
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum ValidationRule {
    NonEmpty,
    Version,
    Color,
    RangeInt(i64, i64),
    RangeFloat(f64, f64),
    MaxLength(usize),
    MaxItems(usize),
}
