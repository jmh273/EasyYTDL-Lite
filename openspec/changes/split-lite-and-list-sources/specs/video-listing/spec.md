## ADDED Requirements

### Requirement: 貼網址列出影片

「網址」分頁 SHALL 有網址輸入框與「列出」按鈕。按下後依網址種類處理:
- 單支影片網址:直接以目前格式加入佇列(規則見 download-queue「加入佇列」),輸入框清空,畫面不列清單。
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
- **THEN** 影片以目前格式加入佇列,提示「已加入佇列」,輸入框清空

#### Scenario: 不認得的網址

- **WHEN** 使用者貼上 `https://example.com/x` 並按「列出」
- **THEN** 不發出任何請求,提示「不是可列出的 YouTube 網址」

### Requirement: 關鍵字搜尋

「搜尋」分頁 SHALL 有搜尋框;送出非空白的關鍵字時,SHALL 列出最多 20 支影片(縮圖、標題、長度、勾選框),順序依 YouTube 的搜尋結果。
搜尋結果中不是影片的項目(頻道、播放清單)MUST NOT 列出。
搜尋 MUST NOT 需要登入或任何金鑰。

#### Scenario: 搜尋

- **WHEN** 使用者輸入「lofi」並送出
- **THEN** 畫面列出最多 20 支影片

#### Scenario: 空白關鍵字

- **WHEN** 搜尋框只有空白
- **THEN** 送出按鈕不能按

### Requirement: 勾選加入佇列

清單中的每支影片 SHALL 有勾選框。已在佇列中(同一種格式、還沒結束)的影片 SHALL 標示「已在佇列」,已下載而且檔案還在的影片 SHALL 標示「已下載」;這兩種 MUST NOT 能勾選,勾選框畫成填滿的灰色方塊。
清單上方 SHALL 有「全選」與「加入佇列(n)」;n 是目前勾選的支數,為 0 時不能按。按下後 SHALL 依清單順序以目前格式加入佇列(整批算同一次加入),清掉勾選,並提示加入幾支。
「全選」SHALL 只勾可以勾選的影片。

#### Scenario: 勾選兩支加入

- **WHEN** 清單有 5 支,使用者勾選第 2、4 支後按「加入佇列(2)」
- **THEN** 佇列依序多了這 2 支,提示「已加入 2 支」,勾選清空,這 2 支改標示「已在佇列」

#### Scenario: 已下載的影片

- **WHEN** 清單中的 `abc123defgh` 已經下載成 MP3 而且檔案還在,目前格式是 MP3
- **THEN** 它標示「已下載」,勾選框是灰色方塊,「全選」不會勾到它

### Requirement: 清單保留與讀取失敗

搜尋與網址兩個分頁 SHALL 各自保留自己的清單與勾選;切換分頁、旋轉螢幕後 SHALL 仍在。開始新的一次列出時,舊的勾選 SHALL 清掉;前一次還沒回來的結果 MUST NOT 蓋掉新的。
讀取失敗時 SHALL 顯示原因:沒有網路、YouTube 拒絕或要求確認不是機器人(提示「到設定按『更新 yt-dlp』」)、其他錯誤。
更新 yt-dlp 進行中時,列出 SHALL 等更新結束再執行。手機上的 yt-dlp 還是 App 內建版時,第一次列出或下載前 SHALL 先更新一次(規則見 ytdlp-version「首次使用前更新」)。

#### Scenario: 切分頁後回來

- **WHEN** 使用者在「搜尋」列出結果並勾選 3 支,切到「佇列」再切回「搜尋」
- **THEN** 結果與 3 個勾選都還在

#### Scenario: YouTube 拒絕

- **WHEN** 列出時 yt-dlp 回報 HTTP 403
- **THEN** 顯示「YouTube 拒絕,到設定按『更新 yt-dlp』後再試」

### Requirement: 分享播放清單或頻道

從其他 App 分享的文字裡有影片 id 時,SHALL 照 download-queue「加入佇列」直接加入。沒有影片 id、但是播放清單或頻道網址時,SHALL 切到「網址」分頁,把網址填入輸入框並開始列出。

#### Scenario: 分享播放清單

- **WHEN** 使用者在 YouTube App 分享 `https://youtube.com/playlist?list=PL123&si=x` 給 EasyYTDL Lite
- **THEN** 畫面切到「網址」分頁,輸入框是分享的網址,開始列出這個清單
