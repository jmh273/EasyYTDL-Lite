# app-update Specification

## Purpose
讓側載安裝的使用者知道有新版:讀 GitHub 公開 Release,有新版就提示並用瀏覽器開下載頁,App 不自行下載安裝。

## Requirements

### Requirement: 自動檢查新版

App 開啟時,距上次嘗試檢查超過 24 小時,SHALL 在背景讀取 GitHub 上本 repo 的最新正式 Release。嘗試時間 SHALL 在發出請求前記下,失敗也算一次。
自動檢查失敗(沒網路、逾時、回應讀不懂)時 MUST NOT 顯示任何訊息。
自動檢查 MUST NOT 自行下載 APK;只有使用者按「更新」才下載(見「App 內更新」)。

#### Scenario: 一天內第二次開 App

- **WHEN** 使用者今天已開過 App(已嘗試檢查),3 小時後再開
- **THEN** 不再連網檢查

#### Scenario: 沒網路

- **WHEN** 開 App 時沒有網路
- **THEN** 畫面沒有任何錯誤訊息,24 小時內不再自動檢查

### Requirement: 新版提示

最新 Release 的版號比目前版本新時,主畫面分頁列下方 SHALL 顯示「有新版 X.Y.Z」,並有「更新」與「略過」;Release 沒有可用的 APK 時,「更新」換成「前往下載」(用瀏覽器開該 Release 頁)。
「略過」SHALL 記住該版號,之後同一版不再提示;更新的版本出現時照常提示。
最新結果 SHALL 存在手機上,App 重開後不必連網也照常顯示提示。
版號比較 SHALL 依三段數字;目前版本的 `-` 後綴(例如 `-debug`)不算。任一邊讀不懂時 MUST NOT 提示。

#### Scenario: 有新版

- **WHEN** 目前是 0.2.0,最新 Release 是 `mobile-v0.2.1`
- **THEN** 主畫面顯示「有新版 0.2.1」與「更新」「略過」

#### Scenario: 已是最新

- **WHEN** 目前是 0.2.0,最新 Release 是 `mobile-v0.2.0`
- **THEN** 不顯示提示

#### Scenario: 略過後又有更新的版本

- **WHEN** 使用者略過 0.2.1,之後最新 Release 變成 0.3.0
- **THEN** 顯示「有新版 0.3.0」

#### Scenario: 網址不是 GitHub

- **WHEN** 回應的 Release 頁網址不是 `https://github.com/` 開頭
- **THEN** 視為讀不懂,不提示

#### Scenario: Release 沒有 APK

- **WHEN** 最新 Release 沒有 `app-release-v<版號>.apk`,或缺 SHA-256
- **THEN** 提示列顯示「前往下載」而不是「更新」

### Requirement: 手動檢查新版

設定頁「關於」區 SHALL 有「檢查新版」按鈕,不受 24 小時限制,也不受「略過」影響。結果 SHALL 顯示在按鈕下方:「已是最新版」、「有新版 X.Y.Z」加「更新」(沒有可用 APK 時是「前往下載」),或「檢查失敗,請稍後再試」。

#### Scenario: 手動檢查失敗

- **WHEN** 沒網路時按「檢查新版」
- **THEN** 顯示「檢查失敗,請稍後再試」

### Requirement: App 內更新

使用者按「更新」時,App SHALL 下載該 Release 的 `app-release-v<版號>.apk`,顯示下載進度;下載完成後 SHALL 比對大小與 Release 提供的 SHA-256,相符才交給系統安裝程式。不符或下載失敗時 SHALL 刪掉檔案、顯示「更新失敗」並提供「前往下載」。
App 還沒被允許「安裝不明應用程式」時,SHALL 先開啟該設定頁並提示「允許後回來再按一次更新」;已下載的檔案保留,回來再按「更新」時 MUST NOT 重新下載。
安裝由系統畫面確認;App MUST NOT 略過系統確認。只能從 `https://github.com/jmh273/EasyYTDL-Lite/releases/download/` 下載。

#### Scenario: 更新成功

- **WHEN** 目前是 0.2.1,最新 Release 是 0.2.2,使用者按「更新」並在系統畫面按「更新」
- **THEN** App 換成 0.2.2,下載紀錄與設定都還在

#### Scenario: 雜湊不符

- **WHEN** 下載完成但 SHA-256 和 Release 提供的不同
- **THEN** 不安裝,檔案刪除,顯示「更新失敗」與「前往下載」

#### Scenario: 還沒允許安裝

- **WHEN** 使用者第一次按「更新」,App 還沒被允許安裝不明應用程式
- **THEN** App 開啟「安裝不明應用程式」設定頁;使用者允許後回來按「更新」,直接出現系統安裝畫面,不重新下載
