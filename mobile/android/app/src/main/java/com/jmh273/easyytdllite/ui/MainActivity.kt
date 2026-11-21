package com.jmh273.easyytdllite.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.jmh273.easyytdllite.R
import com.jmh273.easyytdllite.queue.MediaKind
import com.jmh273.easyytdllite.queue.QueueItem
import com.jmh273.easyytdllite.service.DownloadService
import com.jmh273.easyytdllite.service.QueueStore
import com.jmh273.easyytdllite.service.UpdateChecker
import com.jmh273.easyytdllite.settings.SettingsStore
import com.jmh273.easyytdllite.share.VideoUrl
import com.jmh273.easyytdllite.youtube.ListSource
import com.jmh273.easyytdllite.youtube.Video

/**
 * 主畫面(薄殼,spec app-navigation):三個頂部分頁(搜尋、網址、佇列),標題列有格式按鈕與設定按鈕。
 * 分頁與設定頁的規則在 [NavState];清單狀態在 [ListViewModel]。
 */
class MainActivity : ComponentActivity() {
    private lateinit var store: QueueStore

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 拒絕也照常下載,畫面會顯示提示 */ }

    private val lists: ListViewModel by viewModels()

    private var nav by mutableStateOf(NavState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 旋轉螢幕時 Activity 會重建,所在分頁與設定頁要跟著保留。
        nav = NavState.decode(savedInstanceState?.getString(KEY_NAV))
        store = QueueStore.get(this)
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        // 下載探測只在 debug 版的 manifest 裡(src/debug);release 版解析不到,就不顯示入口。
        val probe = Intent().setClassName(this, PROBE_ACTIVITY)
            .takeIf { packageManager.resolveActivity(it, 0) != null }
        handleIntent(intent)
        setContent {
            EasyYtdlTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppShell(
                        nav = nav,
                        onNav = { nav = it },
                        search = { SearchTab(lists, onEnqueue = ::enqueueVideos) },
                        banner = { UpdateBanner(versionName) },
                        url = { UrlTab(lists, onAddVideo = ::enqueue, onUnsupported = { toast(R.string.list_unsupported_url) }, onEnqueue = ::enqueueVideos) },
                        queue = { QueueTab() },
                        settings = { SettingsScreen(versionName = versionName, onOpenProbe = probe?.let { { startActivity(it) } }) },
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_NAV, nav.encode())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        // 上一個 process 留下的等待中項目:App 一開就繼續處理。
        if (store.state.value.next() != null) DownloadService.start(this)
        // 檢查新版(spec app-update):一天最多一次,失敗不出聲。
        lifecycleScope.launch { UpdateChecker.get(this@MainActivity).autoCheck() }
    }

    /** 勾選的影片:依順序加入佇列(已在佇列的略過),標題先填上清單給的。回傳加入的支數。 */
    private fun enqueueVideos(videos: List<Video>): Int {
        var added = 0
        val now = System.currentTimeMillis()
        val (kind, height) = choice()
        store.update { q ->
            videos.foldIndexed(q) { i, acc, v ->
                val r = acc.add(v.id, VideoUrl.canonical(v.id), now + i, kind, height)
                if (r.added) {
                    added++
                    r.queue.setTitle(QueueItem.keyOf(v.id, kind), v.title)
                } else {
                    r.queue
                }
            }
        }
        if (added > 0) {
            askNotificationPermission()
            DownloadService.start(this)
        }
        return added
    }

    /**
     * 分享進來(spec video-listing「分享播放清單或頻道」):有影片 id 就直接加入佇列並切到「佇列」;
     * 播放清單或頻道切到「網址」並開始列出。點下載通知切到「佇列」。
     */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            intent.action = null // 旋轉螢幕重建時不要再處理一次
            when (val source = ListSource.classify(text)) {
                is ListSource.Playlist, is ListSource.Channel -> {
                    nav = nav.onShareList()
                    lists.url.input = text.orEmpty().trim()
                    lists.load(lists.url, source)
                }
                else -> {
                    nav = nav.onShareVideo()
                    enqueue(text)
                }
            }
        } else if (intent.getBooleanExtra(EXTRA_OPEN_QUEUE, false)) {
            nav = nav.onShareVideo()
            intent.removeExtra(EXTRA_OPEN_QUEUE)
        }
    }

    /** 單支影片加入佇列;回傳是否加入成功,結果以 Toast 告知。 */
    private fun enqueue(text: String?): Boolean {
        val id = VideoUrl.videoId(text)
        if (id == null) {
            toast(R.string.queue_no_video)
            return false
        }
        var added = false
        val (kind, height) = choice()
        store.update { q -> q.add(id, VideoUrl.canonical(id), System.currentTimeMillis(), kind, height).also { added = it.added }.queue }
        if (!added) {
            toast(R.string.queue_duplicate)
            return false
        }
        toast(R.string.queue_added)
        askNotificationPermission()
        DownloadService.start(this)
        return true
    }

    /** 目前選的格式與 MP4 解析度(add-mp4-download design D3);MP3 的解析度是 null。 */
    private fun choice(): Pair<MediaKind, Int?> {
        val s = SettingsStore.get(this).state.value
        return s.kind to (if (s.kind == MediaKind.MP4) s.height else null)
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission()) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun hasNotificationPermission() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()

    companion object {
        /** 點下載通知時帶的 extra:開到「佇列」分頁。 */
        const val EXTRA_OPEN_QUEUE = "open_queue"
        private const val PROBE_ACTIVITY = "com.jmh273.easyytdllite.probe.ProbeActivity"
        private const val KEY_NAV = "nav"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppShell(
    nav: NavState,
    onNav: (NavState) -> Unit,
    search: @Composable () -> Unit,
    banner: @Composable () -> Unit,
    url: @Composable () -> Unit,
    queue: @Composable () -> Unit,
    settings: @Composable () -> Unit,
) {
    // 標題列格式按鈕打開的選項面板(move-options-to-title-menu design D2)。
    var optionsOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (nav.settingsOpen) R.string.nav_settings else R.string.app_name)) },
                navigationIcon = {
                    if (nav.settingsOpen) {
                        val label = stringResource(R.string.nav_back)
                        TextButton(onClick = { onNav(nav.closeSettings()) }, modifier = Modifier.semantics { contentDescription = label }) {
                            Text("←", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                },
                actions = {
                    if (!nav.settingsOpen) {
                        val choice = rememberFormatChoice()
                        val optionsLabel = stringResource(R.string.options_label)
                        TextButton(onClick = { optionsOpen = true }, modifier = Modifier.semantics { contentDescription = optionsLabel }) {
                            Text(formatLabel(choice.kind, choice.height) + " ⚙", style = MaterialTheme.typography.titleMedium)
                        }
                        val settingsLabel = stringResource(R.string.nav_settings)
                        TextButton(onClick = { onNav(nav.openSettings()) }, modifier = Modifier.semantics { contentDescription = settingsLabel }) {
                            Text(settingsLabel, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (nav.settingsOpen) {
                settings()
            } else {
                PrimaryTabRow(selectedTabIndex = nav.tab.ordinal) {
                    for (t in Tab.entries) {
                        Tab(selected = nav.tab == t, onClick = { onNav(nav.select(t)) }, text = { Text(stringResource(tabTitle(t))) })
                    }
                }
                banner()
                when (nav.tab) {
                    Tab.SEARCH -> search()
                    Tab.URL -> url()
                    Tab.QUEUE -> queue()
                }
            }
        }
    }
    if (optionsOpen && !nav.settingsOpen) {
        OptionsSheet { optionsOpen = false }
    }
    // 設定頁開著時,返回鍵回到原本的分頁。
    BackHandler(enabled = nav.settingsOpen) { onNav(nav.closeSettings()) }
}

private fun tabTitle(t: Tab): Int = when (t) {
    Tab.SEARCH -> R.string.tab_search
    Tab.URL -> R.string.tab_url
    Tab.QUEUE -> R.string.tab_queue
}
