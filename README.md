# Xime Config Generator (ximecgen)

可视化编辑 `xime.yaml` 配置文件的跨平台工具，支持 **Web** 和 **Android** 双平台。

## 构建

### Rust 公共核心

```bash
cargo test -p xime-config-core
cargo build -p xime-config-core
```

### Web (WASM)

```bash
# 1. 编译 WASM
wasm-pack build crates/xime-config-core --target web --out-dir web/src/pkg --out-name xime_config_core

# 2. 启动 dev server
cd web
npm install
npm run dev
```

### Android (JNI)

```bash
# 1. 编译 native .so（需安装 cargo-ndk）
cargo ndk -t arm64-v8a -o app/src/main/jniLibs build --release -p xime-config-core

# 2. Android Studio 打开项目根目录，直接运行
```
