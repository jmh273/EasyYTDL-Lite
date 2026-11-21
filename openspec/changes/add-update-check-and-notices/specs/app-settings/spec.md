## MODIFIED Requirements

### Requirement: 設定頁內容

「設定」頁 SHALL 依序分成幾區:
- yt-dlp:目前版本、「更新 yt-dlp」、有上一版時的「退回 <版本>」(規則見 ytdlp-version);
- 下載:同時下載支數、目標音量;
- 關於:App 版本、免責聲明、「檢查新版」(規則見 app-update)、「開放原始碼授權」。debug 版另有「下載探測」入口,release 版 MUST NOT 有這個入口。

「開放原始碼授權」SHALL 列出本 App 的授權(GPL-3.0)與原始碼網址,以及打包的第三方元件(至少 yt-dlp、youtubedl-android、Python、FFmpeg、QuickJS 與 build.gradle 的每個直接相依)的名稱、授權與網址。

設定頁 MUST NOT 有帳號或登入相關的項目。

#### Scenario: 打開設定頁

- **WHEN** 使用者點標題列的設定按鈕
- **THEN** 依序看到 yt-dlp、下載、關於三區;下載區有同時下載支數和目標音量;沒有帳號區

#### Scenario: release 版沒有探測入口

- **WHEN** 在 release 版打開「設定」頁
- **THEN** 沒有「下載探測」按鈕

#### Scenario: 開放原始碼授權

- **WHEN** 使用者在「關於」區按「開放原始碼授權」
- **THEN** 看到本 App 是 GPL-3.0 與原始碼網址,以及 youtubedl-android、FFmpeg 等元件的授權
