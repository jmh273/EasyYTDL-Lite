# download-probe Specification

## Purpose

debug 版限定的下載探測工具:在真機上實測 yt-dlp、JS 執行環境、ffmpeg 與 yt-dlp 更新的實際行為,作為下載功能設計的依據。

## Requirements

### Requirement: 探測工具只存在於 debug 版

下載探測畫面 SHALL 只出現在 debug 版(套件名有 `.debug` 後綴)。release 版 MUST NOT 含有這個畫面或進入它的入口。

#### Scenario: debug 版有入口

- **WHEN** 使用者開啟 debug 版
- **THEN** 可以進入下載探測畫面

#### Scenario: release 版沒有入口

- **WHEN** 以 `aapt2 dump badging` 與 `dump xmltree` 讀 release APK
- **THEN** manifest 中沒有下載探測的 Activity

### Requirement: 以指定的 JS 執行環境模式下載並轉成 MP3

使用者 SHALL 能輸入或分享一個 YouTube 影片網址,選擇「QuickJS」或「對照組」模式後下載音訊並轉成 MP3。
對照組模式 MUST 讓 yt-dlp 實際上沒有任何 JS 執行環境可用,而且這件事 MUST 能從該次執行的詳細日誌中看出來。

#### Scenario: QuickJS 模式

- **WHEN** 使用者以 QuickJS 模式下載一支影片
- **THEN** 該次詳細日誌顯示 yt-dlp 可用的 JS 執行環境包含 QuickJS

#### Scenario: 對照組模式

- **WHEN** 使用者以對照組模式下載一支影片
- **THEN** 該次詳細日誌顯示 yt-dlp 沒有可用的 JS 執行環境
- **AND** 下載結果(成功、失敗或缺少格式)如實記錄,不重試

#### Scenario: 從 YouTube App 分享

- **WHEN** 使用者在 YouTube App 對影片按「分享」並選擇 EasyYTDL Lite(測試)
- **THEN** 下載探測畫面開啟,網址已經填好,但不會自動開始下載

### Requirement: MP3 存到每天的資料夾

轉好的 MP3 SHALL 存到公用的 `Music/EasyYTDL-Lite/YYYYMMDD/`(和正式下載共用 `DailyFolder`),日期取下載當天的手機本地日期,而且 MUST NOT 需要「所有檔案存取權」或儲存權限。

#### Scenario: 成功存檔

- **WHEN** 2026-11-05 下載成功
- **THEN** 檔案出現在 `/sdcard/Music/EasyYTDL-Lite/20261105/`,副檔名是 `.mp3`
- **AND** 其他 App 透過 MediaStore 看得到這個檔案

### Requirement: 保留每次執行的原始紀錄

每次下載 SHALL 保留完整的指令、退出碼、標準輸出、標準錯誤、耗時,以及影片資訊(info JSON),存成 App 私有目錄裡的檔案,可以用 adb 取出。
紀錄 MUST NOT 只保留摘要;下載失敗時也 MUST 保留。

#### Scenario: 下載失敗仍有紀錄

- **WHEN** 一次下載以非 0 退出碼結束
- **THEN** 該次的指令、退出碼、標準錯誤都存在紀錄檔裡,畫面顯示紀錄檔路徑

### Requirement: yt-dlp 版本查詢、更新與退回

探測畫面 SHALL 顯示目前使用的 yt-dlp 版本,並提供「更新到 stable」與「退回內建版本」兩個操作。

#### Scenario: 更新

- **WHEN** 使用者按「更新到 stable」
- **THEN** 畫面顯示更新結果(已更新/已是最新/失敗原因),以及更新後的版本

#### Scenario: 退回內建版本

- **WHEN** 使用者在更新後按「退回內建版本」
- **THEN** 顯示的版本回到 App 內建的版本,而且這個操作不需要連網

### Requirement: 檢查 ffmpeg 濾鏡

探測畫面 SHALL 能列出附帶的 ffmpeg 支援的濾鏡,並明確顯示其中有沒有 `replaygain`。

#### Scenario: 列出濾鏡

- **WHEN** 使用者按「檢查 ffmpeg」
- **THEN** 畫面顯示 `replaygain`:有/沒有,完整的濾鏡清單存成紀錄檔
