## Why

朋友從 GitHub Release 側載安裝,不會自己追新版;YouTube 改版時舊版可能整個不能用,卻沒有任何提示。
另外 App 打包了 GPL-3.0 的 youtubedl-android 與 FFmpeg 等第三方元件,公開散布 APK 時 App 內要看得到授權聲明與原始碼位置。

## What Changes

- 新增「檢查新版」:讀 GitHub 公開 API 的 `releases/latest`(免授權),比目前版號新就在主畫面顯示提示,按下開瀏覽器到 Release 頁。
  - App 開啟時自動檢查,一天最多一次;設定頁「關於」區有手動「檢查新版」按鈕。
  - 不在 App 內下載或安裝 APK(不宣告 `REQUEST_INSTALL_PACKAGES`)。
- 設定頁「關於」區新增「開放原始碼授權」:列出本 App 與打包的第三方元件、各自的授權,以及原始碼網址。
- README 補安裝步驟與 App 畫面截圖。
- 版號 0.2.0。

## Capabilities

### New Capabilities

- `app-update`:檢查新版的時機、比對規則、提示與失敗處理。

### Modified Capabilities

- `app-settings`:「關於」區多了「檢查新版」與「開放原始碼授權」。

## Impact

- 新程式:`update/UpdateCheck.kt`(純邏輯:解析回應、比版號、要不要自動檢查)、`service/UpdateChecker.kt`(連網與存上次檢查時間)、`ui/UpdateBanner.kt`、`about/OpenSourceNotices.kt`(名單)、`ui/Licenses.kt`(對話框)。
- 權限不變(只用既有的 `INTERNET`)。
- 不加新相依(用 `HttpURLConnection` 與 Android 內建的 `org.json`),R8 規則不變。
