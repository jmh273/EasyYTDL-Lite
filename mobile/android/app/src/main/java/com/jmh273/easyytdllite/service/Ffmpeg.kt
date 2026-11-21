package com.jmh273.easyytdllite.service

import android.content.Context
import java.io.File

/**
 * 直接執行 youtubedl-android 附帶的 ffmpeg(`nativeLibraryDir/libffmpeg.so`)。
 * 它的動態函式庫由 `FFmpeg.init` 解壓到 `noBackupFilesDir/youtubedl-android/packages/`,呼叫前 MUST 先初始化。
 */
class Ffmpeg(context: Context) {
    private val app = context.applicationContext
    private val bin = File(app.applicationInfo.nativeLibraryDir, "libffmpeg.so")
    private val libBase = File(app.noBackupFilesDir, "youtubedl-android")
    private val ldPath = listOf("packages/ffmpeg/usr/lib", "packages/python/usr/lib")
        .joinToString(":") { File(libBase, it).absolutePath }

    data class Result(val exitCode: Int, val output: String)

    /** stdout 與 stderr 合併回傳。會阻塞,MUST 在背景執行緒呼叫。 */
    fun run(args: List<String>): Result {
        val pb = ProcessBuilder(listOf(bin.absolutePath) + args).redirectErrorStream(true)
        pb.environment()["LD_LIBRARY_PATH"] = ldPath
        val process = pb.start()
        val output = process.inputStream.bufferedReader().readText()
        return Result(process.waitFor(), output)
    }
}
