#!/usr/bin/env bash
# 构建 Android 端 Rust 核心，产出 app/src/main/jniLibs/<abi>/libxime_config_core.so
# 用法:
#   scripts/build-native.sh            # release
#   scripts/build-native.sh debug      # debug
#
# 环境要求: rustup(含 aarch64-linux-android/armv7-linux-androideabi/x86_64-linux-android
#           三个 target)、cargo-ndk、Android NDK。
# 工具链缺失时跳过构建（Gradle 会警告），已有产物则继续可用。
set -euo pipefail
cd "$(dirname "$0")/.."

PROFILE="${1:-release}"
CRATE="xime-config-core"
OUT_DIR="app/src/main/jniLibs"

# ── 定位 cargo / cargo-ndk（优先使用工具链内的真实二进制，规避 rustup 代理 argv0 问题）──
TOOLCHAIN_BIN="$(ls -d "$HOME"/.rustup/toolchains/*/bin 2>/dev/null | sort | tail -1)"
if [ -z "$TOOLCHAIN_BIN" ] || [ ! -x "$TOOLCHAIN_BIN/cargo" ]; then
  echo "[build-native] 警告: 未找到 Rust 工具链，跳过 native 构建（app 将缺少 .so，运行时会显示引擎错误）"
  exit 0
fi
export PATH="$TOOLCHAIN_BIN:$HOME/.cargo/bin:$PATH"
command -v cargo-ndk >/dev/null 2>&1 || {
  echo "[build-native] 警告: 未安装 cargo-ndk（cargo install cargo-ndk），跳过 native 构建"
  exit 0
}

# ── 检查 Android 交叉编译 target ──
for t in aarch64-linux-android armv7-linux-androideabi x86_64-linux-android; do
  if ! rustup target list --installed 2>/dev/null | grep -q "$t"; then
    echo "[build-native] 警告: 缺少 Rust target $t（rustup target add $t），跳过 native 构建"
    exit 0
  fi
done
# ── 定位 NDK：优先 ANDROID_NDK_HOME，否则扫描 SDK/ndk 下已完整安装的最大版本 ──
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
if [ -n "${ANDROID_NDK_HOME:-}" ] && [ -f "$ANDROID_NDK_HOME/source.properties" ]; then
  NDK="$ANDROID_NDK_HOME"
else
  NDK=""
  for d in "$SDK"/ndk/*/; do
    [ -f "$d/source.properties" ] || continue   # 跳过失败的空壳安装
    NDK="${d%/}"
  done
  [ -n "$NDK" ] || { echo "[build-native] 警告: 在 $SDK/ndk 未找到完整安装的 NDK，跳过 native 构建"; exit 0; }
fi
export ANDROID_NDK_HOME="$NDK"
echo "[build-native] NDK: $NDK"
echo "[build-native] profile: $PROFILE"

mkdir -p "$OUT_DIR"
# 只构建 arm64-v8a（真机目标）；需要模拟器调试时用 ANDROID_ABIS="arm64-v8a x86_64" 覆盖
ABIS="${ANDROID_ABIS:-arm64-v8a}"
ARGS=()
for abi in $ABIS; do ARGS+=(-t "$abi"); done
cargo ndk "${ARGS[@]}" --platform 28 -o "$OUT_DIR" \
  build --"$PROFILE" -p "$CRATE"

echo "[build-native] 完成:"
find "$OUT_DIR" -name "libxime_config_core.so" -exec ls -la {} \;
