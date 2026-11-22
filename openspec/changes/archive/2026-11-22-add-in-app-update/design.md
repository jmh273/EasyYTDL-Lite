## Context

Release 由 CI 發,每版一個 `app-release-v<版號>.apk`;GitHub API `releases/latest` 的 asset 帶 `browser_download_url`、`size`、`digest`(`sha256:<64 個十六進位>`;2026-11-22 實查 0.1.1、0.2.0 都有)。
Android 安裝更新時會檢查新 APK 的簽章與已安裝的相同,不同就拒絕;所以被換掉的 APK 裝不上去,SHA-256 是另一層保護(擋下載途中損壞與被換)。

## Decisions

### D1 選哪個 APK

asset 名稱必須正好是 `app-release-v<tag 的版號>.apk`,下載網址必須以 `https://github.com/jmh273/EasyYTDL-Lite/releases/download/` 開頭,`digest` 必須是 `sha256:` 加 64 個十六進位,`size` 大於 0。任一不符 ⇒ 沒有可用 APK,只給「前往下載」。

### D2 下載與驗證

- 下載到 `cacheDir/update/<asset 名稱>.part`,完成、大小相符、SHA-256 相符後才改名成 `<asset 名稱>`;不符就刪掉,顯示失敗並給「前往下載」。
- `HttpURLConnection`,允許 GitHub 轉址到 `objects.githubusercontent.com`(同為 https);連線逾時 15 秒、讀取逾時 30 秒。
- 已下載且驗證過的同名檔案再按「更新」時直接安裝,不重新下載。
- App 開啟時刪掉 `cacheDir/update/` 裡版號不大於目前版本的檔案(已裝好的舊 APK 不留著佔空間)。

### D3 交給系統安裝

- `ACTION_VIEW` + `application/vnd.android.package-archive`,以 FileProvider 的 content URI 給讀取權限。
- `packageManager.canRequestPackageInstalls()` 為 false 時,先開 `ACTION_MANAGE_UNKNOWN_APP_SOURCES`(帶本 App 套件名),畫面提示「允許後回來再按一次更新」;下載好的檔案保留,回來按「更新」直接安裝。
- 安裝結果(使用者取消、簽章不符)由系統畫面顯示;App 不追蹤 session 結果。版本有沒有更新,以重開後的版號為準。

### D4 狀態

`UpdateInstaller.state`(StateFlow):閒置、下載中(百分比)、等待允許安裝、失敗(原因);畫面依狀態顯示。同一時間只有一個下載;下載中再按無效。

## Risks / Trade-offs

- 多一個權限,系統設定頁會列出「安裝不明應用程式」;僅在使用者按「更新」時才引導。
- 公開 repo 被入侵、APK 與 digest 一起被換:SHA-256 擋不住,但簽章不同,系統拒絕安裝(更新必須同簽章)。
