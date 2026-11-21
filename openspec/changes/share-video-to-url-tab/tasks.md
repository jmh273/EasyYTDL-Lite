## 1. 實作

- [x] 1.1 `ListSource.SingleVideo` 可列出(目標網址、`--no-playlist`);JVM 測試
- [x] 1.2 `FlatList.parse` 解析單支影片的 JSON;JVM 測試(實機輸出)
- [x] 1.3 `ListViewModel` 單支影片列出後自動勾選
- [x] 1.4 `UrlTab` 單支影片改列出;`MainActivity` 分享一律進「網址」分頁;刪掉直接加入佇列的路徑與字串

## 2. 驗證

- [x] 2.1 `:app:testDebugUnitTest`、`assembleDebug`
- [x] 2.2 實機(先問):分享單支影片 → 網址分頁列出且已勾選、改 MP4 後加入、佇列項目是 MP4;量單支列出耗時;分享播放清單照常
- [x] 2.3 實機結果(2026-11-22,debug):分享單支影片約 13 秒列出(含每次截取畫面的延遲)、已勾選;切 MP4 720p 加入,佇列項目「MP4 720p」並下載完成;分享播放清單照常、不自動勾選。單支影片的真機輸出去掉網址後存成 `test/resources/flatlist/singlevideo.json`
- [x] 2.4 不存在的影片 id 顯示「找不到這支影片、清單或頻道」(原字串只寫清單或頻道)
