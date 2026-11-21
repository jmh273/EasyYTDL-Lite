#!/bin/sh
# 驗證「APK 檔本身」:版號、versionCode、簽章憑證的 SHA-256。
# CI(.github/workflows/mobile-release.yml)與本機執行的是同一支,改這裡就是同時改兩邊。
#
# 用法(期望值一律由環境變數給,缺任何一項都失敗,不會當成「不檢查」):
#   EXPECTED_VERSION=<版號> EXPECTED_VERSION_CODE=<versionCode> \
#   EXPECTED_FINGERPRINT=<憑證 SHA-256,冒號與大小寫皆可> \
#   sh mobile/scripts/verify-apk.sh <APK 檔路徑>
#
# 退出碼:0 全部通過;1 某道閘門不通過(訊息以 [APK-...] 開頭,指名是哪一項);2 用法或工具問題。
#
# 驗證對象 MUST 是 APK 檔本身,MUST NOT 改成 dist/、原始碼字串或 build.gradle。
# aapt2 與 apksigner 住在 Android SDK 的 build-tools 目錄,那個目錄通常不在 PATH 上;
# 找不到時依 ANDROID_SDK_ROOT / ANDROID_HOME 取版本號最大的一個。
set -eu

APK="${1:-}"
if [ -z "$APK" ]; then
  echo "[APK-USAGE] usage: verify-apk.sh <apk-file> (expected values come from environment variables)"
  exit 2
fi
if [ ! -f "$APK" ]; then
  echo "[APK-FILE] expected APK file not found: $APK"
  exit 1
fi

# 期望值未設定時 MUST 失敗:空字串會讓任何比對都通過,閘門就形同不存在。
if [ -z "${EXPECTED_VERSION:-}" ]; then
  echo "[APK-VERSION] EXPECTED_VERSION is not set; refusing to verify against an empty expectation."
  exit 1
fi
if [ -z "${EXPECTED_VERSION_CODE:-}" ]; then
  echo "[APK-VERSION] EXPECTED_VERSION_CODE is not set; refusing to verify against an empty expectation."
  exit 1
fi
if [ -z "${EXPECTED_FINGERPRINT:-}" ]; then
  echo "[APK-FINGERPRINT] EXPECTED_FINGERPRINT is not set (repository variable MOBILE_KEYSTORE_FINGERPRINT in CI)."
  echo "Without it this gate cannot tell the release key from a debug or throwaway key."
  exit 1
fi

# 找工具:先看 PATH,再看 SDK 的 build-tools。Windows 上的副檔名是 .exe / .bat。
BUILD_TOOLS_PATH=""
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [ -n "$SDK_ROOT" ] && [ -d "$SDK_ROOT/build-tools" ]; then
  # sort -V 依版本序而非字典序,才不會把低位數的大版本排在後面。目錄名是版本字串,ls 在這裡是安全的。
  # shellcheck disable=SC2012
  BUILD_TOOLS_DIR=$(ls -1 "$SDK_ROOT/build-tools" | sort -V | tail -n 1)
  BUILD_TOOLS_PATH="$SDK_ROOT/build-tools/$BUILD_TOOLS_DIR"
fi

find_tool() {
  for candidate in "$1" "$1.exe" "$1.bat"; do
    if command -v "$candidate" >/dev/null 2>&1; then
      command -v "$candidate"
      return 0
    fi
    if [ -n "$BUILD_TOOLS_PATH" ] && [ -f "$BUILD_TOOLS_PATH/$candidate" ]; then
      echo "$BUILD_TOOLS_PATH/$candidate"
      return 0
    fi
  done
  return 1
}

if ! AAPT2=$(find_tool aapt2); then
  echo "[APK-TOOLS] aapt2 not found on PATH or under the SDK build-tools (ANDROID_SDK_ROOT / ANDROID_HOME)."
  exit 2
fi
if ! APKSIGNER=$(find_tool apksigner); then
  echo "[APK-TOOLS] apksigner not found on PATH or under the SDK build-tools (ANDROID_SDK_ROOT / ANDROID_HOME)."
  exit 2
fi

# ---- 版號與 versionCode:讀 APK 內的 badging ----
BADGING=$("$AAPT2" dump badging "$APK" | tr -d '\r')
VERSION_NAME=$(echo "$BADGING" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p" | head -n 1)
VERSION_CODE=$(echo "$BADGING" | sed -n "s/.*versionCode='\([^']*\)'.*/\1/p" | head -n 1)

if [ -z "$VERSION_NAME" ] || [ -z "$VERSION_CODE" ]; then
  echo "[APK-VERSION] could not read versionName/versionCode from the APK badging output."
  echo "$BADGING" | head -n 5
  exit 1
fi
if [ "$VERSION_NAME" != "$EXPECTED_VERSION" ]; then
  echo "[APK-VERSION] APK versionName ($VERSION_NAME) does not match expected version ($EXPECTED_VERSION)"
  exit 1
fi
if [ "$VERSION_CODE" != "$EXPECTED_VERSION_CODE" ]; then
  echo "[APK-VERSION] APK versionCode ($VERSION_CODE) does not match expected ($EXPECTED_VERSION_CODE)"
  exit 1
fi
echo "APK version verification passed: $VERSION_NAME / $VERSION_CODE"

# ---- 簽章憑證:比對憑證的 SHA-256 ----
# 只取「certificate SHA-256 digest」那一行。整段 grep 會誤命中「public key SHA-256 digest」,那是另一個值。
if ! CERTS=$("$APKSIGNER" verify --print-certs "$APK" | tr -d '\r'); then
  echo "[APK-FINGERPRINT] apksigner could not verify the APK signature (unsigned or corrupted APK)."
  exit 1
fi
echo "$CERTS"
ACTUAL=$(echo "$CERTS" | grep -i "certificate SHA-256 digest" | head -n 1 | awk '{print $NF}')
NORM_ACTUAL=$(printf '%s' "$ACTUAL" | tr -d ': ' | tr '[:upper:]' '[:lower:]')
NORM_EXPECTED=$(printf '%s' "$EXPECTED_FINGERPRINT" | tr -d ': ' | tr '[:upper:]' '[:lower:]')

if [ -z "$NORM_ACTUAL" ]; then
  echo "[APK-FINGERPRINT] could not read the certificate SHA-256 digest from apksigner output."
  exit 1
fi
if ! printf '%s' "$NORM_EXPECTED" | grep -Eq '^[0-9a-f]{64}$'; then
  echo "[APK-FINGERPRINT] EXPECTED_FINGERPRINT is not a SHA-256 value (64 hex characters, colons optional)."
  exit 1
fi
if [ "$NORM_ACTUAL" != "$NORM_EXPECTED" ]; then
  echo "[APK-FINGERPRINT] APK signing certificate does not match the expected release key."
  echo "  expected: $NORM_EXPECTED"
  echo "  actual:   $NORM_ACTUAL"
  exit 1
fi
echo "APK signing certificate verification passed."
