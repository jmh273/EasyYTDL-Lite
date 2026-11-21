## MODIFIED Requirements

### Requirement: 貼網址列出影片

「網址」分頁 SHALL 有網址輸入框與「列出」按鈕。按下後依網址種類處理:
- 單支影片網址:列出這一支影片,並自動勾選(已下載或已在佇列的不勾);使用者確認格式後按「加入佇列」。
- 播放清單網址(含 `list=`):列出清單中的影片,最多 200 支,依清單順序。
- 頻道網址(`/@名稱`、`/channel/UC…`、`/c/名稱`、`/user/名稱`):列出頻道「影片」分頁最近 30 支,新到舊。
- 其他文字,或 YouTube 自動產生的合輯(`list=RD…`):不列清單,提示「不是可列出的 YouTube 網址」。

列出時 SHALL 顯示讀取中;完成後顯示清單標題與每支影片的縮圖、標題、長度(有的話)。
列出 MUST NOT 需要登入或任何金鑰。

#### Scenario: 貼頻道網址

- **WHEN** 使用者貼上 `https://www.youtube.com/@somechannel` 並按「列出」
- **THEN** 畫面列出這個頻道最近 30 支影片,每支有縮圖、標題、長度與勾選框

#### Scenario: 貼播放清單網址

- **WHEN** 使用者貼上 `https://www.youtube.com/playlist?list=PL123` 並按「列出」
- **THEN** 畫面依清單順序列出影片

#### Scenario: 貼單支影片網址

- **WHEN** 使用者貼上 `https://youtu.be/abc123defgh` 並按「列出」
- **THEN** 畫面列出這一支影片(縮圖、標題、長度),已勾選,「加入佇列(1)」可以按;佇列不變

#### Scenario: 不認得的網址

- **WHEN** 使用者貼上 `https://example.com/x` 並按「列出」
- **THEN** 不發出任何請求,提示「不是可列出的 YouTube 網址」

## REMOVED Requirements

### Requirement: 分享播放清單或頻道

**Reason**: 單支影片也改成先列出再加入,規則合併到新的「分享影片、播放清單或頻道」。
**Migration**: 見 ADDED「分享影片、播放清單或頻道」。

## ADDED Requirements

### Requirement: 分享影片、播放清單或頻道

從其他 App 分享進來時,SHALL 切到「網址」分頁,把分享的文字填入輸入框,依「貼網址列出影片」的規則處理:單支影片、播放清單、頻道都開始列出;認不得的文字提示「不是可列出的 YouTube 網址」。
分享進來 MUST NOT 直接加入佇列;使用者 SHALL 能在按「加入佇列」前用標題列的格式按鈕改格式。

#### Scenario: 分享單支影片

- **WHEN** 使用者在 YouTube App 分享 `https://youtu.be/abc123defgh?si=x` 給 EasyYTDL Lite
- **THEN** 畫面切到「網址」分頁,輸入框是分享的網址,列出這支影片並已勾選;佇列不變

#### Scenario: 分享後改成 MP4

- **WHEN** 分享一支影片進來後,使用者用格式按鈕切到 MP4 720p,再按「加入佇列」
- **THEN** 佇列多一個這支影片的 MP4 720p 項目

#### Scenario: 分享播放清單

- **WHEN** 使用者在 YouTube App 分享 `https://youtube.com/playlist?list=PL123&si=x` 給 EasyYTDL Lite
- **THEN** 畫面切到「網址」分頁,輸入框是分享的網址,開始列出這個清單
