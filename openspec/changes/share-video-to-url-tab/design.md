## Decisions

### D1 單支影片用 yt-dlp 讀標題

`yt-dlp -J --flat-playlist --no-playlist https://www.youtube.com/watch?v=<id>`。回應是一支影片的物件(`_type: video`,沒有 `entries`);`FlatList.parse` 遇到沒有 `entries`、但根物件的 `id` 是 11 字元影片 id 時,當成只有一支的清單,清單標題留空(畫面已有影片標題)。直播中與尚未開播的照 video-listing 規則不列出。
不用「只顯示 id 與縮圖、不讀標題」:列表沒有標題,使用者無法確認分享的是哪一支。

### D2 自動勾選

單支影片列出後自動勾選,省一步;已下載或已在佇列的照既有規則不能勾選。勾選邏輯沿用 `VideoSelection.selectAll`。

### D3 分享一律進「網址」分頁

`MainActivity.handleIntent` 不再呼叫直接加入佇列;所有分享文字填入「網址」輸入框,能列出就列出。原本的 `enqueue(text)` 與只給它用的字串刪除。
