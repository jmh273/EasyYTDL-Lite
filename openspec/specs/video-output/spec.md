# video-output Specification

## Purpose
把 YouTube 影片存成手機本機就能播放的 MP4:依使用者選的解析度上限取影音軌、合併但不重新編碼,存到公用的 `Movies/EasyYTDL-Lite/` 每日資料夾。
## Requirements
### Requirement: 格式選擇

MP4 SHALL 依使用者選的解析度上限(1080p、720p、480p、360p 其中之一)下載。解析度 SHALL 以影片的**短邊**計算(橫式影片看高度、直式影片看寬度),和 YouTube 標示的「1080p」「720p」一致。選取順序是:
1. 不超過上限的 H.264 影片軌 + M4A(AAC)音軌;
2. 沒有符合的 H.264 時,不超過上限的任何影片軌 + M4A 音軌;
3. 再沒有時,不超過上限的任何影片軌 + 任何音軌;
4. 再沒有時,不超過上限、已經含有聲音的單一檔案。

以上都沒有時 SHALL 標記失敗,MUST NOT 改取超過上限的解析度。
影片的最高解析度低於上限時,SHALL 取它有的最高解析度。
項目的紀錄 SHALL 寫下實際取得的解析度與影片編碼。

#### Scenario: 一般影片選 720p

- **WHEN** 影片有 H.264 的 360p、720p、1080p 與 VP9 的各解析度,使用者選 720p
- **THEN** 輸出是 720p H.264 + AAC 的 MP4

#### Scenario: 影片沒有那麼高

- **WHEN** 影片最高只有 480p,使用者選 1080p
- **THEN** 輸出是 480p,不算失敗

#### Scenario: 直式影片

- **WHEN** 直式影片(例如 Shorts)有 H.264 的 1080×1920、720×1280、480×854,使用者選 1080p
- **THEN** 輸出是 1080×1920,不是 480×854

#### Scenario: 沒有 H.264

- **WHEN** 影片在上限內只有 VP9 影片軌
- **THEN** 輸出是 VP9 + AAC 的 MP4,紀錄寫明影片編碼是 VP9

### Requirement: 不重新編碼、保留原音

MP4 SHALL 只合併影音軌(重新封裝),MUST NOT 重新編碼影片或聲音,MUST NOT 調整音量(不套用目標音量)。
輸出 SHALL 把索引放在檔案開頭(可以邊讀邊播),並 SHALL 寫入標籤:標題(影片標題)、作者(頻道名稱)、日期(影片上傳日期)、註解(影片網址)。

#### Scenario: 讀取輸出檔

- **WHEN** 以 ffprobe 讀取輸出的 MP4
- **THEN** 有一條影片軌、一條聲音軌,`title`、`artist`、`date`、`comment` 都有值
- **AND** 聲音軌的取樣與 YouTube 原始音軌相同(沒有被重新編碼)

### Requirement: 存到每天的 MP4 資料夾

輸出檔 SHALL 以 MediaStore 寫到公用的 `Movies/EasyYTDL-Lite/YYYYMMDD_MP4/`(8 位數日期加 `_MP4`,例如 `20261106_MP4`),日期是處理當天的手機本地日期,而且 MUST NOT 需要儲存權限或「所有檔案存取權」。
處理過程的暫存檔 SHALL 放在 App 私有的目錄,完成或失敗後都 SHALL 刪除。

#### Scenario: 存檔成功

- **WHEN** 2026-11-06 處理完成一支 MP4
- **THEN** 檔案在 `/sdcard/Movies/EasyYTDL-Lite/20261106_MP4/`,相簿與影片播放器 App 看得到

### Requirement: MP4 檔名

檔名 SHALL 是「序號 + 底線 + 影片標題 + `.mp4`」。序號、不能用的字元與同名的處理 SHALL 和 audio-output「檔名」相同,但序號只看 MP4 每日資料夾裡的檔案,和 MP3 資料夾互不影響。

#### Scenario: 和 MP3 分開編號

- **WHEN** 當天的 MP3 資料夾已有 `01_A.mp3`–`05_E.mp3`,MP4 資料夾還沒有檔案
- **THEN** 當天第一支 MP4 的檔名是 `01_<標題>.mp4`

### Requirement: 開始前檢查剩餘空間

MP4 開始下載前,SHALL 檢查手機剩餘空間;少於 2 GB 時 MUST NOT 開始下載,這支 SHALL 標記失敗(原因「其他錯誤」),紀錄寫明是剩餘空間不足以及當時的剩餘空間。

#### Scenario: 空間不足

- **WHEN** 手機剩餘 1.5 GB,一支 MP4 要開始處理
- **THEN** 這支標記失敗,不發出下載請求;查看紀錄時寫著剩餘空間不足

