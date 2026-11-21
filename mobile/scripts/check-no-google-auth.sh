#!/bin/sh
# 守門:EasyYTDL Lite 不得含 Google 授權函式庫(split-lite-and-list-sources design D7)。
# CI(.github/workflows/mobile-release.yml)與本機執行的是同一支。
#
# 用法:
#   sh mobile/scripts/check-no-google-auth.sh <build.gradle> [APK 檔]
#   sh mobile/scripts/check-no-google-auth.sh --self-test     # 對照組:乾淨與違規的替身輸入,答案必須不同
#
# 檢查兩處:
#   1. build.gradle(先去掉 // 註解)不得出現 play-services-auth;
#   2. APK 內每個 classes*.dex 不得出現 com/google/android/gms/auth/ 的類別。
# 每處都先找已知必然存在的錨點(youtubedl-android),找不到就失敗:看不到錨點代表掃描本身壞了,0 命中不能當證據。
# 射程:只看 build.gradle 的相依宣告與 dex 內的類別名稱字串;以反射、字串拼出的類別名稱看不到。
#
# 退出碼:0 通過;1 找到違規或錨點不在;2 用法或工具問題。
set -eu

GRADLE_FORBIDDEN='play-services-auth'
GRADLE_ANCHOR='youtubedl-android:library'
DEX_FORBIDDEN='Lcom/google/android/gms/auth/'
DEX_ANCHOR='Lcom/yausername/youtubedl_android/'

check_gradle() {
  f=$1
  [ -f "$f" ] || { echo "[AUTH-USAGE] 找不到 $f"; return 2; }
  body=$(sed 's#//.*$##' "$f")
  if ! printf '%s\n' "$body" | grep -q "$GRADLE_ANCHOR"; then
    echo "[AUTH-ANCHOR] $f 裡找不到錨點 $GRADLE_ANCHOR(掃描對象不對?)"
    return 1
  fi
  if printf '%s\n' "$body" | grep -n "$GRADLE_FORBIDDEN"; then
    echo "[AUTH-GRADLE] $f 宣告了 $GRADLE_FORBIDDEN"
    return 1
  fi
  echo "[AUTH-GRADLE] ok:$f 沒有 $GRADLE_FORBIDDEN"
}

check_apk() {
  apk=$1
  [ -f "$apk" ] || { echo "[AUTH-USAGE] 找不到 $apk"; return 2; }
  dexes=$(unzip -Z1 "$apk" | grep -E '^classes[0-9]*\.dex$' || true)
  [ -n "$dexes" ] || { echo "[AUTH-ANCHOR] $apk 裡沒有任何 classes*.dex"; return 1; }
  anchor=0
  for d in $dexes; do
    if unzip -p "$apk" "$d" | grep -a -q "$DEX_ANCHOR"; then anchor=1; fi
    if unzip -p "$apk" "$d" | grep -a -q "$DEX_FORBIDDEN"; then
      echo "[AUTH-APK] $apk 的 $d 含有 $DEX_FORBIDDEN 的類別"
      return 1
    fi
  done
  [ "$anchor" = 1 ] || { echo "[AUTH-ANCHOR] $apk 的 dex 裡找不到錨點 $DEX_ANCHOR(掃描對象不對?)"; return 1; }
  echo "[AUTH-APK] ok:$apk 沒有 $DEX_FORBIDDEN"
}

self_test() {
  tmp=$(mktemp -d)
  trap 'rm -rf "$tmp"' EXIT
  fail=0
  expect() { # expect <預期退出碼> <說明> <命令...>
    want=$1; what=$2; shift 2
    set +e; "$@" > "$tmp/out" 2>&1; got=$?; set -e
    if [ "$got" = "$want" ]; then echo "  ok   $what(退出碼 $got)"; else echo "  FAIL $what:預期 $want,實際 $got"; cat "$tmp/out"; fail=1; fi
  }
  printf "dependencies {\n    implementation 'io.github.junkfood02.youtubedl-android:library:0.18.1'\n}\n" > "$tmp/clean.gradle"
  { cat "$tmp/clean.gradle"; echo "dependencies { implementation 'com.google.android.gms:play-services-auth:22.0.0' }"; } > "$tmp/bad.gradle"
  { cat "$tmp/clean.gradle"; echo "// 舊版用過 com.google.android.gms:play-services-auth"; } > "$tmp/comment.gradle"
  printf "dependencies {\n}\n" > "$tmp/noanchor.gradle"
  expect 0 "乾淨的 build.gradle" check_gradle "$tmp/clean.gradle"
  expect 1 "宣告 play-services-auth" check_gradle "$tmp/bad.gradle"
  expect 0 "只在註解裡提到" check_gradle "$tmp/comment.gradle"
  expect 1 "沒有錨點" check_gradle "$tmp/noanchor.gradle"

  mkdir -p "$tmp/clean" "$tmp/bad" "$tmp/noanchor"
  printf 'dex\n035 Lcom/yausername/youtubedl_android/YoutubeDL;' > "$tmp/clean/classes.dex"
  cp "$tmp/clean/classes.dex" "$tmp/bad/classes.dex"
  printf 'dex\n035 Lcom/google/android/gms/auth/api/identity/AuthorizationClient;' > "$tmp/bad/classes2.dex"
  printf 'dex\n035 Lkotlin/Unit;' > "$tmp/noanchor/classes.dex"
  # 替身 APK:有 zip 用 zip,沒有就用 JDK 的 jar(Windows 的 Git Bash 沒有 zip)。
  for k in clean bad noanchor; do
    if command -v zip >/dev/null 2>&1; then (cd "$tmp/$k" && zip -q "../$k.apk" classes*.dex)
    else (cd "$tmp/$k" && jar cfM "../$k.apk" classes*.dex); fi
  done
  expect 0 "乾淨的 APK" check_apk "$tmp/clean.apk"
  expect 1 "第二個 dex 含授權類別" check_apk "$tmp/bad.apk"
  expect 1 "dex 沒有錨點" check_apk "$tmp/noanchor.apk"
  [ "$fail" = 0 ] && echo "[AUTH-SELFTEST] 全部相符" || echo "[AUTH-SELFTEST] 有不相符的列"
  return "$fail"
}

case "${1:-}" in
  --self-test) self_test ;;
  '') echo "[AUTH-USAGE] usage: check-no-google-auth.sh <build.gradle> [apk] | --self-test"; exit 2 ;;
  *)
    check_gradle "$1"
    if [ -n "${2:-}" ]; then check_apk "$2"; fi
    ;;
esac
