## 1. 檢查新版(app-update)

- [x] 1.1 `update/UpdateCheck.kt`:解析 `releases/latest`、比版號、要不要自動檢查、要不要提示;JVM 測試(含 2026-11-22 實查回應)
- [x] 1.2 `service/UpdateChecker.kt`:`HttpURLConnection` 讀 API、SharedPreferences 存嘗試時間與最新結果、略過的版號
- [x] 1.3 `ui/UpdateBanner.kt` 接到主畫面;`MainActivity.onStart` 自動檢查
- [x] 1.4 設定頁「檢查新版」按鈕與結果

## 2. 開放原始碼授權

- [x] 2.1 `about/OpenSourceNotices.kt` 名單、`ui/Licenses.kt` 對話框;設定頁按鈕
- [x] 2.2 JVM 守門:build.gradle 每個 `implementation` 的 group 都在名單裡(錨點 youtubedl-android;拿掉一項要紅)

## 3. 文件與發版

- [x] 3.1 README 安裝步驟與 App 截圖
- [x] 3.2 `VERSION_NAME` 0.2.0;`:app:testDebugUnitTest`、`assembleDebug`、`check-no-google-auth.sh`
- [x] 3.3 實機驗收(每批 adb 先問):自動檢查提示(用改小的版號模擬舊版)、略過、手動檢查、授權對話框;release 全新安裝冒煙
- [x] 3.4 推 `mobile-v0.2.0` tag(先確認);CI 全綠,全新安裝 CI 簽章 APK 冒煙:分享 → 列出 → 加入 → MP3 完成、手動檢查「已是最新版」
