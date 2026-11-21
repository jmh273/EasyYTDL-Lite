# audio-output Specification

## Purpose

把下載下來的音訊做成和 Windows 版聽起來一樣大聲的 192 kbps MP3,帶有標題與頻道標籤,存到公用的 `Music/EasyYTDL-Lite/YYYYMMDD/`(檔名帶當天序號),讓其他 App 讀取。

## Requirements

### Requirement: 音量正規化到 89 dB

每個輸出檔 SHALL 依 ReplayGain 的 track gain 和使用者設定的目標音量調整音量(規則見 app-settings「目標音量」,預設 89 dB,和 Windows 版的預設相同)。
要套用的增益 SHALL 是 `track gain + (目標音量 − 89)`。
要調大聲時,套用的增益 MUST 讓調整後的峰值不超過 0.98;因此實際增益可能小於上面算出來的值,這時 SHALL 在項目的紀錄寫明。
項目的紀錄 SHALL 寫下這次使用的目標音量。

#### Scenario: 調小聲

- **WHEN** 目標音量 89 dB,來源的 track gain 是 −7.89 dB、峰值 1.20
- **THEN** 套用 −7.89 dB

#### Scenario: 調大聲但受峰值限制

- **WHEN** 目標音量 89 dB,來源的 track gain 是 +6.0 dB、峰值 0.70
- **THEN** 套用的增益讓峰值變成 0.98(約 +2.92 dB),不是 +6.0 dB

#### Scenario: 目標音量調高

- **WHEN** 目標音量 92 dB,來源的 track gain 是 −7.89 dB、峰值 1.20
- **THEN** 套用 −4.89 dB

#### Scenario: 目標音量調高但受峰值限制

- **WHEN** 目標音量 95 dB,來源的 track gain 是 +6.0 dB、峰值 0.70
- **THEN** 套用約 +2.92 dB(和目標 89 dB 時相同),紀錄寫明受峰值限制

#### Scenario: 目標音量調低

- **WHEN** 目標音量 80 dB,來源的 track gain 是 −7.89 dB、峰值 1.20
- **THEN** 套用 −16.89 dB

#### Scenario: 和 Windows 版一致

- **WHEN** 目標音量 89 dB,同一個來源分別以 Windows 版(mp3gain)與本 App 處理
- **THEN** 兩個輸出的響度相差小於 1 dB

### Requirement: 192 kbps MP3 與標籤

輸出 SHALL 是 192 kbps CBR 的 MP3,並 SHALL 寫入標籤:標題(影片標題)、歌手(頻道名稱)、日期(影片上傳日期)、註解(影片網址)。

#### Scenario: 讀取輸出檔

- **WHEN** 以 ffprobe 讀取輸出檔
- **THEN** 位元率約 192 kbps,`title`、`artist`、`date`、`comment` 標籤都有值

### Requirement: 存到每天的資料夾

輸出檔 SHALL 以 MediaStore 寫到公用的 `Music/EasyYTDL-Lite/YYYYMMDD/`(8 位數日期,不含分隔符號),日期是處理當天的手機本地日期,而且 MUST NOT 需要儲存權限或「所有檔案存取權」。
舊版建立的 `Music/EasyYTDL-Lite/YYYY-MM-DD/` 資料夾與裡面的檔案 MUST NOT 被搬移、改名或刪除。
處理過程的暫存檔 SHALL 放在 App 的快取目錄,完成或失敗後都 SHALL 刪除。

#### Scenario: 存檔成功

- **WHEN** 2026-11-05 處理完成一支影片
- **THEN** 檔案在 `/sdcard/Music/EasyYTDL-Lite/20261105/`,其他 App 透過 MediaStore 看得到

#### Scenario: 升級當天

- **WHEN** 舊版已經在 `Music/EasyYTDL-Lite/2026-11-05/` 存了檔案,同一天升級後又完成一支影片
- **THEN** 新檔案存在 `Music/EasyYTDL-Lite/20261105/`,序號是 `01`;`2026-11-05/` 裡的檔案不變

### Requirement: 檔名

檔名 SHALL 是「序號 + 底線 + 影片標題 + `.mp3`」,例如 `01_歌名.mp3`。
序號 SHALL 是當天資料夾中現有檔案的最大序號加 1;資料夾裡沒有帶序號的檔案時從 1 開始。序號 1–99 SHALL 補零成 2 位數(`01`–`99`),100 以上照實際位數(`100`、`101`…)。
只有檔名開頭是「一個以上的數字接著底線」的檔案才算帶序號,例如 `07_歌名.mp3` 的序號是 7;`歌名.mp3`、`1999 演唱會.mp3` 不算。
同時有多支影片完成時,序號 SHALL 依實際存檔的先後順序給,而且 MUST NOT 重複。
標題中檔名不能用的字元(`/ \ : * ? " < > |` 與控制字元)SHALL 換成空白並合併連續空白,過長時截短。萬一資料夾已有同名檔案,SHALL 再加上「 (2)」「 (3)」等,MUST NOT 覆寫既有檔案。

#### Scenario: 當天第一支

- **WHEN** 當天的資料夾還沒有任何檔案,處理完成一支標題是「歌名」的影片
- **THEN** 檔名是 `01_歌名.mp3`

#### Scenario: 接在最大序號後面

- **WHEN** 當天的資料夾有 `01_A.mp3`、`02_B.mp3`、`07_C.mp3`
- **THEN** 下一支的序號是 `08`

#### Scenario: 沒有序號的檔案不算

- **WHEN** 當天的資料夾只有 `歌名.mp3` 和 `1999 演唱會.mp3`
- **THEN** 下一支的序號是 `01`

#### Scenario: 超過 99

- **WHEN** 當天的資料夾最大序號是 `99`
- **THEN** 下一支的序號是 `100`,再下一支是 `101`

#### Scenario: 同時完成

- **WHEN** 同時下載的兩支影片幾乎同時完成,資料夾原本最大序號是 `03`
- **THEN** 兩個檔案的序號分別是 `04` 和 `05`,先存檔的拿 `04`

#### Scenario: 標題含不能用的字元

- **WHEN** 標題是 `A/B: "Live" ?`,當天的資料夾還沒有檔案
- **THEN** 檔名是 `01_A B Live.mp3`
