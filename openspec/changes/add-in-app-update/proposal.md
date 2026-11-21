## Why

使用者要求(2026-11-22):0.2.1 加上「由程式更新」。現在有新版只能開瀏覽器下載 APK、再到下載資料夾點開安裝,朋友常卡在找檔案。
這推翻 add-update-check-and-notices 的「不在 App 內下載安裝 APK」(當時為了不宣告 `REQUEST_INSTALL_PACKAGES`),由使用者決定。

## What Changes

- 新版提示與設定頁手動檢查多一個「更新」:App 自己從 GitHub Release 下載 APK、驗 SHA-256,交給系統安裝程式;使用者在系統畫面按「更新」。
- 宣告 `REQUEST_INSTALL_PACKAGES`;第一次更新時,系統會要求允許「安裝不明應用程式」,App 先帶使用者到該設定頁。
- 「前往下載」保留在下載或驗證失敗時、以及 Release 沒有可用 APK 時,當作退路。
- 版號 0.2.1(和改名「YT下載Lite」一起發)。

## Capabilities

### Modified Capabilities

- `app-update`:App 可以下載並交給系統安裝新版;提示與手動檢查的按鈕改變。

## Impact

- 新程式:`update/UpdateCheck.kt` 解析 Release 的 APK 檔(名稱、網址、大小、SHA-256);`service/UpdateInstaller.kt` 下載、驗證、啟動安裝;FileProvider 設定。
- 權限多一項 `REQUEST_INSTALL_PACKAGES`(README 與規格同步)。
- 0.2.0 以前的使用者還沒有這個功能,要先手動裝一次 0.2.1;之後的新版才能在 App 內更新。
