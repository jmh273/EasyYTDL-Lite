# 與 personal-dev-kit `mobile:android-release` 範本的差異

生成方式:本專案不是渲染出來的,是 2026-11-21 從 EasyYTDL(HEAD `17db546`)**複製**的(見文末「複製時改的地方」)。
EasyYTDL 當初於 2026-11-04 執行 `render_template.py --feature drive` 生成;純原生 Kotlin + Compose,不使用 Capacitor 與 Node。改法沿用 EasyToggle 的 `mobile/KIT-DIFF.md`(同一套 `native-android-variant.md`)。
原始碼中標有 `[KIT-DIFF]` 的註解,都和這裡的條目一一對應。

| # | 檔案 | 範本 | 這裡 | 理由 |
| --- | --- | --- | --- | --- |
| 1 | `.github/workflows/mobile-release.yml` | `setup-node` 步驟 | 刪除 | 沒有 web 層 |
| 2 | 同上 | `npm ci`、`npm run build`、`npx cap sync android` | 刪除 | 同上 |
| 3 | 同上 | 版號用 `node -p` 讀 `mobile/package.json` | 用 `grep` 讀 `mobile/android/gradle.properties` 的 `VERSION_NAME`;必須恰好一行 | CI 不需要 Node |
| 4 | `mobile/android/app/build.gradle` | `apply plugin` + `JsonSlurper` 讀 `package.json` | `plugins {}` 套用 AGP、Kotlin、Compose 編譯器;改讀 `VERSION_NAME` | 同 3 |
| 5 | 同上 | 版號格式檢查(`[0-9]+\.[0-9]+\.[0-9]+`) | 判準不變,只改錯誤訊息裡的來源檔名 | 新版範本已內建檢查(EasyToggle 當時是自己補的) |
| 6 | 同上 | `aaptOptions.ignoreAssetsPattern`(web 資產) | 刪除;加上 `buildFeatures { compose true }` | 沒有 web 資產 |
| 7 | 同上 | `compileOptions` 只有 encoding | 加上 Java 17 source/target,以及 `kotlin { compilerOptions { jvmTarget = JVM_17 } }` | Kotlin 與 Java 的目標版本要一致 |
| 8 | 同上 | Capacitor 相依、`flatDir`、`capacitor.build.gradle`、google-services | 刪除;換成 Compose(BOM、activity-compose、lifecycle-runtime-compose)、core-ktx、junit,再加上 App 本身的相依(youtubedl-android、Coil 3.3.0) | 純原生 |
| 9 | `mobile/android/`(root) | 範本沒有提供(來自 Capacitor 生成的專案) | 自己寫 `settings.gradle`、`build.gradle`(`ext` 提供 `rootProject.ext.*`)、`gradle.properties`;wrapper 從 EasyToggle 複製 | 範本假設 root 由 `cap add android` 產生 |
| 10 | `.github/workflows/mobile-release.yml` | `--feature drive` 的 Drive 前置檢查與上傳兩步 | 刪除,`mobile/scripts/` 的 Drive 腳本一併刪除 | Lite 版給朋友,只發 GitHub Release;Drive 要用作者的 Google 授權 |
| 11 | 同上 | 沒有 | 驗證 APK 之後加「Verify no Google auth library」(`mobile/scripts/check-no-google-auth.sh`,含自測) | Lite 版不得含 Google 授權函式庫 |

## 不是範本差異、而是 App 本身需要的設定(`app/build.gradle`)

- `abiFilters 'arm64-v8a'`:youtubedl-android 附的 python、ffmpeg、QuickJS 是原生執行檔,手機是 arm64。
- `useLegacyPackaging = true`(搭配 manifest 的 `extractNativeLibs="true"`):youtubedl-android 直接執行 `nativeLibraryDir` 裡的 `lib*.so`。
- release 開啟 R8(`minifyEnabled true` + `proguard-rules.pro`,0.3.0 起);debug 不開。

## 版本組合(沿用 EasyToggle 2026-11-03 的讀數,本專案 2026-11-04 建置通過)

- AGP 8.13.0 + Gradle 8.14.3 + Kotlin 2.2.20;JDK 21(本機)。
- Compose BOM `2026.06.01`:`2026.08.00` 起是 Compose 1.12,要求 compileSdk 37,而 AGP 8.13 最高只支援 36。
- compileSdk 36、targetSdk 35、minSdk 29(MediaStore `RELATIVE_PATH` 寫入 `Music/`、`Movies/` 不需要儲存權限)。
- Coil 固定 3.3.0:3.4.0 起需要 Kotlin 標準函式庫 2.3+,升 Kotlin 時再一起升。
- 🔴 三者連動:升 Compose BOM 前先查它要求的 compileSdk 與 AGP 支援上限。

## 已知外部干擾(來自 EasyToggle)

- `gradle/wrapper/gradle-wrapper.properties` 曾被不明工具(疑為 IDE 的 Gradle/Java 擴充)改寫成 Gradle 9.x,導致所有任務在套用外掛時失敗。
  建置突然失敗而沒改過設定時,先看 `git diff -- mobile/android/gradle/wrapper/gradle-wrapper.properties`。

## 複製時改的地方(2026-11-21,來源 EasyYTDL `17db546`)

| 項目 | EasyYTDL | EasyYTDL-Lite |
| --- | --- | --- |
| applicationId 與 namespace | `com.jmh273.easyytdl` | `com.jmh273.easyytdllite`(🔴 發版後不能改) |
| 套件目錄 | `com/jmh273/easyytdl/` | `com/jmh273/easyytdllite/`(main、debug、test 三處) |
| `rootProject.name` | `EasyYTDL` | `EasyYTDL-Lite` |
| App 顯示名 | `EasyYTDL`、debug `EasyYTDL(測試)` | `EasyYTDL Lite`、debug `EasyYTDL Lite(測試)` |
| 輸出根目錄(`Pipeline.ROOT_FOLDER`) | `EasyYTDL` | `EasyYTDL-Lite`(`Music/EasyYTDL-Lite/`、`Movies/EasyYTDL-Lite/`) |
| yt-dlp 上一版的存放目錄 | `noBackupFilesDir/easyytdl` | `noBackupFilesDir/easyytdllite` |
| 版號 | `0.7.1` | `0.1.0` |
| 簽章腳本 `PACKAGE_ID` | `com.jmh273.easyytdl` | `com.jmh273.easyytdllite`;金鑰由使用者另建,不共用 |
| 資源前綴 | 沿用(字串前綴規則見 `strings.xml` 開頭) | 拿掉 `subs_`、`account_`、`latest_`,加 `list_` |
| 偏好與存檔檔名 | `queue.json`、`history.json`、`ytdlp_versions` 等 | 沿用同名:各自在自己的 App 私有目錄,不互相衝突 |
| 圖示 | 靛藍 → 紫漸層 + 音符與下載箭頭 | 前景與 monochrome 沿用;背景改成青綠 → 藍綠漸層以便區分(🟡 暫定,待使用者確認方向) |
| 帶走的領域程式 | — | 不帶 Google 授權、YouTube Data API、帳號、訂閱、最新影片、授權探測與它們的 7 支測試 |

殘留舊名掃描:同一個掃描(`com.jmh273.easyytdl` 後面不接 `lite`、`EasyYTDL` 後面不接 `-Lite`/` Lite`)對 EasyYTDL 命中 248 處;對本專案只剩刻意保留的出處說明(註解與 openspec 文件裡的「沿用 EasyYTDL」)。
