package com.jmh273.easyytdllite.about

/** 一個元件的授權聲明。[groups] 是它涵蓋的 Gradle group 前綴(守門用,見 OpenSourceNoticesTest)。 */
data class Notice(val name: String, val license: String, val url: String, val groups: List<String> = emptyList())

/** 「開放原始碼授權」的名單(spec app-settings;add-update-check-and-notices design D5)。 */
object OpenSourceNotices {
    const val SOURCE_URL = "https://github.com/jmh273/EasyYTDL-Lite"
    val APP = Notice("YT下載Lite", "GPL-3.0", SOURCE_URL)
    val ENTRIES: List<Notice> = listOf(
        Notice("youtubedl-android", "GPL-3.0", "https://github.com/JunkFood02/youtubedl-android", listOf("io.github.junkfood02.youtubedl-android")),
        Notice("yt-dlp", "Unlicense", "https://github.com/yt-dlp/yt-dlp"),
        Notice("Python", "PSF License", "https://www.python.org/"),
        Notice("FFmpeg", "LGPL-2.1+ / GPL-2.0+(依建置選項)", "https://ffmpeg.org/"),
        Notice("QuickJS", "MIT", "https://bellard.org/quickjs/"),
        Notice("Jackson", "Apache-2.0", "https://github.com/FasterXML/jackson"),
        Notice("Apache Commons Compress", "Apache-2.0", "https://commons.apache.org/proper/commons-compress/"),
        Notice("AndroidX、Jetpack Compose", "Apache-2.0", "https://developer.android.com/jetpack/androidx", listOf("androidx")),
        Notice("Coil", "Apache-2.0", "https://github.com/coil-kt/coil", listOf("io.coil-kt")),
        Notice("OkHttp", "Apache-2.0", "https://github.com/square/okhttp"),
        Notice("Kotlin、kotlinx.coroutines", "Apache-2.0", "https://kotlinlang.org/"),
    )
}
