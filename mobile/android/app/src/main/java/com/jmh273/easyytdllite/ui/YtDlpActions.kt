package com.jmh273.easyytdllite.ui

import android.content.Context
import com.jmh273.easyytdllite.R
import com.jmh273.easyytdllite.queue.Failure
import com.jmh273.easyytdllite.service.AppNotice
import com.jmh273.easyytdllite.service.DownloadService
import com.jmh273.easyytdllite.service.QueueStore
import com.jmh273.easyytdllite.service.YtDlpManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * yt-dlp 的版本、更新、退回(設定頁與佇列的失敗提示共用)。結果以 [AppNotice] 公告。
 * 下載中時更新與退回要等 yt-dlp 結束(design D7),先公告「等待下載結束後更新」。
 */
object YtDlpActions {
    suspend fun version(context: Context): String? = withContext(Dispatchers.IO) {
        runCatching { YtDlpManager.get(context).run { init(); version() } }.getOrNull()
    }

    suspend fun previous(context: Context): String? = withContext(Dispatchers.IO) {
        YtDlpManager.get(context).previousVersion()
    }

    suspend fun update(context: Context) = withContext(Dispatchers.IO) {
        val ytdlp = YtDlpManager.get(context)
        if (ytdlp.isBusy()) AppNotice.post(context.getString(R.string.settings_waiting_downloads))
        val message = runCatching {
            ytdlp.init()
            val outcome = ytdlp.update()
            if (outcome is YtDlpManager.UpdateOutcome.Updated) {
                val store = QueueStore.get(context)
                store.update { it.retryAll(Failure.YOUTUBE_REJECTED).retryAll(Failure.BOT_CHECK) }
                if (store.state.value.next() != null) DownloadService.start(context)
            }
            DownloadService.describe(context, outcome)
        }.getOrElse { it.toString() }
        AppNotice.post(message)
    }

    suspend fun revert(context: Context) = withContext(Dispatchers.IO) {
        val ytdlp = YtDlpManager.get(context)
        if (ytdlp.isBusy()) AppNotice.post(context.getString(R.string.settings_waiting_downloads))
        val message = runCatching { context.getString(R.string.ytdlp_reverted, ytdlp.revertToPrevious() ?: "?") }
            .getOrElse { it.toString() }
        AppNotice.post(message)
    }
}
