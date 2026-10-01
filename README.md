# Xime Config Generator (ximecgen)

可视化编辑 [Xime 输入法](https://github.com/ximeiorg/Xime) `xime.yaml` 配置文件的跨平台工具，支持 **Android** 和 **Web** 双平台。

## 功能

- **文件管理**：基于网络仓库最新模板新建（离线回退内置副本）、SAF 打开/保存 `xime.custom.yaml`、最近文件
- **编辑器**（四个分区）：
  - 常规：metadata、显示模式（浅色/深色/跟随系统）、主题引用、市场索引端点
  - 主题配色：`color_schemes` 增删复制、13 组颜色（含深色变体）、solid/gradient/image 背景
  - 键盘外观：全局后备色、按键圆角/间距、阴影、字体
  - 布局与手势：3 行布局编辑、每键 tap/上滑/下滑/长按手势、九键快捷符号
- **实时键盘预览**：布局权重/配色/圆角/阴影对齐 Xime 真实渲染；悬浮球 + 预览浮层（窄屏）、双栏自适应（宽屏）、全屏预览页；点按键直接编辑手势
- **校验**：颜色/布局/手势动作/URL 等结构校验（未知动作按 Xime 行为降级为提示）

## 架构

```
yaml(SAF/模板) ──► Rust parse ──► configJson（唯一状态源）
                                      │
      编辑（表单/取色/手势/布局） ─────┤► Rust applyOps（JSON Pointer 批量操作）
                                      ▼
                            Rust validate ──► 校验面板
                                      │
      保存/导出 ◄── Rust to_yaml ◄────┘
```

- **Rust 核心**（`crates/xime-config-core`）以 `serde_json::Value` 动态树承载配置：保留字段顺序、
  不丢未知字段、`y`/`n` 等键位按 YAML 1.2 core schema 保持字符串（与 Xime 的 kaml 解析一致）
- Android (JNI) 与 Web (WASM) 共享同一核心，UI 各自原生实现
- 刻意**不保留** Kotlin/JS fallback 解析器：native 加载失败时 UI 显示明确错误，避免静默行为不一致

## 构建

### Rust 公共核心

```bash
cargo test -p xime-config-core
```

测试使用 `crates/xime-config-core/tests/samples/` 下的 8 份真实配置（7 份来自
Xime `docs/config_examples` + 默认 `xime.yaml`）做语义等价往返回归。

### Web (WASM)

```bash
cd web
npm run wasm     # wasm-pack build（需要 wasm-pack；离线环境已在 Cargo.toml 关闭 wasm-opt）
npm install
npm run dev      # 或 npm run build
```

### Android (JNI)

环境要求：Rust（targets: `aarch64-linux-android`、`armv7-linux-androideabi`、`x86_64-linux-android`）、
`cargo install cargo-ndk`、Android NDK（完整安装，含 `source.properties`）。

```bash
./gradlew assembleDebug
# preBuild 钩子自动执行 scripts/build-native.sh（三个 ABI 的 libxime_config_core.so 输出到 app/src/main/jniLibs）
# 工具链缺失时脚本跳过构建并警告；已有 .so 产物可直接复用
```

### Release APK（CI 构建）

推送 `v*` 标签（如 `v1.0.1`）或手动触发 workflow（Actions → Release APK → Run workflow），
GitHub Actions 自动完成：Rust 交叉编译 → Gradle release 构建（R8 混淆、arm64-v8a）→
上传 artifact 并附带到 GitHub Release。产物命名 `ximecgen-<版本>-arm64-v8a.apk`。

**签名（可选但推荐）**：在仓库 Settings → Secrets and variables → Actions 配置四个 secret：

| Secret | 说明 |
|---|---|
| `KEYSTORE_FILE` | keystore 文件的 base64（`base64 -w0 release.keystore`） |
| `KEYSTORE_PASSWORD` | keystore 密码 |
| `KEY_ALIAS` | 别名 |
| `KEY_PASSWORD` | key 密码 |

配置后 CI 产物为已签名 APK，可直接安装；未配置时产出未签名 APK，需手动签名：

```bash
# 本地生成发布 keystore
keytool -genkeypair -v -keystore release.keystore -storetype PKCS12 \
  -alias ximecgen -keyalg RSA -keysize 2048 -validity 10000

# 本地手动签名未签名产物
apksigner sign --ks release.keystore --out signed.apk ximecgen-1.0.0-arm64-v8a.apk
```

本地构建签名包：`KEYSTORE_FILE=... KEYSTORE_PASSWORD=... KEY_ALIAS=... KEY_PASSWORD=... ./gradlew assembleRelease`
