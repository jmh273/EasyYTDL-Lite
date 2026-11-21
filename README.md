# EasyYTDL Lite

> ⚠️ **僅供個人使用。從 YouTube 下載可能違反其服務條款,風險自負。**

Android App:搜尋 YouTube 影片,或貼上影片、播放清單、頻道網址,下載成 MP3 或 MP4 存在手機裡,給其他播放器 App 播放。
**不需要登入、不需要任何金鑰。**

這是 EasyYTDL 的免登入版:拿掉了需要 Google 帳號的訂閱與最新影片,改用 yt-dlp 直接列出搜尋結果、播放清單與頻道影片。

## 功能

- **搜尋**:輸入關鍵字,列出 20 支影片,勾選後加入佇列。
- **網址**:貼上單支影片網址直接下載;貼上播放清單或頻道網址,列出影片(頻道列最近 30 支)勾選下載。
- **分享**:在 YouTube App 對影片按「分享」→ EasyYTDL Lite,直接加入佇列;分享播放清單或頻道則開到「網址」分頁列出。
- **MP3**:192 kbps,依 ReplayGain 正規化到設定的目標音量(預設 89 dB),存到 `Music/EasyYTDL-Lite/YYYYMMDD/`,檔名是 `nn_標題.mp3`(`nn` 是當天的序號)。
- **MP4**:解析度上限 1080p / 720p / 480p / 360p,合併影音軌但不重新編碼,存到 `Movies/EasyYTDL-Lite/YYYYMMDD_MP4/`。
- **佇列**:背景下載,同時 1–3 支(預設 2);格式與解析度在標題列的「MP3 ⚙」切換。
- **yt-dlp**:第一次下載前自動更新一次;下載失敗顯示「YouTube 拒絕」時,到「設定」按「更新 yt-dlp」。

## 安裝(給朋友)

- 需求:**Android 10 以上、64 位元(arm64)手機**。32 位元的舊手機裝不了。
- 到本 repo 的 **Releases** 頁下載最新的 `app-release-v<版號>.apk`(約 60 MB),在手機上點開安裝。
- 第一次安裝時,手機會要求允許「安裝不明應用程式」,允許瀏覽器或檔案管理員安裝即可;Play 安全防護可能跳出警告,選「仍要安裝」。
- 第一次下載時會要求通知權限:允許後可以在通知列看到進度;不允許也會照常下載。

可以和 EasyYTDL 裝在同一支手機:兩者是不同的 App,檔案存在不同資料夾。

## 建置與測試

在 `mobile/android/` 執行(需要 JDK 17 以上與 Android SDK):

```bash
./gradlew :app:testDebugUnitTest   # JVM 單元測試
./gradlew :app:assembleDebug       # debug 版:套件名 com.jmh273.easyytdllite.debug,名稱「EasyYTDL Lite(測試)」
sh ../scripts/check-no-google-auth.sh app/build.gradle app/build/outputs/apk/debug/app-debug.apk   # 不得含 Google 授權函式庫
```

版號唯一來源:`mobile/android/gradle.properties` 的 `VERSION_NAME`(versionCode = MAJOR*10000 + MINOR*100 + PATCH)。
需求的完整描述在 [openspec/specs/](openspec/specs/)。

## 發版

由 `.github/workflows/mobile-release.yml` 負責:推 `mobile-v<版號>` tag ⇒ CI 驗證、建置、驗 APK 簽章與版號、確認不含 Google 授權函式庫 ⇒ 建立 GitHub Release。
手動觸發預設 `dry_run`:只建置與驗證,不發佈。

1. 一次性設定(使用者親手,在 repo 根目錄自己的終端機執行):
   - `bash mobile/scripts/setup-signing.sh`:建立 release keystore 並設定 GitHub secrets / variables。
   - 🔴 keystore 與密碼要備份到「版控之外、本機之外」的兩處。遺失就無法再發出可覆蓋安裝的更新。
2. 每次發版:改 `VERSION_NAME` 並提交 ⇒ `git tag mobile-v<版號>` ⇒ `git push origin mobile-v<版號>`。

與 kit 範本的差異、以及從 EasyYTDL 複製時改的地方,見 [mobile/KIT-DIFF.md](mobile/KIT-DIFF.md)。

## 授權

- 本 repo 以 **GPL-3.0** 授權,全文見 [LICENSE](LICENSE)。
- 使用的 `youtubedl-android`、ffmpeg 也是 GPL-3.0;APK 的完整原始碼即本 repo,公開提供。
- 下載核心(yt-dlp 參數、youtubedl-android 版本)的修正要同步到 EasyYTDL 與 Windows 版(YT2MP3-Lite)。
