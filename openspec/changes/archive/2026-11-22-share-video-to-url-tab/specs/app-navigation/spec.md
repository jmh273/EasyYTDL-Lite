## MODIFIED Requirements

### Requirement: 預設頁面與位置保留

App 打開時 SHALL 顯示「搜尋」分頁。
點下載通知時,SHALL 關閉設定頁(如果開著)並切到「佇列」分頁。從其他 App 分享進來時(單支影片、播放清單、頻道,或認不得的文字),SHALL 關閉設定頁並切到「網址」分頁(見 video-listing「分享影片、播放清單或頻道」)。
旋轉螢幕或 Activity 重建時,所在的分頁與設定頁是否開著 MUST 保留。

#### Scenario: 分享進來

- **WHEN** 使用者正在「設定」頁,這時從 YouTube App 分享一支影片給 EasyYTDL Lite
- **THEN** 畫面切到「網址」分頁並列出這支影片;佇列不變

#### Scenario: 旋轉螢幕

- **WHEN** 使用者在「網址」分頁旋轉螢幕
- **THEN** 仍停在「網址」分頁
