## Why

使用者回報(2026-11-22):從 YouTube 分享單支影片進來時直接加入佇列開始下載,沒有機會選 MP3 或 MP4。
格式按鈕在標題列,分享前不會先打開 App 切格式,所以「分享即下載」等於永遠用上一次的格式。

## What Changes

- 分享單支影片改成切到「網址」分頁,列出這支影片並自動勾選;使用者可先改格式,再按「加入佇列」。
- 「網址」分頁手動貼單支影片網址也一樣先列出(同一個輸入框,不因來源不同而行為不同)。
- 分享認不得的文字:切到「網址」分頁,填入文字並提示「不是可列出的 YouTube 網址」。
- 點下載通知仍切到「佇列」。

## Capabilities

### Modified Capabilities

- `video-listing`:單支影片網址也列出;分享規則合併為「分享影片、播放清單或頻道」。
- `download-queue`:加入佇列一律經過清單的「加入佇列」按鈕。
- `app-navigation`:分享進來一律切到「網址」分頁。

## Impact

- `youtube/ListSource.kt`(單支影片也是可列出的來源)、`youtube/FlatList.kt`(解析單支影片的 JSON)、`ui/ListViewModel.kt`(單支影片自動勾選)、`ui/ListTabs.kt`、`ui/MainActivity.kt`。
- 單支影片要先跑一次 yt-dlp 取標題與長度,加入前多等幾秒(🟡 實機量)。
