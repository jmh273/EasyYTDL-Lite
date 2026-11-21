#!/usr/bin/env bash
# 一次性:建立 release 簽章金鑰,並把 6 個發版用的 secrets / variables 直接設進 GitHub。
#
# 用法(在 repo 根目錄,Git Bash 或一般終端機):
#   bash mobile/scripts/setup-signing.sh [keystore 路徑]
#   bash mobile/scripts/setup-signing.sh --self-test     # 自測:替身 gh + 暫存區的丟棄式金鑰,不碰真實值
# 預設金鑰路徑 $HOME/keys/<PACKAGE_ID>-release.keystore;MUST 在 repo 之外。
#
# 由使用者親手執行:密碼只經由不回顯的輸入、環境變數與管線傳給 keytool / gh,
# 不出現在命令列引數、shell 歷史、畫面或任何檔案。
# MUST NOT 加 set -x。
# 退出碼:0 完成(自測:每列相符);1 失敗(自測:有列不符);2 跑不起來(缺工具、值未渲染)。
set -euo pipefail

# 佔位符只出現在這一段(單引號賦值);渲染前執行會在下面被擋下。
PACKAGE_ID='com.jmh273.easyytdllite'

die() { echo "錯誤:$*" >&2; exit 1; }

# 未渲染就執行:退出碼 2,不是 0。比對只用 '{{' 兩個字元,不寫出完整的佔位符形狀(否則渲染器或殘留檢查會誤判)。
case "$PACKAGE_ID" in
  *'{{'*) echo "錯誤:PACKAGE_ID 尚未渲染(還是範本裡的佔位符)。請先以渲染器生成專案,再執行本腳本。" >&2; exit 2 ;;
esac

KT() { keytool -J-Duser.language=en "$@"; }   # keytool 的輸出依系統語系翻譯,固定成英文才抓得到 SHA256 那一行。

# 替身斷言(自測用):在 PATH 前面加上 $1 之後,每個被替換的命令都 MUST 解析到 $2/<命令>;不成立回非 0。
# 自測啟動任何子行程之前都先過這一關:替身沒蓋住真的 gh,測試值就會寫進真實 repo。
assert_resolves_to_stub() {
  local path_dir=$1 expect_dir=$2 c got
  shift 2
  for c in "$@"; do
    got=$(PATH="$path_dir:$PATH" command -v "$c" 2>/dev/null || true)
    [ "$got" = "$expect_dir/$c" ] || { echo "替身斷言失敗:$c 解析到「${got:-(找不到)}」,預期「$expect_dir/$c」" >&2; return 1; }
  done
}

self_test() {
  set +e
  local c
  for c in keytool git sha256sum base64 cmp grep sed awk; do
    command -v "$c" >/dev/null 2>&1 || { echo "錯誤:自測需要 $c,找不到。" >&2; exit 2; }
  done
  local SELF ST BIN HOMEDIR REPO
  SELF=$(cd "$(dirname "$0")" && pwd)/$(basename "$0")
  ST=$(mktemp -d) || { echo "錯誤:建不了暫存區。" >&2; exit 2; }
  # 暫存區 MUST 是 POSIX 路徑:磁碟機代號加冒號的形狀會把 PATH 切斷,結果叫到真的 gh。
  case "$ST" in
    [A-Za-z]:*) rm -rf "$ST"; echo "錯誤:暫存區是 Windows 形狀的路徑,PATH 會被冒號切斷,自測中止。" >&2; exit 2 ;;
  esac
  trap 'rm -rf "$ST"' EXIT
  : > "$ST/.pdk-selftest"
  BIN=$ST/bin; HOMEDIR=$ST/home; REPO=$ST/repo
  mkdir -p "$BIN" "$ST/store" "$HOMEDIR" "$REPO"
  git -C "$REPO" init -q || { echo "錯誤:git init 失敗。" >&2; exit 2; }

  cat > "$BIN/gh" <<'STUB'
#!/usr/bin/env bash
# 自測用替身 gh:只在自測的暫存區內動作;存放區不符一律回 97,不假裝成功。
root=${PDK_FAKE_ROOT:-}
if [ -z "$root" ] || [ ! -f "$root/.pdk-selftest" ] || [ ! -d "$root/store" ]; then
  echo "替身 gh:不在自測暫存區內,拒絕動作" >&2; exit 97
