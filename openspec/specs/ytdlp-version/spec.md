# ytdlp-version Specification

## Purpose

讓 App 使用一個今天能下載的 yt-dlp 版本:內建版過期時先更新,下載失敗疑似 YouTube 改版時提示更新,新版有問題時能退回上一個可用的版本。採用使用者裁定的「只在失敗時更新」,不做背景或定期更新。

## Requirements

### Requirement: 首次使用前更新

處理佇列中的第一個項目之前,如果 yt-dlp 還是 App 內建的版本(從未更新過),系統 SHALL 先更新到 stable 再開始下載。
更新失敗時 SHALL 仍然用內建版嘗試下載,並在畫面顯示更新失敗的原因。

#### Scenario: 第一次下載

- **WHEN** 剛安裝 App 的使用者第一次加入影片
- **THEN** 通知先顯示「正在更新 yt-dlp」,更新完成後才開始下載;畫面顯示更新後的版本

### Requirement: 失敗時提示更新

項目因「YouTube 拒絕」或「要求登入確認不是機器人」失敗時,畫面 SHALL 顯示目前的 yt-dlp 版本與「更新 yt-dlp」按鈕。系統 MUST NOT 在背景自動更新。

#### Scenario: 失敗後更新並重試

- **WHEN** 使用者在「YouTube 拒絕」的提示按「更新 yt-dlp」
- **THEN** 更新到 stable 後,所有「YouTube 拒絕」的失敗項目回到「等待中」

### Requirement: 手動更新

「設定」頁 SHALL 顯示目前使用的 yt-dlp 版本,並提供「更新 yt-dlp」。顯示的版本 SHALL 以實際執行 `yt-dlp --version` 的結果為準。
有下載正在進行時,更新 SHALL 等進行中的 yt-dlp 都結束才替換執行檔,替換期間 MUST NOT 開始新的 yt-dlp;畫面 SHALL 顯示「等待下載結束後更新」。

#### Scenario: 已是最新

- **WHEN** 已經是最新版的使用者在「設定」頁按「更新 yt-dlp」
- **THEN** 畫面顯示「已是最新版本」與版本號

#### Scenario: 下載中按更新

- **WHEN** 有 2 支正在下載時,使用者按「更新 yt-dlp」
- **THEN** 畫面顯示「等待下載結束後更新」;進行中的 yt-dlp 結束後才更新,佇列中其他等待的項目在更新完成後才開始下載

### Requirement: 保留與退回上一個版本

每次更新前,系統 SHALL 保留目前的 yt-dlp 執行檔。更新後如果新版執行 `--version` 失敗,SHALL 自動還原保留的版本。使用者 SHALL 能在「設定」頁手動「退回上一版」;只保留一個上一版。
退回的對象是「上一個版本」,MUST NOT 是 App 內建的版本(除非上一版就是內建版)。退回和更新一樣,要等進行中的 yt-dlp 都結束才替換執行檔。

#### Scenario: 新版無法執行

- **WHEN** 更新下載的檔案損壞,執行 `--version` 失敗
- **THEN** 自動還原為更新前的版本,畫面顯示「更新失敗,已還原為 <版本>」

#### Scenario: 手動退回

- **WHEN** 使用者從 2026.10.26 更新到 2026.11.20 後,在「設定」頁按「退回上一版」
- **THEN** 使用的版本變回 2026.10.26
