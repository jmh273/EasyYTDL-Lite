package com.jmh273.easyytdllite.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.jmh273.easyytdllite.update.ApkAsset
import com.jmh273.easyytdllite.update.ApkVerify
import com.jmh273.easyytdllite.update.UpdateCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * App 內更新的薄殼(spec app-update「App 內更新」;add-in-app-update design D2–D4):
 * 下載 Release 的 APK、驗大小與 SHA-256、交給系統安裝程式。規則在 [UpdateCheck]、[ApkVerify]。
 */
class UpdateInstaller private constructor(context: Context) {
    private val app = context.applicationContext
    private val dir = File(app.cacheDir, "update")

    sealed class State {
        data object Idle : State()
        data class Downloading(val percent: Int) : State()
        /** 還沒被允許安裝不明應用程式:已開設定頁,檔案留著,回來再按一次。 */
        data object NeedPermission : State()
        data object Failed : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** App 開啟時:刪掉版號不大於目前版本的 APK 與沒完成的下載。 */
    fun cleanup(currentVersion: String) {
        val names = dir.list()?.toList() ?: return
        for (n in ApkVerify.stale(names, currentVersion)) File(dir, n).delete()
    }

    /** 按「更新」:已下載且驗證過就直接安裝,否則先下載。下載中再按無效。 */
    suspend fun update(asset: ApkAsset) {
        if (_state.value is State.Downloading) return
        val file = File(dir, asset.name)
        val ready = withContext(Dispatchers.IO) { file.exists() && ApkVerify.matches(file, asset) } || download(asset, file)
        if (!ready) {
            _state.value = State.Failed
            return
        }
        if (!app.packageManager.canRequestPackageInstalls()) {
            _state.value = State.NeedPermission
            val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { app.startActivity(settings) }
            return
        }
        _state.value = State.Idle
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.update", file)
        val install = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { app.startActivity(install) }.isFailure) _state.value = State.Failed
    }

    private suspend fun download(asset: ApkAsset, target: File): Boolean = withContext(Dispatchers.IO) {
        if (!asset.url.startsWith(UpdateCheck.DOWNLOAD_PREFIX)) return@withContext false
        dir.mkdirs()
        val part = File(dir, asset.name + ".part")
        _state.value = State.Downloading(0)
        val ok = runCatching {
            val conn = URL(asset.url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.instanceFollowRedirects = true
                if (conn.responseCode != 200) return@runCatching false
                conn.inputStream.use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var lastPct = -1
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (done > asset.size) return@runCatching false
                            val pct = (done * 100 / asset.size).toInt()
                            if (pct != lastPct) { lastPct = pct; _state.value = State.Downloading(pct) }
                        }
                    }
                }
                ApkVerify.matches(part, asset) && part.renameTo(target)
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false)
        if (!ok) part.delete()
        ok
    }

    companion object {
        @Volatile private var instance: UpdateInstaller? = null
        fun get(context: Context): UpdateInstaller = instance ?: synchronized(this) {
            instance ?: UpdateInstaller(context).also { instance = it }
        }
    }
}
