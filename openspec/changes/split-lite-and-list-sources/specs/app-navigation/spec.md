## REMOVED Requirements

### Requirement: 左側選單

**Reason**: 拿掉帳號、最新影片、頻道下載後,選單只剩「設定」一項。
**Migration**: 設定改由標題列右側的設定按鈕開啟(見「頂部分頁與設定按鈕」)。

### Requirement: 帳號次選單

**Reason**: Lite 版沒有 Google 帳號。
**Migration**: 無。

### Requirement: 主頁面與頁籤

**Reason**: 「最新影片」「頻道下載」兩個主頁面需要 Google 授權,Lite 版沒有。
**Migration**: 改成三個頂部分頁(見「頂部分頁與設定按鈕」)。

## ADDED Requirements

### Requirement: 頂部分頁與設定按鈕

主畫面 SHALL 在頂部有三個分頁,依序是「搜尋」、「網址」、「佇列」(規則見 video-listing 與 download-queue「佇列畫面」)。
標題列 SHALL 顯示 App 名稱,右側依序是格式按鈕(見 download-queue「格式與解析度選擇」)與設定按鈕。
點設定按鈕 SHALL 打開整頁的「設定」;在設定頁按返回鍵或標題列的返回 SHALL 回到原本的分頁。設定頁不顯示格式按鈕與分頁。

#### Scenario: 切換分頁

- **WHEN** 使用者在「搜尋」分頁點「佇列」
- **THEN** 畫面顯示下載佇列,「佇列」分頁標示為目前分頁

#### Scenario: 打開設定再返回

- **WHEN** 使用者在「網址」分頁點設定按鈕,再按返回鍵
- **THEN** 回到「網址」分頁,App 不離開

## MODIFIED Requirements

### Requirement: 預設頁面與位置保留

App 打開時 SHALL 顯示「搜尋」分頁。
從其他 App 分享單支影片進來,或點下載通知時,SHALL 關閉設定頁(如果開著)並切到「佇列」分頁;分享播放清單或頻道網址時切到「網址」分頁(見 video-listing「分享播放清單或頻道」)。
旋轉螢幕或 Activity 重建時,所在的分頁與設定頁是否開著 MUST 保留。

#### Scenario: 分享進來

- **WHEN** 使用者正在「設定」頁,這時從 YouTube App 分享一支影片給 EasyYTDL Lite
- **THEN** 畫面切到「佇列」分頁,佇列多了這支

#### Scenario: 旋轉螢幕

- **WHEN** 使用者在「網址」分頁旋轉螢幕
- **THEN** 仍停在「網址」分頁
