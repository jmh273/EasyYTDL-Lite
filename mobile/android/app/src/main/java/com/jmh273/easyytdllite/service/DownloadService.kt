package com.jmh273.easyytdllite.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.jmh273.easyytdllite.R
import com.jmh273.easyytdllite.queue.Failure
import com.jmh273.easyytdllite.queue.MediaKind
import com.jmh273.easyytdllite.video.MergeProgress
import com.jmh273.easyytdllite.queue.QueueItem
import com.jmh273.easyytdllite.queue.Status
import com.jmh273.easyytdllite.settings.SettingsStore
import com.jmh273.easyytdllite.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 下載佇列的前景服務(design D8,類型 dataSync)。薄殼:狀態變化都由 [com.jmh273.easyytdllite.queue.Queue] 的純函式產生。
 * 有等待中的項目時由畫面啟動;佇列處理完就結束。
 */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private lateinit var store: QueueStore
    private lateinit var ytdlp: YtDlpManager
    private lateinit var connectivity: ConnectivityManager
    private var lastNotify = 0L

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            store.update { it.networkRestored() }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        store = QueueStore.get(this)
        ytdlp = YtDlpManager.get(this)
        connectivity = getSystemService(ConnectivityManager::class.java)
        createChannel()
        connectivity.registerDefaultNetworkCallback(networkCallback)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification(getString(R.string.notif_preparing), null, null), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (job?.isActive != true) job = scope.launch { processQueue() }
        return START_NOT_STICKY
    }

    /** 正在處理的項目:id → 標題(還沒取得時是網址)。 */
    private val running = ConcurrentHashMap<String, String>()
    private val progress = ConcurrentHashMap<String, Float>()
    private val doneThisRun = AtomicInteger(0)

    /** 有工作結束時通知排程迴圈,不必忙等。 */
    private val finished = Channel<Unit>(Channel.CONFLATED)

    /** 排程迴圈(add-drawer-and-parallel-downloads design D5):最多同時 N 支,依加入順序開始。 */
    private suspend fun processQueue() {
        ytdlp.init()
        if (ytdlp.needsFirstUpdate()) show(getString(R.string.notif_updating_ytdlp), null, null, force = true)
        ytdlp.updateBundledOnce()?.let { AppNotice.post(describe(this, it)) }
        // 上一個 process 留下的暫存;這時還沒有任何工作在跑。
        Pipeline.workRoot(this).deleteRecursively()
        val pipeline = Pipeline(this, ytdlp, ::networkAvailable)
        val settings = SettingsStore.get(this)
        doneThisRun.set(0)
        while (scope.isActive) {
            // 沒有網路時不開始新的一支:否則每支都會立刻失敗,白白用掉一次網路重試(spike 4 實測發現)。
            if (networkAvailable()) {
                while (true) {
                    var claimed: QueueItem? = null
                    store.update { q ->
                        val (next, item) = q.claimNext(running.size, settings.state.value.concurrency)
                        claimed = item
                        next
                    }
                    val item = claimed ?: break
                    running[item.key] = item.title ?: item.url
                    // 目標音量在開始處理時讀:改設定從下一支生效(spec app-settings「目標音量」)。
                    val targetDb = settings.state.value.targetDb
                    scope.launch { work(pipeline, item, targetDb) }
                }
            }
            val queue = store.state.value
            val waitingNetwork = queue.items.any { it.status == Status.WAITING_NETWORK }
            if (running.isEmpty() && queue.next() == null && !waitingNetwork) break
            if (running.isEmpty() && !networkAvailable()) {
                show(getString(R.string.notif_waiting_network), null, null)
            } else {
                if (waitingNetwork && networkAvailable()) store.update { it.networkRestored() }
                showProgress(force = true)
            }
            withTimeoutOrNull(3_000) { finished.receive() }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun work(pipeline: Pipeline, item: QueueItem, targetDb: Int) {
        var merge = MergeProgress()
        try {
            val outcome = pipeline.run(
                item,
                targetDb,
                onTitle = { title ->
                    running[item.key] = title
                    store.update { it.setTitle(item.key, title) }
                },
                onProgress = { p ->
                    // MP4 先下載影片軌再下載音軌,進度會跑兩次 0–100(add-mp4-download design D6)。
                    progress[item.key] = if (item.kind == MediaKind.MP4) {
                        merge = merge.next(p)
                        merge.shown
                    } else {
                        p
                    }
                    showProgress()
                },
            )
            when (outcome) {
                is Pipeline.Outcome.Done -> {
                    store.update { it.complete(item.key, outcome.savedPath) }
                    HistoryStore.get(this).add(item.id, item.kind, outcome.uri)
                }
                is Pipeline.Outcome.Failed ->
                    if (outcome.failure == Failure.NETWORK) store.update { it.failNetwork(item.key) }
                    else store.update { it.fail(item.key, outcome.failure) }
            }
            doneThisRun.incrementAndGet()
        } finally {
            running.remove(item.key)
            progress.remove(item.key)
            finished.trySend(Unit)
        }
    }

    /** 「下載中 N 支 · 已完成 x / 共 y」;進度條是整批的進度(已完成的算 100%)。 */
    private fun showProgress(force: Boolean = false) {
        val active = running.size
        if (active == 0) return
        val done = doneThisRun.get()
        val remaining = store.state.value.items.count { it.status in ACTIVE }
        val total = (done + remaining).coerceAtLeast(1)
        val overall = (done * 100f + running.keys.sumOf { (progress[it] ?: 0f).toDouble() }.toFloat()) / total
        show(
            getString(R.string.notif_parallel_title, active, done, total),
            running.values.joinToString("、"),
            overall,
            force,
        )
    }

    /** Android 15:dataSync 的累計時間用完,系統要求停止。 */
    override fun onTimeout(startId: Int, fgsType: Int) {
        store.update { it.recoverAfterRestart() }
        AppNotice.post(getString(R.string.notice_system_paused))
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
        scope.cancel()
        // 服務被結束時,處理中的項目回到等待中;下次開 App 會繼續。
        store.update { it.recoverAfterRestart() }
        super.onDestroy()
    }

    private fun networkAvailable(): Boolean {
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** 進度回呼很頻繁(而且來自多個工作),最多每 0.5 秒更新一次通知。 */
    @Synchronized
    private fun show(title: String, text: String?, progress: Float?, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastNotify < 500) return
        lastNotify = now
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(title, text, progress))
    }

    private fun notification(title: String, text: String?, progress: Float?): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(MainActivity.EXTRA_OPEN_QUEUE, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .apply { if (progress != null) setProgress(100, progress.toInt().coerceIn(0, 100), progress <= 0f) }
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "download"
        private const val NOTIFICATION_ID = 1
        private val ACTIVE = setOf(Status.WAITING, Status.RUNNING, Status.WAITING_NETWORK)

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
        }

        fun describe(context: Context, outcome: YtDlpManager.UpdateOutcome): String = when (outcome) {
            is YtDlpManager.UpdateOutcome.Updated -> context.getString(R.string.ytdlp_updated, outcome.from ?: "?", outcome.to)
            is YtDlpManager.UpdateOutcome.AlreadyLatest -> context.getString(R.string.ytdlp_already_latest, outcome.version ?: "?")
            is YtDlpManager.UpdateOutcome.Restored -> context.getString(R.string.ytdlp_restored, outcome.version ?: "?")
            is YtDlpManager.UpdateOutcome.Failed -> context.getString(R.string.ytdlp_update_failed, outcome.reason)
        }
    }
}
