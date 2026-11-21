package com.jmh273.easyytdllite.service

import android.content.Context
import com.jmh273.easyytdllite.update.LatestRelease
import com.jmh273.easyytdllite.update.UpdateCheck
import com.jmh273.easyytdllite.update.Version
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 檢查新版的薄殼(spec app-update):連 GitHub API、存上次嘗試時間與最新結果。規則在 [UpdateCheck]。
 * 存檔鍵發版後 MUST NOT 改。
 */
class UpdateChecker private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("update_check", Context.MODE_PRIVATE)

    private val _latest = MutableStateFlow(storedLatest())
    /** 最近一次成功讀到的最新 Release(存在手機上,重開 App 也在)。 */
    val latest: StateFlow<LatestRelease?> = _latest.asStateFlow()

    private val _skipped = MutableStateFlow(Version.parse(prefs.getString(KEY_SKIPPED, null)))
    val skipped: StateFlow<Version?> = _skipped.asStateFlow()

    /** App 開啟時呼叫:一天最多一次,失敗不出聲。 */
    suspend fun autoCheck() {
        val last = prefs.getLong(KEY_LAST_ATTEMPT, -1L).takeIf { it >= 0 }
        val now = System.currentTimeMillis()
        if (!UpdateCheck.shouldAutoCheck(last, now)) return
        prefs.edit().putLong(KEY_LAST_ATTEMPT, now).apply() // 發請求前就記下,失敗也算一次
        fetch()
    }

    /** 手動檢查:不受一天一次限制。回傳讀到的結果,失敗是 null。 */
    suspend fun checkNow(): LatestRelease? {
        prefs.edit().putLong(KEY_LAST_ATTEMPT, System.currentTimeMillis()).apply()
        return fetch()
    }

    fun skip(version: Version) {
        prefs.edit().putString(KEY_SKIPPED, version.toString()).apply()
        _skipped.value = version
    }

    private suspend fun fetch(): LatestRelease? = withContext(Dispatchers.IO) {
        val body = runCatching {
            val conn = URL(UpdateCheck.LATEST_API).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                if (conn.responseCode != 200) null else conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
        val rel = UpdateCheck.parse(body) ?: return@withContext null
        prefs.edit().putString(KEY_LATEST_VERSION, rel.version.toString()).putString(KEY_LATEST_URL, rel.url).apply()
        _latest.value = rel
        rel
    }

    private fun storedLatest(): LatestRelease? {
        val v = Version.parse(prefs.getString(KEY_LATEST_VERSION, null)) ?: return null
        val url = prefs.getString(KEY_LATEST_URL, null)?.takeIf { it.startsWith("https://github.com/") } ?: return null
        return LatestRelease(v, url)
    }

    companion object {
        private const val KEY_LAST_ATTEMPT = "last_attempt_ms"
        private const val KEY_SKIPPED = "skipped_version"
        private const val KEY_LATEST_VERSION = "latest_version"
        private const val KEY_LATEST_URL = "latest_url"
        private const val TIMEOUT_MS = 10_000

        @Volatile private var instance: UpdateChecker? = null
        fun get(context: Context): UpdateChecker = instance ?: synchronized(this) {
            instance ?: UpdateChecker(context).also { instance = it }
        }
    }
}
