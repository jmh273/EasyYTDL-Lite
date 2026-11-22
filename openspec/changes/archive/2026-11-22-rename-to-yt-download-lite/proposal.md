## Why

使用者要求(2026-11-22):桌面與分享選單上的名稱改為「YT下載Lite」(EasyYTDL 同時改為「YT下載」)。

## What Changes

- 正式版顯示「YT下載Lite」,debug 版「YT下載Lite(測試)」;「開放原始碼授權」裡的本 App 名稱同步。
- 套件名 `com.jmh273.easyytdllite`、儲存資料夾 `EasyYTDL-Lite`、repo 名稱不變(改了會變成另一個 App、或找不到舊檔)。
- 版號 0.2.1。

## Capabilities

### Modified Capabilities

- `app-navigation`:加上 App 顯示名稱。

## Impact

- `res/values/strings.xml`、`app/build.gradle`(debug 名稱)、`about/OpenSourceNotices.kt`、README。覆蓋安裝後資料保留。
