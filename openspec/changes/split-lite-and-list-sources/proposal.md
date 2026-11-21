## Why

朋友想用 EasyYTDL,但 EasyYTDL 需要 Google 登入與 YouTube Data API(作者自己的 Google Cloud 專案),不能給別人用。
拿掉登入之後,原本的入口只剩「分享單支影片」與「佇列頁貼單支網址」,所以第一版另外補上「貼播放清單/頻道網址」與「關鍵字搜尋」,都用 yt-dlp 列清單,不需要任何授權。
依據:`NewProjectExplore/explore/2026-11-21-easyytdl-lite.md`(使用者 2026-11-21 定案)。

## What Changes

- 從 EasyYTDL(HEAD `17db546`)複製已追蹤檔案,換成獨立 App:applicationId `com.jmh273.easyytdllite`、桌面名稱「EasyYTDL Lite」、輸出資料夾 `Music/EasyYTDL-Lite/`、`Movies/EasyYTDL-Lite/`,版號從 `0.1.0` 起算。
- **BREAKING(相對 EasyYTDL)**:移除 Google 授權、多帳號、訂閱頻道、最新影片、授權探測,以及相依 `play-services-auth`。
- 移除左側選單;主畫面改成三個頂部分頁:**搜尋**、**網址**、**佇列**。設定改由標題列按鈕開啟。
- 新增「網址」分頁:貼單支影片網址直接加入佇列;貼播放清單或頻道網址時列出影片(頻道最近 30 支)供勾選加入。
- 新增「搜尋」分頁:以關鍵字列出 20 支影片供勾選加入。
- 從其他 App 分享播放清單或頻道網址時,開到「網址」分頁並列出影片。
- CI 移除 Google Drive 上傳;只發 GitHub Release。
- 新增守門:建置設定與 APK 內不得出現 Google 授權函式庫。

## Capabilities

### New Capabilities

- `video-listing`:以 yt-dlp 列出播放清單、頻道、搜尋結果的影片,勾選後加入佇列。

### Modified Capabilities

- `app-navigation`:左側選單與帳號次選單移除,改成三個頂部分頁與標題列的設定按鈕。
- `app-settings`:設定頁移除帳號區。
- `download-queue`:加入佇列的入口改成分享與「網址」分頁;佇列分頁不再有貼網址框;格式按鈕出現在三個分頁。

### Removed Capabilities

- `youtube-accounts`、`subscription-browse`、`latest-videos`、`youtube-auth-probe`:需要 Google 授權,整份不帶(複製時已不搬主 spec)。

## Impact

- 程式:`ui/`(MainActivity、NavState、新的 SearchTab、UrlTab、ListViewModel)、`youtube/`(新的清單解析)、`service/`(列清單呼叫 yt-dlp)。
- 刪除:`GoogleAuth`、`YouTubeApi`、`YouTubeClient`、`Accounts`、`Latest`、`BrowseViewModel`、`BrowseStores`(下載紀錄移到 `HistoryStore.kt`)、`SubscriptionsScreen`、`LatestScreen`、`AuthProbe` 與對應測試。
- 發版:`.github/workflows/mobile-release.yml` 拿掉 Drive 兩步;`mobile/scripts/` 拿掉 Drive 腳本。簽章金鑰由使用者親手另建。
