## 1. 實作

- [x] 1.1 `UpdateCheck.parse` 讀出 APK asset(名稱、網址、大小、SHA-256);`ApkVerify`(SHA-256、大小)與舊檔清理規則;JVM 測試
- [x] 1.2 `service/UpdateInstaller.kt`:下載(進度)、驗證、FileProvider、安裝權限引導、啟動系統安裝
- [x] 1.3 提示列與設定頁:「更新」/下載中/失敗與「前往下載」
- [x] 1.4 Manifest:`REQUEST_INSTALL_PACKAGES`、FileProvider;README 權限與更新說明

## 2. 驗證

- [x] 2.1 `:app:testDebugUnitTest`、`assembleDebug`;`aapt2` 讀權限
- [x] 2.2 端對端(先問 adb):測試分支把版號改成 0.1.9,CI dry_run 產生正式簽章 APK;手機移除 Lite 後裝它,App 內更新到 Release 上的最新版;`firstInstallTime` 不變、版號變新、資料保留
- [ ] 2.3 發 `mobile-v0.2.1`(先確認)
- [x] 2.4 端對端結果(2026-11-22,CI dry_run 44964006823 的 0.1.9 正式簽章 APK,測試機全新安裝):提示「有新版 0.2.0」→ 按「更新」約 17 秒下載並驗證 → 開「安裝不明應用程式」→ 允許後回來按「更新」直接出現系統安裝畫面(未重新下載)→ Play 安全防護選「不掃描就安裝」→ 0.2.0(versionCode 200),firstInstallTime 不變。測試分支已刪,手機的安裝權限改回 default。
