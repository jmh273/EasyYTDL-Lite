## 1. 拆分與改名

- [x] 1.1 `git archive` 複製 EasyYTDL HEAD `17db546` 的已追蹤檔案(不含 `openspec/changes/archive/`、`.agent/`、`.claude/`、`docs/`)
- [x] 1.2 套件與 applicationId 改成 `com.jmh273.easyytdllite`;App 名稱「EasyYTDL Lite」、debug「EasyYTDL Lite(測試)」;`rootProject.name`;輸出根目錄 `EasyYTDL-Lite`;版號 `0.1.0`
- [x] 1.3 刪 Google 授權、YouTube API、帳號、訂閱、最新影片、授權探測的程式與測試;主 spec 不帶 youtube-accounts、subscription-browse、latest-videos、youtube-auth-probe
- [x] 1.4 CI 拿掉 Drive 兩步,刪 Drive 腳本
- [x] 1.5 刪 `BrowseViewModel`、`BrowseStores`(下載紀錄移到 `HistoryStore.kt`)、`play-services-auth` 相依;`Video` 型別移到 `youtube/Video.kt`
- [x] 1.6 殘留舊名掃描:先對 EasyYTDL 跑同一個掃描要命中,再對 Lite 跑預期只剩刻意保留的出處說明
- [x] 1.7 驗證:`:app:testDebugUnitTest` 全綠;測試名單前後逐行比對,差異只有刪掉的 7 支授權測試加上新增的測試

## 2. 列清單(video-listing)

- [x] 2.1 `youtube/ListSource.kt`:網址分類(影片、播放清單、頻道、不認得)與 yt-dlp 參數組成;JVM 測試
- [x] 2.2 `youtube/FlatList.kt`:解析 `--flat-playlist -J` 的 JSON;JVM 測試(播放清單、頻道、搜尋、缺欄位、非影片項目)
- [x] 2.3 `service/Lister.kt`:拿讀鎖、內建版先更新一次、執行 yt-dlp、失敗分類
- [x] 2.4 `ui/ListViewModel.kt`:搜尋與網址兩個 slot、請求編號、勾選
- [x] 2.5 `ui/ListTabs.kt`(SearchTab、UrlTab)、共用的 `ui/VideoList.kt`

## 3. 畫面與導覽

- [x] 3.1 `NavState` 改成三分頁 + 設定頁;JVM 測試
- [x] 3.2 `MainActivity`:頂部分頁、標題列格式與設定按鈕、分享的分流(單支 → 佇列、清單 → 網址)
- [x] 3.3 `QueueTab` 拿掉網址框與空清單提示;`SettingsScreen` 拿掉帳號區;`OptionsSheet` 拿掉影片條件
- [x] 3.4 `strings.xml` 拿掉帳號、訂閱、最新影片的字串,加上清單字串

## 4. 守門與文件

- [x] 4.1 `mobile/scripts/check-no-google-auth.sh`(建置設定 + APK dex)含自測對照組;CI 驗證 APK 後跑
- [x] 4.2 `assembleDebug` 並以 `aapt2 dump badging` 讀 applicationId、版號、權限
- [x] 4.3 README 改寫(條款聲明、安裝、相容性、與 EasyYTDL 共存);`KIT-DIFF.md` 加「複製時改的地方」
- [x] 4.4 實機驗收(搜尋、頻道、播放清單、分享清單、下載一支 MP3)——每批 adb 操作先問使用者
- [x] 4.5 實機跑一次 `--flat-playlist -J`(頻道、播放清單、搜尋各一),把真實輸出存成 FlatListTest 的測試檔
- [x] 4.7 實機發現搜尋結果含直播中的影片:FlatList 略過 `is_live`、`is_upcoming`;spec 加情境;JVM 測試以實機輸出驗證
- [x] 4.6 使用者親手:`setup-signing.sh` 產生新簽章金鑰並設 GitHub secrets;建公開 repo、第一個 commit(作者信箱用 GitHub noreply)、push;推 `mobile-v0.1.0` tag(各步先確認)
- [x] 4.8 mobile-v0.1.0 正式版全新安裝第一次呼叫 yt-dlp 閃退(R8 改名 commons-compress 的 zip 類別,`ExtraFieldUtils` 反射失敗):Release 改回草稿、tag 保留;`proguard-rules.pro` 保留 `org.apache.commons.compress.archivers.zip.**`;新守門 `mobile/scripts/check-r8-keeps.sh` 接進 CI(壞版 APK 紅、修正版綠);全新安裝實機冒煙(搜尋、下載 MP3)通過;發 0.1.1
