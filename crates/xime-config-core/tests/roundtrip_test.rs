use std::fs;
use std::path::Path;
use xime_config_core::model::*;
use xime_config_core::parser;

const SAMPLES_DIR: &str = "../../Xime/docs/config_examples";

const SAMPLE_YAML: &str = r#"metadata:
  app_name: Xime
  app_version: ">=2.5.0"
  platform: android
  config_version: 1
  modified_time: "2026-07-05"

style:
  color_scheme: lavender_purple

keyboard:
  key:
    corner_radius: 8
  shadow:
    enabled: true
    elevation: 1
  qwerty:
    button_layout: standard
    keys:
      q: { tap: "q", swipe_up: "1", long_press: { display: "bubble", values: ["q", "Q"] } }
      a: { tap: "a", swipe_up: { label: "～", value: "~" }, long_press: { display: "bubble", values: ["a", "A"] } }
      shift_l: { tap: { label: "英", action: "toggle_ascii" } }
"#;

#[test]
fn test_parse_and_roundtrip() {
    let config: XimeConfig = parser::parse_yaml(SAMPLE_YAML).unwrap();
    assert_eq!(config.metadata.app_name, "Xime");
    let keyboard = config.keyboard.as_ref().unwrap();
    let qwerty = keyboard.qwerty.as_ref().unwrap();
    let keys = qwerty.keys.as_ref().unwrap();

    let key_q = keys.key_q.as_ref().unwrap();
    match key_q.tap.as_ref().unwrap() {
        GestureAction::Simple(s) => assert_eq!(s, "q"),
        _ => panic!("expected simple gesture"),
    }

    let key_a = keys.key_a.as_ref().unwrap();
    match key_a.swipe_up.as_ref().unwrap() {
        GestureAction::Detailed { ref value, .. } => {
            assert_eq!(value.as_ref().unwrap(), "~");
        }
        _ => panic!("expected detailed gesture"),
    }

    let key_shift = keys.key_shift_l.as_ref().unwrap();
    match key_shift.tap.as_ref().unwrap() {
        GestureAction::Detailed { ref label, ref action, .. } => {
            assert_eq!(label.as_ref().unwrap(), "英");
            assert_eq!(action.as_ref().unwrap(), "toggle_ascii");
        }
        _ => panic!("expected detailed gesture"),
    }

    let yaml_out = parser::to_yaml(&config).unwrap();
    assert!(!yaml_out.is_empty());
    let config2: XimeConfig = parser::parse_yaml(&yaml_out).unwrap();
    assert_eq!(config2.metadata.app_name, "Xime");
}

#[test]
fn test_validation() {
    let config = XimeConfig {
        metadata: Metadata {
            app_name: String::new(),
            app_version: String::new(),
            platform: String::new(),
            ..Default::default()
        },
        xime_index: None,
        style: None,
        color_schemes: None,
        keyboard: None,
    };
    let result = parser::validate_all_fields(&config);
    let app_name_err = result.iter().find(|e| e.path == "metadata.app_name");
    assert!(app_name_err.is_some(), "expected validation error for empty app_name");
}

#[test]
fn test_parse_all_examples() {
    let sample_dir = Path::new(SAMPLES_DIR);
    if !sample_dir.exists() {
        eprintln!("Samples dir {} not found, skipping", SAMPLES_DIR);
        return;
    }

    let files = [
        "xime.cangjie.yaml",
        "xime.flypy.yaml",
        "xime.full_example.yaml",
        "xime.shortcut.yaml",
        "xime.theme_example.yaml",
        "xime.wubi_compact.yaml",
    ];

    for fname in &files {
        let path = sample_dir.join(fname);
        let yaml_str = fs::read_to_string(&path)
            .unwrap_or_else(|e| panic!("Failed to read {}: {}", path.display(), e));

        let config: XimeConfig = parser::parse_yaml(&yaml_str)
            .unwrap_or_else(|e| panic!("Failed to parse {}: {}", fname, e));

        assert!(!config.metadata.app_name.is_empty(), "{}: app_name empty", fname);

        // Verify round-trip
        let yaml_out = parser::to_yaml(&config).unwrap();
        assert!(!yaml_out.is_empty(), "{}: to_yaml produced empty output", fname);

        let _config2: XimeConfig = parser::parse_yaml(&yaml_out)
            .unwrap_or_else(|e| panic!("{}: round-trip parse failed: {}", fname, e));
    }
}
