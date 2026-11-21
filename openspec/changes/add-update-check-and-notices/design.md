## Context

0.1.x 由 CI 發到公開 repo `jmh273/EasyYTDL-Lite` 的 GitHub Release,tag 是 `mobile-v<MAJOR>.<MINOR>.<PATCH>`,`versionName` 與 tag 版號相同(CI 驗證)。
GitHub API `GET /repos/{owner}/{repo}/releases/latest` 免授權,只回傳最新一筆非草稿、非預先發布的 Release;未授權呼叫每個 IP 每小時 60 次。

## Goals / Non-Goals

**Goals:** 朋友在不知道有新版時也看得到;看得到授權聲明。
**Non-Goals:** App 內下載安裝 APK、強制更新、改用其他發布管道。

## Decisions

### D1 資料來源與解析

- 網址固定 `https://api.github.com/repos/jmh273/EasyYTDL-Lite/releases/latest`,`Accept: application/vnd.github+json`,連線與讀取逾時各 10 秒。
- 只讀 `tag_name` 與 `html_url`。`tag_name` 必須是 `mobile-v` 加上三段數字,否則視為「讀不懂」;`html_url` 必須以 `https://github.com/` 開頭,否則同樣視為讀不懂(不把任意網址交給瀏覽器)。
- 2026-11-22 實查回應存成測試檔(`test/resources/update/latest.json`)。

### D2 比版號

- 三段數字依序比較。目前版號去掉 `-` 之後的後綴(debug 版是 `0.2.0-debug`)再比。
- 目前版號讀不懂時不提示(寧可漏報,不要每次都跳)。

### D3 何時檢查

- 自動:`MainActivity.onStart`,距上次「嘗試」超過 24 小時才檢查。嘗試時間在發出請求前就寫入,失敗也算一次(不在沒網路時每次開 App 都重試)。
- 手動:設定頁按鈕,不受 24 小時限制。
- 自動檢查失敗不顯示任何訊息;手動檢查失敗顯示「檢查失敗,請稍後再試」。
- 時鐘被調回(上次嘗試時間在未來)時視為該檢查。

### D4 提示

- 有新版時,主畫面分頁列下方顯示一列:「有新版 X.Y.Z」「前往下載」「略過」。
- 「前往下載」用 `ACTION_VIEW` 開 `html_url`。
- 「略過」記住這個版號,之後同一版不再顯示提示;更新的版本出來時照常顯示。設定頁手動檢查不受「略過」影響。
- 存檔:SharedPreferences `update_check`,鍵 `last_attempt_ms`、`skipped_version`、`latest_version`、`latest_url`(最新結果留著,App 重開後提示還在,不必再連網)。

### D5 開放原始碼授權

- 名單是程式裡的常數(`about/OpenSourceNotices.kt` 的 `ENTRIES`,純 Kotlin、JVM 可測),以 `ui/Licenses.kt` 的對話框顯示:元件、授權、網址;最上面是本 App(GPL-3.0)與原始碼網址。
- 守門:JVM 測試讀 `app/build.gradle` 的 `implementation` 座標,每個 group 都要在名單裡找得到對應項目;錨點是 `youtubedl-android`(找不到代表讀錯檔)。射程:只涵蓋直接相依與 youtubedl-android 內建的執行檔(yt-dlp、Python、FFmpeg、QuickJS 手動列入);間接相依列主要的幾個。

## Risks / Trade-offs

- repo 改名或轉私有 ⇒ 檢查一律失敗,自動檢查靜默。要改網址只能發新版。
- 未授權額度 60 次/小時/IP:一天一次的自動檢查遠低於上限;手動連按會被擋,顯示檢查失敗。
- 授權名單靠人工維護;守門只抓得到 build.gradle 的直接相依。
