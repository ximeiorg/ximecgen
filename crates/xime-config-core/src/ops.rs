use serde_json::Value;

/// 单条配置修改操作。`path` 为 JSON Pointer（RFC 6901，含 ~0/~1 转义），
/// `value` 按调用方给出的 JSON 类型原样写入（颜色等数值由 UI 层保证类型）。
#[derive(Debug, Clone)]
pub struct Op {
    pub op: String,
    pub path: String,
    pub value: Option<Value>,
}

/// 解析 ops JSON 数组：`[{"op":"set","path":"/a/b","value":1}, ...]`
pub fn parse_ops(json: &str) -> Result<Vec<Op>, String> {
    let raw: Value = serde_json::from_str(json).map_err(|e| format!("ops 不是合法 JSON: {}", e))?;
    let items = raw
        .as_array()
        .ok_or_else(|| "ops 必须是 JSON 数组".to_string())?;

    let mut ops = Vec::with_capacity(items.len());
    for (i, item) in items.iter().enumerate() {
        let obj = item
            .as_object()
            .ok_or_else(|| format!("ops[{}] 必须是对象", i))?;
        let op = obj
            .get("op")
            .and_then(Value::as_str)
            .ok_or_else(|| format!("ops[{}] 缺少 op 字段", i))?;
        let path = obj
            .get("path")
            .and_then(Value::as_str)
            .ok_or_else(|| format!("ops[{}] 缺少 path 字段", i))?;
        if !matches!(op, "set" | "add" | "remove") {
            return Err(format!("ops[{}] 不支持的操作类型: {}", i, op));
        }
        ops.push(Op {
            op: op.to_string(),
            path: path.to_string(),
            value: obj.get("value").cloned(),
        });
    }
    Ok(ops)
}

/// 按顺序应用一批操作；任何一步失败即中止并返回错误（调用方应保留原值）。
pub fn apply_ops(root: &mut Value, ops: &[Op]) -> Result<(), String> {
    for (i, op) in ops.iter().enumerate() {
        apply_one(root, op).map_err(|e| format!("ops[{}] ({} {}): {}", i, op.op, op.path, e))?;
    }
    Ok(())
}

fn apply_one(root: &mut Value, op: &Op) -> Result<(), String> {
    if !root.is_object() && !root.is_null() {
        return Err("根节点必须是映射".into());
    }
    if root.is_null() {
        *root = Value::Object(Default::default());
    }

    let tokens = decode_pointer(&op.path)?;
    match op.op.as_str() {
        "set" => do_set(root, &tokens, op.value.clone().unwrap_or(Value::Null)),
        "add" => do_add(root, &tokens, op.value.clone().unwrap_or(Value::Null)),
        "remove" => do_remove(root, &tokens),
        _ => Err(format!("不支持的操作: {}", op.op)),
    }
}

/// 拆分 JSON Pointer："/a/b~1c" -> ["a", "b/c"]；"" 与 "/" 边界已处理。
fn decode_pointer(pointer: &str) -> Result<Vec<String>, String> {
    if pointer.is_empty() {
        return Ok(Vec::new());
    }
    if !pointer.starts_with('/') {
        return Err(format!("path 必须以 / 开头: {}", pointer));
    }
    Ok(pointer[1..]
        .split('/')
        .map(|t| t.replace("~1", "/").replace("~0", "~"))
        .collect())
}

/// 沿 token 走到倒数第二层，缺失的中间容器按“下一级 token 是否为数字下标”创建对象/数组。
fn walk_to_parent<'a>(root: &'a mut Value, tokens: &[String]) -> Result<&'a mut Value, String> {
    let mut current = root;
    for (i, token) in tokens.iter().enumerate() {
        let next_is_index = tokens
            .get(i + 1)
            .map(|t| t.parse::<usize>().is_ok() || t == "-")
            .unwrap_or(false);

        current = match current {
            Value::Object(map) => {
                let entry = map.entry(token.clone()).or_insert_with(|| {
                    if next_is_index {
                        Value::Array(Vec::new())
                    } else {
                        Value::Object(Default::default())
                    }
                });
                if entry.is_null() {
                    *entry = if next_is_index {
                        Value::Array(Vec::new())
                    } else {
                        Value::Object(Default::default())
                    };
                }
                entry
            }
            Value::Array(arr) => {
                let idx = parse_index(token, arr.len())?;
                if idx == arr.len() {
                    arr.push(Value::Null);
                }
                let slot = &mut arr[idx];
                if slot.is_null() {
                    *slot = if next_is_index {
                        Value::Array(Vec::new())
                    } else {
                        Value::Object(Default::default())
                    };
                }
                slot
            }
            other => {
                return Err(format!(
                    "路径冲突：{} 处已是标量 {}，无法继续下钻",
                    token, other
                ))
            }
        };
    }
    Ok(current)
}

fn do_set(root: &mut Value, tokens: &[String], value: Value) -> Result<(), String> {
    let (last, parents) = tokens
        .split_last()
        .ok_or_else(|| "set 不能作用于根节点（请整体替换）".to_string())?;
    let parent = walk_to_parent(root, parents)?;

    match parent {
        Value::Object(map) => {
            map.insert(last.clone(), value);
            Ok(())
        }
        Value::Array(arr) => {
            let idx = parse_index(last, arr.len())?;
            if idx == arr.len() {
                arr.push(value);
            } else {
                arr[idx] = value;
            }
            Ok(())
        }
        Value::Null => Err("父路径为空".into()),
        other => Err(format!("父节点类型不支持 set: {}", other)),
    }
}

