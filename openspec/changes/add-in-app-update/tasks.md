## 1. 實作

- [x] 1.1 `UpdateCheck.parse` 讀出 APK asset(名稱、網址、大小、SHA-256);`ApkVerify`(SHA-256、大小)與舊檔清理規則;JVM 測試
- [x] 1.2 `service/UpdateInstaller.kt`:下載(進度)、驗證、FileProvider、安裝權限引導、啟動系統安裝
- [x] 1.3 提示列與設定頁:「更新」/下載中/失敗與「前往下載」
- [x] 1.4 Manifest:`REQUEST_INSTALL_PACKAGES`、FileProvider;README 權限與更新說明

## 2. 驗證

- [x] 2.1 `:app:testDebugUnitTest`、`assembleDebug`;`aapt2` 讀權限
- [ ] 2.2 端對端(先問 adb):測試分支把版號改成 0.1.9,CI dry_run 產生正式簽章 APK;手機移除 Lite 後裝它,App 內更新到 Release 上的最新版;`firstInstallTime` 不變、版號變新、資料保留
- [ ] 2.3 發 `mobile-v0.2.1`(先確認)