fi
here=$(cd "$(dirname "$0")" && pwd)
rootn=$(cd "$root" && pwd)
if [ "$here" != "$rootn/bin" ]; then
  echo "替身 gh:位置與存放區不符,拒絕動作" >&2; exit 97
fi
store=$rootn/store
echo "$*" >> "$rootn/argv.log"
case "$1 ${2:-}" in
  "auth status") exit 0 ;;
  "repo view") echo "example-owner/pdk-selftest"; exit 0 ;;
  "secret set") cat > "$store/secret.$3"; exit 0 ;;
  "variable set")
    v=$7
    [ "${PDK_FAKE_MODE:-}" = bad-variable ] && v=wrong-value
    printf '%s' "$v" > "$store/var.$3"; exit 0 ;;
  "secret list")
    for f in "$store"/secret.*; do [ -e "$f" ] && echo "${f##*/secret.}"; done
    exit 0 ;;
  "variable get") cat "$store/var.$3" 2>/dev/null || exit 1; exit 0 ;;
esac
echo "替身 gh:未支援 $*" >&2; exit 98
STUB
  chmod +x "$BIN/gh"

  local LAUNCHES=0 RC=0 FAKE_MODE=ok EXPECT_DIR=""
  # 在假 repo 內以子行程跑主流程;答案從檔案餵入。啟動前先過替身斷言。
  launch() {
    local ans=$1
    shift
    assert_resolves_to_stub "$BIN" "${EXPECT_DIR:-$BIN}" gh || { RC=97; return 0; }
    LAUNCHES=$((LAUNCHES + 1))
    RC=0
    ( cd "$REPO" && HOME="$HOMEDIR" PATH="$BIN:$PATH" PDK_FAKE_ROOT="$ST" PDK_FAKE_MODE="$FAKE_MODE" bash "$SELF" "$@" ) < "$ans" > "$ST/out" 2> "$ST/err"
    RC=$?
    return 0
  }
  reset_store() { rm -f "$ST/store"/* "$ST/argv.log"; : > "$ST/argv.log"; }
  count_sets() { grep -cE '^(secret|variable) set ' "$ST/argv.log"; }

  local ROWS="" BAD=0 TOTAL=0
  row() {  # 編號 情境 預期 實際
    local v=符合
    TOTAL=$((TOTAL + 1))
    [ "$3" = "$4" ] || { v=不符; BAD=$((BAD + 1)); }
    ROWS="$ROWS| $1 | $2 | $3 | $4 | $v |"$'\n'
  }

  local ALIAS=pdkalias PW='Pdk-Selftest-9x' WRONG='Pdk-Wrong-Pw-1'
  local KS_DEFAULT="$HOMEDIR/keys/$PACKAGE_ID-release.keystore"
  printf '%s\n%s\n%s\n' "$ALIAS" "$PW" "$PW" > "$ST/ans_new"
  printf '%s\n%s\n' "$ALIAS" "$PW" > "$ST/ans_reuse"
  printf '%s\n%s\n' "$ALIAS" "$WRONG" > "$ST/ans_wrong"
  export ST_PW="$PW"

  # ---- S1 正向:新建金鑰(預設路徑)
  reset_store
  launch "$ST/ans_new"
  local want got ok=1 notes=""
  [ "$RC" = 0 ] || { ok=0; notes="$notes 退出碼$RC"; }
  [ -f "$KS_DEFAULT" ] || { ok=0; notes="$notes 金鑰不在預設路徑"; }
  if [ -f "$KS_DEFAULT" ]; then
    base64 -d "$ST/store/secret.MOBILE_KEYSTORE_BASE64" > "$ST/restored" 2>/dev/null
    cmp -s "$ST/restored" "$KS_DEFAULT" || { ok=0; notes="$notes base64還原不同"; }
    [ "$(cat "$ST/store/var.MOBILE_KEYSTORE_SHA256" 2>/dev/null)" = "$(sha256sum "$KS_DEFAULT" | awk '{print $1}')" ] || { ok=0; notes="$notes SHA256不符"; }
    local LISTV FP
    LISTV=$(KT -list -v -keystore "$KS_DEFAULT" -alias "$ALIAS" -storepass:env ST_PW 2>&1)
    FP=$(printf '%s\n' "$LISTV" | grep -m1 'SHA256:' | awk '{print $NF}' | tr -d '\r')
    { [ -n "$FP" ] && [ "$(cat "$ST/store/var.MOBILE_KEYSTORE_FINGERPRINT" 2>/dev/null)" = "$FP" ]; } || { ok=0; notes="$notes 指紋不符"; }
    printf '%s\n' "$LISTV" | grep -q "CN=$PACKAGE_ID" || { ok=0; notes="$notes CN不是PACKAGE_ID"; }
  fi
  local n
  for n in MOBILE_KEYSTORE_BASE64 MOBILE_KEYSTORE_PASSWORD MOBILE_KEY_PASSWORD MOBILE_KEY_ALIAS; do
    [ -f "$ST/store/secret.$n" ] || { ok=0; notes="$notes 缺secret$n"; }
  done
  for n in MOBILE_KEYSTORE_FINGERPRINT MOBILE_KEYSTORE_SHA256; do
    [ -f "$ST/store/var.$n" ] || { ok=0; notes="$notes 缺variable$n"; }
  done
  local leaks
  leaks=$(cat "$ST/out" "$ST/err" "$ST/argv.log" | grep -cF -- "$PW")
  [ "$leaks" = 0 ] || { ok=0; notes="$notes 密碼出現$leaks行"; }
  want="0;金鑰在預設路徑;base64、SHA256、指紋、CN 皆相符;4 secrets + 2 variables 齊全;密碼在輸出與 argv 中 0 次"
  if [ "$ok" = 1 ]; then got="$want"; else got="不符:$notes"; fi
  row S1 "正向:新建金鑰(預設路徑)" "$want" "$got"

  # ---- S2 沿用既有金鑰、密碼正確
  local before after
  before=$(sha256sum "$KS_DEFAULT" 2>/dev/null | awk '{print $1}')
  reset_store
  launch "$ST/ans_reuse"
  after=$(sha256sum "$KS_DEFAULT" 2>/dev/null | awk '{print $1}')
  got="$RC;金鑰檔$([ -n "$before" ] && [ "$before" = "$after" ] && echo 前後相同 || echo 前後不同)"
  row S2 "沿用既有金鑰、密碼正確" "0;金鑰檔前後相同" "$got"

  # ---- S3 🔴 既有金鑰配錯誤密碼
  reset_store
  launch "$ST/ans_wrong"
  got="$RC;$([ "$(count_sets)" = 0 ] && echo 沒有任何set || echo 出現了set);$(grep -q '打不開金鑰' "$ST/err" && echo 因打不開金鑰而停 || echo 停的原因不對)"
  row S3 "既有金鑰配錯誤密碼(對照)" "1;沒有任何set;因打不開金鑰而停" "$got"

  # ---- S4 🔴 金鑰路徑在 repo 內
  reset_store
  mkdir -p "$REPO/inrepo"
  launch "$ST/ans_new" "$REPO/inrepo/x.keystore"
  got="$RC;$([ "$(count_sets)" = 0 ] && [ ! -e "$REPO/inrepo/x.keystore" ] && echo 沒有任何set且沒建金鑰 || echo 出現了set或建了金鑰);$(grep -q '在 repo 內' "$ST/err" && echo 因在repo內而停 || echo 停的原因不對)"
  row S4 "金鑰路徑在 repo 內(對照)" "1;沒有任何set且沒建金鑰;因在repo內而停" "$got"
  if command -v cygpath >/dev/null 2>&1; then
    reset_store
    mkdir -p "$REPO/inrepo2"
    launch "$ST/ans_new" "$(cygpath -m "$REPO/inrepo2/y.keystore")"
    got="$RC;$([ "$(count_sets)" = 0 ] && [ ! -e "$REPO/inrepo2/y.keystore" ] && echo 沒有任何set且沒建金鑰 || echo 出現了set或建了金鑰);$(grep -q '在 repo 內' "$ST/err" && echo 因在repo內而停 || echo 停的原因不對)"
    row S4b "金鑰路徑在 repo 內,以磁碟機代號形式給出(對照:兩種形狀比對)" "1;沒有任何set且沒建金鑰;因在repo內而停" "$got"
  else
    row S4b "金鑰路徑在 repo 內,以磁碟機代號形式給出" "不適用(沒有 cygpath,這個環境沒有兩種路徑形狀)" "不適用(沒有 cygpath,這個環境沒有兩種路徑形狀)"
  fi

  # ---- S5 🔴 讀回守門:替身把 variable 存成錯的值
  reset_store
  FAKE_MODE=bad-variable
  launch "$ST/ans_reuse"
  FAKE_MODE=ok
  got="$RC;$(grep -qE 'MOBILE_KEYSTORE_(FINGERPRINT|SHA256)' "$ST/err" && echo 標準錯誤指名variable || echo 未指名)"
  [ "$RC" = 0 ] && got="0;讀回守門沒有擋下錯誤的值"
  row S5 "讀回守門:替身把 variable 存成錯的值(對照)" "1;標準錯誤指名variable" "$got"

  # ---- S6 替身守門自身:預期路徑 ≠ 實際解析路徑
  local l0=$LAUNCHES
  reset_store
  EXPECT_DIR="$ST/not-the-stub-dir"
  launch "$ST/ans_reuse"
  EXPECT_DIR=""
  got="$([ "$RC" != 0 ] && echo 斷言回非0 || echo 斷言放行了);$([ "$LAUNCHES" = "$l0" ] && [ ! -s "$ST/argv.log" ] && echo 沒有啟動子行程 || echo 啟動了子行程)"
  row S6 "替身守門自身:預期路徑 ≠ 實際解析路徑(對照)" "斷言回非0;沒有啟動子行程" "$got"

  echo "setup-signing 自測:情境 × 預期 × 實際"
  echo "| # | 情境 | 預期 | 實際 | 判定 |"
  echo "| --- | --- | --- | --- | --- |"
  printf '%s' "$ROWS"
  if [ "$BAD" != 0 ]; then
    echo "自測失敗:$BAD/$TOTAL 列不符"
    exit 1
  fi
  echo "自測完成:$TOTAL/$TOTAL 列符合"
  exit 0
}

if [ "${1:-}" = "--self-test" ]; then
  self_test
fi

command -v keytool >/dev/null || die "找不到 keytool(JDK 的一部分),請確認 JDK 已安裝並在 PATH 上。"
command -v gh >/dev/null || die "找不到 gh(GitHub CLI)。"
gh auth status >/dev/null 2>&1 || die "gh 尚未登入,先執行 gh auth login。"

# 經 cd + pwd 正規化:Git Bash 上 git 回傳磁碟機代號形式的路徑、pwd 回傳類 Unix 形式,不正規化就永遠比對不到。
REPO_ROOT=$(cd "$(git rev-parse --show-toplevel 2>/dev/null)" && pwd) || die "請在 repo 目錄內執行。"
REPO=$(gh repo view --json nameWithOwner --jq .nameWithOwner) || die "讀不到 GitHub repo,確認已設定 origin。"
KS=${1:-$HOME/keys/$PACKAGE_ID-release.keystore}

# 金鑰 MUST 在 repo 之外:放在 repo 內只差一次 git add -f 就外洩。
KS_DIR=$(mkdir -p "$(dirname "$KS")" && cd "$(dirname "$KS")" && pwd)
case "$KS_DIR/" in
  "$REPO_ROOT"/*) die "金鑰路徑 $KS 在 repo 內,請換一個 repo 以外的位置。" ;;
esac
KS="$KS_DIR/$(basename "$KS")"

echo "repo:     $REPO"
echo "金鑰路徑: $KS"
echo

if [ -e "$KS" ]; then
  echo "金鑰檔已存在,不會覆寫。將沿用這把金鑰設定 GitHub(密碼與別名要與當初建立時相同)。"
  CREATE=0
else
  CREATE=1
fi

# 別名提示取 PACKAGE_ID 的最後一段(執行時導出)。
printf '金鑰別名(例如 %s):' "${PACKAGE_ID##*.}"
read -r KEY_ALIAS
[ -n "$KEY_ALIAS" ] || die "別名不可空白。"

printf '金鑰密碼(不會顯示,至少 6 字元):'
read -rs KS_PASS; echo
[ ${#KS_PASS} -ge 6 ] || die "密碼至少 6 個字元。"
if [ "$CREATE" = 1 ]; then
  printf '再輸入一次:'
  read -rs KS_PASS2; echo
  [ "$KS_PASS" = "$KS_PASS2" ] || die "兩次輸入的密碼不同。"
  unset KS_PASS2
fi
# keytool 以 :env 讀密碼,不放進命令列引數(會出現在行程清單)。
export KS_PASS

if [ "$CREATE" = 1 ]; then
  echo
  echo "建立金鑰(PKCS12,RSA 4096,效期 10000 天)..."
  # PKCS12 的 store 密碼與 key 密碼必須相同,所以兩者用同一個。
  # CN 用 PACKAGE_ID:有辨識度、只含小寫英數與點,不需跳脫;側載安裝的使用者看不到它。
  KT -genkeypair -storetype PKCS12 -keyalg RSA -keysize 4096 -validity 10000 \
     -keystore "$KS" -alias "$KEY_ALIAS" -dname "CN=$PACKAGE_ID" \
     -storepass:env KS_PASS -keypass:env KS_PASS >/dev/null
fi

# 同時驗證密碼與別名:任一錯就在這裡停,不會把錯的值送上 GitHub。
LIST=$(KT -list -v -keystore "$KS" -alias "$KEY_ALIAS" -storepass:env KS_PASS 2>&1) \
  || die "打不開金鑰或找不到別名「$KEY_ALIAS」:$(printf '%s' "$LIST" | head -1)"
# 取憑證指紋(Certificate fingerprints 下的 SHA256),不是公鑰的。
FINGERPRINT=$(printf '%s\n' "$LIST" | sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p' | head -1 | tr -d '\r')
[ -n "$FINGERPRINT" ] || die "從 keytool 輸出讀不到 SHA256 憑證指紋。"
# 檔案雜湊 MUST 小寫:CI 以 sha256sum 的輸出逐字比對。
FILE_SHA=$(sha256sum "$KS" | awk '{print $1}')

echo
echo "設定 GitHub secrets / variables 到 $REPO ..."
# 全部經由管線送進 gh,不經剪貼簿(長字串可能被靜默截斷)、不留在 shell 歷史。
base64 -w0 "$KS" | gh secret set MOBILE_KEYSTORE_BASE64 -R "$REPO"
printf '%s' "$KS_PASS"   | gh secret set MOBILE_KEYSTORE_PASSWORD -R "$REPO"
printf '%s' "$KS_PASS"   | gh secret set MOBILE_KEY_PASSWORD -R "$REPO"
printf '%s' "$KEY_ALIAS" | gh secret set MOBILE_KEY_ALIAS -R "$REPO"
gh variable set MOBILE_KEYSTORE_FINGERPRINT -R "$REPO" --body "$FINGERPRINT"
gh variable set MOBILE_KEYSTORE_SHA256 -R "$REPO" --body "$FILE_SHA"
unset KS_PASS

# 回讀確認:secret 讀不回值,只確認名稱存在;variable 逐字比對。
SECRETS=$(gh secret list -R "$REPO" --json name --jq '.[].name')
for n in MOBILE_KEYSTORE_BASE64 MOBILE_KEYSTORE_PASSWORD MOBILE_KEY_PASSWORD MOBILE_KEY_ALIAS; do
  printf '%s\n' "$SECRETS" | grep -qx "$n" || die "secret $n 沒有設成功。"
done
[ "$(gh variable get MOBILE_KEYSTORE_FINGERPRINT -R "$REPO")" = "$FINGERPRINT" ] || die "variable MOBILE_KEYSTORE_FINGERPRINT 回讀不符。"
[ "$(gh variable get MOBILE_KEYSTORE_SHA256 -R "$REPO")" = "$FILE_SHA" ] || die "variable MOBILE_KEYSTORE_SHA256 回讀不符。"

echo
echo "完成:4 個 secrets 與 2 個 variables 已設定並回讀確認。"
echo "  憑證指紋:$FINGERPRINT"
echo
echo "🔴 現在就備份,至少兩處、都不在這台電腦上(例如密碼管理器 + 隨身碟):"
echo "   1. 金鑰檔 $KS"
echo "   2. 別名與密碼"
echo "   遺失任何一項,之後的版本都無法覆蓋安裝,只能換 applicationId 重來。"
