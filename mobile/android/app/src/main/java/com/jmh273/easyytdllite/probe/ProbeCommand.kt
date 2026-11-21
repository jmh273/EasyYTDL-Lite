package com.jmh273.easyytdllite.probe

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 下載探測的 JS 執行環境模式。`tag` 用在紀錄資料夾名稱,發版後不改。 */
enum class JsMode(val tag: String) {
    /** 用函式庫預設加上的 QuickJS。 */
    QUICKJS("quickjs"),

    /** 對照組:在自訂指令加 `--no-js-runtimes`,排在函式庫的 `--js-runtimes` 之後把它清掉。 */
    CONTROL("control"),
}

/**
 * 一次探測要交給 yt-dlp 的參數。純邏輯,不碰 Android 與 youtubedl-android。
 *
 * @property options 以 `YoutubeDLRequest.addOption` 加入的選項;值為 null 表示沒有引數的旗標。
 * @property customCommands 以 `YoutubeDLRequest.addCommands` 加入的指令,排在函式庫自己加的選項之後。
 */
data class ProbeCommand(
    val options: List<Pair<String, String?>>,
    val customCommands: List<String>,
)

object ProbeCommands {
    private val FOLDER_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    fun build(mode: JsMode, outputDir: String): ProbeCommand = ProbeCommand(
        options = listOf(
            "-v" to null,
            // 分享的網址可能帶 &list=,不加會把整個播放清單算進請求額度。
            "--no-playlist" to null,
            "-x" to null,
            "--audio-format" to "mp3",
            "--write-info-json" to null,
            "-o" to "$outputDir/%(title)s [%(id)s].%(ext)s",
        ),
        customCommands = when (mode) {
            JsMode.QUICKJS -> emptyList()
            JsMode.CONTROL -> listOf("--no-js-runtimes")
        },
    )

    /** 紀錄資料夾名稱:`yyyyMMdd-HHmmss-<模式>`。 */
    fun runFolderName(time: LocalDateTime, mode: JsMode): String = runFolderName(time, mode.tag)

    /** 從分享的文字取出第一個 http(s) 網址;沒有就回傳 null。 */
    fun extractUrl(sharedText: String?): String? =
        sharedText?.let { Regex("""https?://\S+""").find(it)?.value }

    /** 非下載的動作(ffmpeg 檢查、更新、退回)用自己的標籤。 */
    fun runFolderName(time: LocalDateTime, tag: String): String = "${time.format(FOLDER_TIME)}-$tag"
}