fn do_add(root: &mut Value, tokens: &[String], value: Value) -> Result<(), String> {
    let (last, parents) = tokens
        .split_last()
        .ok_or_else(|| "add 不能作用于根节点".to_string())?;
    let parent = walk_to_parent(root, parents)?;

    match parent {
        Value::Object(map) => {
            map.insert(last.clone(), value);
            Ok(())
        }
        Value::Array(arr) => {
            if last == "-" {
                arr.push(value);
                return Ok(());
            }
            let idx = parse_index(last, arr.len())?;
            if idx == arr.len() {
                arr.push(value);
            } else {
                arr.insert(idx, value);
            }
            Ok(())
        }
        Value::Null => Err("父路径为空".into()),
        other => Err(format!("父节点类型不支持 add: {}", other)),
    }
}

fn do_remove(root: &mut Value, tokens: &[String]) -> Result<(), String> {
    let (last, parents) = tokens
        .split_last()
        .ok_or_else(|| "remove 不能作用于根节点".to_string())?;
    let parent = walk_to_parent(root, parents)?;

    match parent {
        Value::Object(map) => {
            map.remove(last);
            Ok(())
        }
        Value::Array(arr) => {
            let idx = parse_index(last, arr.len())?;
            if idx >= arr.len() {
                Err(format!("数组下标越界: {}", idx))
            } else {
                arr.remove(idx);
                Ok(())
            }
        }
        Value::Null => Err("父路径为空".into()),
        other => Err(format!("父节点类型不支持 remove: {}", other)),
    }
}

/// 数组下标：0..=len 合法（== len 表示追加）。
fn parse_index(token: &str, len: usize) -> Result<usize, String> {
    token
        .parse::<usize>()
        .ok()
        .filter(|i| *i <= len)
        .ok_or_else(|| format!("数组下标非法或越界: {}（长度 {}）", token, len))
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    fn run(root: &mut Value, ops_json: &str) -> Result<(), String> {
        let ops = parse_ops(ops_json)?;
        apply_ops(root, &ops)
    }

    #[test]
    fn set_creates_nested_containers() {
        let mut root = json!({});
        run(
            &mut root,
            r#"[{"op":"set","path":"/keyboard/qwerty/keys/q/tap","value":"q"}]"#,
        )
        .unwrap();
        assert_eq!(root["keyboard"]["qwerty"]["keys"]["q"]["tap"], json!("q"));
    }

    #[test]
    fn set_on_scalar_conflict_is_error() {
        let mut root = json!({"style": {"color_scheme": "lavender_purple"}});
        let err = run(
            &mut root,
            r#"[{"op":"set","path":"/style/color_scheme/light","value":"ocean_blue"}]"#,
        )
        .unwrap_err();
        assert!(err.contains("lavender_purple"), "unexpected: {}", err);
        // 先整体替换为对象后即可继续编辑子字段
        run(
            &mut root,
            r#"[{"op":"set","path":"/style/color_scheme","value":{"light":"lavender_purple","dark":"slate_gray"}},
                {"op":"set","path":"/style/color_scheme/light","value":"ocean_blue"}]"#,
        )
        .unwrap();
        assert_eq!(root["style"]["color_scheme"]["light"], json!("ocean_blue"));
    }

    #[test]
    fn set_array_index_replaces_and_appends() {
        let mut root = json!({"rows": ["a", "b"]});
        run(
            &mut root,
            r#"[{"op":"set","path":"/rows/1","value":"x"},{"op":"set","path":"/rows/2","value":"y"}]"#,
        )
        .unwrap();
        assert_eq!(root["rows"], json!(["a", "x", "y"]));
    }

    #[test]
    fn add_inserts_and_pushes() {
        let mut root = json!({"list": ["a"]});
        run(
            &mut root,
            r#"[{"op":"add","path":"/list/0","value":"z"},{"op":"add","path":"/list/-","value":"w"}]"#,
        )
        .unwrap();
        assert_eq!(root["list"], json!(["z", "a", "w"]));
    }

    #[test]
    fn remove_is_tolerant_on_object_strict_on_array() {
        let mut root = json!({"m": {"a": 1}, "l": [1, 2]});
        run(&mut root, r#"[{"op":"remove","path":"/m/missing"}]"#).unwrap();
        assert!(run(&mut root, r#"[{"op":"remove","path":"/l/9"}]"#).is_err());
        run(&mut root, r#"[{"op":"remove","path":"/l/0"}]"#).unwrap();
        assert_eq!(root["l"], json!([2]));
    }

    #[test]
    fn pointer_escapes() {
        let mut root = json!({});
        run(
            &mut root,
            r#"[{"op":"set","path":"/color_schemes/a~1b~0c/name","value":"带斜杠方案"}]"#,
        )
        .unwrap();
        assert_eq!(root["color_schemes"]["a/b~c"]["name"], json!("带斜杠方案"));
    }
}
