## ADDED Requirements

### Requirement: App 顯示名稱

App 在桌面、App 清單與分享選單 SHALL 顯示「YT下載Lite」;debug 版 SHALL 顯示「YT下載Lite(測試)」。
套件名 `com.jmh273.easyytdllite`、儲存資料夾 `EasyYTDL-Lite` MUST NOT 因改名而改變;覆蓋安裝後設定與下載紀錄 SHALL 保留。

#### Scenario: 分享選單

- **WHEN** 使用者在 YouTube App 對影片按「分享」
- **THEN** 分享選單裡本 App 的名稱是「YT下載Lite」
