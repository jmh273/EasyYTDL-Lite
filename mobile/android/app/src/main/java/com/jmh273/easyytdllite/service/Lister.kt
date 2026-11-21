package com.jmh273.easyytdllite.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.jmh273.easyytdllite.queue.Failure
import com.jmh273.easyytdllite.youtube.FlatList
import com.jmh273.easyytdllite.youtube.ListPage
import com.jmh273.easyytdllite.youtube.ListSource
import com.jmh273.easyytdllite.ytdlp.FailureClassifier
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest

/**
 * 以 `yt-dlp --flat-playlist -J` 列出影片(split-lite-and-list-sources design D2)。薄殼:參數在 [ListSource],解析在 [FlatList]。
 * 會阻塞,MUST 在背景執行緒呼叫。和下載共用 yt-dlp 執行檔:拿讀鎖,更新 yt-dlp 時等它結束。
 */
class Lister(context: Context) {
    private val app = context.applicationContext
    private val ytdlp = YtDlpManager.get(app)

    sealed class Result {
        data class Ok(val page: ListPage) : Result()
        data class Failed(val failure: Failure) : Result()
    }

    fun list(source: ListSource): Result {
        ytdlp.init()
        ytdlp.updateBundledOnce()?.let { AppNotice.post(DownloadService.describe(app, it)) }
        val request = YoutubeDLRequest(source.target())
        val options = source.options()
        var i = 0
        while (i < options.size) {
            val name = options[i]
            val value = options.getOrNull(i + 1)?.takeUnless { it.startsWith("-") }
            if (value != null) request.addOption(name, value) else request.addOption(name)
            i += if (value != null) 2 else 1
        }
        val response = try {
            ytdlp.whileRunning { YoutubeDL.getInstance().execute(request) }
        } catch (e: YoutubeDLException) {
            return Result.Failed(FailureClassifier.classify(e.message ?: e.toString(), networkAvailable()))
        } catch (e: InterruptedException) {
            return Result.Failed(Failure.OTHER)
        }
        val page = FlatList.parse(response.out) ?: return Result.Failed(FailureClassifier.classify(response.err, networkAvailable()))
        return Result.Ok(page)
    }

    private fun networkAvailable(): Boolean {
        val cm = app.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
