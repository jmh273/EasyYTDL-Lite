#!/bin/sh
# 守門:release APK 裡,靠反射建立的類別 MUST 保留原名(R8 改名或改成無法實例化就會在執行期閃退)。
# 來由:mobile-v0.1.0 的 R8 把 commons-compress 的 AsiExtraField 等改名,全新安裝第一次呼叫 yt-dlp
# (解壓內建 Python)就閃退;覆蓋安裝看不出來。規則在 mobile/android/app/proguard-rules.pro。
#
# 用法:sh mobile/scripts/check-r8-keeps.sh <release APK>
# 射程:只確認類別描述字串在 dex 裡;保留了名稱但被刪掉建構子的情形看不到,那要靠全新安裝的實機冒煙。
# 退出碼:0 通過;1 少了某個類別;2 用法或檔案問題。
set -eu

REQUIRED='
Lorg/apache/commons/compress/archivers/zip/ExtraFieldUtils;
Lorg/apache/commons/compress/archivers/zip/AsiExtraField;
Lorg/apache/commons/compress/archivers/zip/ZipFile;
Lcom/yausername/youtubedl_android/YoutubeDL;
'

apk=${1:-}
[ -n "$apk" ] || { echo "[R8-USAGE] usage: check-r8-keeps.sh <release APK>"; exit 2; }
[ -f "$apk" ] || { echo "[R8-USAGE] 找不到 $apk"; exit 2; }
dexes=$(unzip -Z1 "$apk" | grep -E '^classes[0-9]*\.dex$' || true)
[ -n "$dexes" ] || { echo "[R8-KEEP] $apk 裡沒有任何 classes*.dex"; exit 1; }

all=$(for d in $dexes; do unzip -p "$apk" "$d"; done | tr -c '[:print:]' '\n')
fail=0
for c in $REQUIRED; do
  if printf '%s\n' "$all" | grep -q -F "$c"; then echo "  ok   $c"; else echo "  MISS $c"; fail=1; fi
done
if [ "$fail" = 0 ]; then echo "[R8-KEEP] ok:$apk"; else echo "[R8-KEEP] $apk 少了上面標 MISS 的類別(被 R8 改名或刪除)"; fi
exit "$fail"
