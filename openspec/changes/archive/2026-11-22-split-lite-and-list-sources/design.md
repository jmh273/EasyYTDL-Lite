## Context

EasyYTDL 是純原生 Kotlin + Compose App,下載引擎 `youtubedl-android` 0.18.1(yt-dlp + QuickJS + ffmpeg)。下載、佇列、轉檔都不需要授權;需要授權的只有讀訂閱與頻道影片(YouTube Data API)。
Lite 版要給朋友用,所以不能依賴作者的 Google Cloud 專案。列影片清單改用 yt-dlp 本身。

## Goals / Non-Goals

**Goals:** 拆出不含任何 Google 授權程式的獨立 App;補上播放清單、頻道、搜尋三個入口;和 EasyYTDL 能裝在同一支手機。

**Non-Goals:** 觀察名單、Takeout 匯入、「檢查新版」(階段 2、3);App 內自動更新 APK。

## Decisions

### D1 複製而非重建
從 EasyYTDL 的已追蹤檔案複製(`git archive`),再改名。改名用完整識別字比對(新名稱包含舊名稱):`com.jmh273.easyytdl` 後面不接小寫字母才算舊名。
不帶 git 歷史、不帶 `openspec/changes/archive/`。

### D2 列清單用 `yt-dlp --flat-playlist -J`
- 播放清單:`https://www.youtube.com/playlist?list=<id>`,最多 200 支(`--playlist-end 200`;🟡 上限是本設計自訂,避免上千支的清單讓手機等太久)。
- 頻道:網址正規化成 `https://www.youtube.com/<頻道路徑>/videos`,`--playlist-end 30`(使用者 11-21 定)。不加 `/videos` 時 yt-dlp 回的是分頁(影片、Shorts、直播)的巢狀清單。
- 搜尋:`ytsearch20:<關鍵字>`(使用者 11-21 定)。
- 和下載共用同一個 yt-dlp 執行檔,所以列清單 MUST 拿讀鎖(`YtDlpManager.whileRunning`):更新 yt-dlp 時等它結束。
- 內建版 yt-dlp 一定被 YouTube 擋(EasyYTDL 的經驗),所以列清單前也套用「內建版先更新一次」,和下載共用同一個「這次執行已試過」旗標。
- 解析 `-J` 的 JSON 是純 Kotlin(`youtube/FlatList.kt`),配 JVM 測試;只收 id 是 11 字元的影片項目,其他(頻道、子清單)略過。

### D3 網址分類
`ListSource.classify(text)` 是純邏輯,依序判斷:
1. 有影片 id(既有 `VideoUrl.videoId`)→ 單支影片,直接加入佇列(既有行為不變)。
2. 有 `list=<id>` → 播放清單。`RD` 開頭的「合輯」是無限清單,不支援,回「不是可列出的網址」。
3. 頻道路徑:`/@handle`、`/channel/UC…`、`/c/<name>`、`/user/<name>` → 頻道。
4. 其他 → 不認得。

分享進來的文字也走同一個分類:有影片 id 就照舊直接加入佇列;是播放清單或頻道就切到「網址」分頁並開始列出。

### D4 畫面:三個頂部分頁 + 設定按鈕
`NavState` 改成 `tab`(SEARCH、URL、QUEUE)與 `settingsOpen`。預設是「搜尋」。設定頁是整頁,返回鍵回到原本的分頁。
分享單支影片或點下載通知 → 「佇列」;分享清單 → 「網址」。旋轉時保留。

### D5 清單狀態放 ViewModel
`ListViewModel` 有搜尋與網址兩個獨立的 slot(結果、勾選、讀取中、錯誤),切分頁與旋轉螢幕都保留。每次請求有編號,晚回來的舊結果丟掉。
勾選規則沿用 EasyYTDL 的 `VideoSelection`(已在佇列、已下載不能勾,灰色方塊)。

### D6 失敗訊息
列清單失敗時用既有的 `FailureClassifier` 分類;「YouTube 拒絕」「要求確認不是機器人」時訊息直接寫「到設定按『更新 yt-dlp』」。

### D7 守門:不得含 Google 授權
`mobile/scripts/check-no-google-auth.sh`:掃 `app/build.gradle` 的相依與 APK 內 dex 的類別名稱,出現 `play-services-auth` 或 `com/google/android/gms/auth` 就失敗。附自測(對照組):對一份含違規字串的替身輸入必須紅。CI 在驗證 APK 時跑。

## Risks / Trade-offs

- 列清單也是對 YouTube 發請求:不做自動重整、不做背景預抓。
- `--flat-playlist` 在 youtubedl-android 上的實際輸出欄位(🟡 未實測):解析器對缺欄位寬鬆處理(沒有標題顯示 id、沒有長度就不顯示),測試檔依 yt-dlp 文件的欄位撰寫,實機跑一次後換成真實輸出。
- 拿掉 `play-services-auth` 後 APK 大小 🟡 未量,建置後以 APK 讀數為準。
