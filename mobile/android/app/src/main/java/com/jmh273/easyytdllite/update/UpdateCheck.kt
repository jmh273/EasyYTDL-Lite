package com.jmh273.easyytdllite.update

import org.json.JSONObject

/** 三段版號。 */
data class Version(val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {
    override fun compareTo(other: Version): Int = compareValuesBy(this, other, Version::major, Version::minor, Version::patch)
    override fun toString() = "$major.$minor.$patch"

    companion object {
        private val THREE = Regex("""^(\d{1,4})\.(\d{1,4})\.(\d{1,4})$""")

        /** 「0.2.0」「0.2.0-debug」;`-` 之後的後綴不算。不是三段數字時回傳 null。 */
        fun parse(text: String?): Version? {
            val core = text?.trim()?.substringBefore('-') ?: return null
            val m = THREE.matchEntire(core) ?: return null
            val (a, b, c) = m.destructured
            return Version(a.toInt(), b.toInt(), c.toInt())
        }
    }
}

/** Release 上可以 App 內更新的 APK(add-in-app-update design D1)。 */
data class ApkAsset(val name: String, val url: String, val size: Long, val sha256: String)

/** [apk] 是 null 時沒有可用的 APK,只能「前往下載」。 */
data class LatestRelease(val version: Version, val url: String, val apk: ApkAsset? = null)

sealed class ManualResult {
    data object UpToDate : ManualResult()
    data class NewVersion(val release: LatestRelease) : ManualResult()
    data object Failed : ManualResult()
}

/** 檢查新版的規則(spec app-update;add-update-check-and-notices design D1–D4)。純邏輯。 */
object UpdateCheck {
    const val LATEST_API = "https://api.github.com/repos/jmh273/EasyYTDL-Lite/releases/latest"
    const val DAY_MS = 24L * 60 * 60 * 1000
    private const val TAG_PREFIX = "mobile-v"
    private const val URL_PREFIX = "https://github.com/"
    const val DOWNLOAD_PREFIX = "https://github.com/jmh273/EasyYTDL-Lite/releases/download/"
    private val SHA256 = Regex("""^[0-9a-fA-F]{64}$""")
    private val TAG_VERSION = Regex("""^\d+\.\d+\.\d+$""")

    /** `releases/latest` 的回應:tag 要是 `mobile-v` 加三段數字,網址要是 GitHub 的頁面;否則 null。 */
    fun parse(json: String?): LatestRelease? {
        val root = runCatching { JSONObject(json ?: return null) }.getOrNull() ?: return null
        val tag = root.optString("tag_name")
        if (!tag.startsWith(TAG_PREFIX)) return null
        val number = tag.removePrefix(TAG_PREFIX)
        if (!TAG_VERSION.matches(number)) return null
        val version = Version.parse(number) ?: return null
        val url = root.optString("html_url")
        if (!url.startsWith(URL_PREFIX)) return null
        return LatestRelease(version, url, apkAsset(root, version))
    }

    /** design D1:名稱、下載網址、大小、SHA-256 都合格的 `app-release-v<版號>.apk`;沒有就是 null。 */
    private fun apkAsset(root: JSONObject, version: Version): ApkAsset? {
        val assets = root.optJSONArray("assets") ?: return null
        val wanted = "app-release-v$version.apk"
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            if (a.optString("name") != wanted) continue
            val dl = a.optString("browser_download_url")
            if (!dl.startsWith(DOWNLOAD_PREFIX) || !dl.endsWith("/$wanted")) return null
            val size = a.optLong("size", 0)
            if (size <= 0) return null
            val sha = a.optString("digest").removePrefix("sha256:").takeIf { a.optString("digest").startsWith("sha256:") } ?: return null
            if (!SHA256.matches(sha)) return null
            return ApkAsset(wanted, dl, size, sha.lowercase())
        }
        return null
    }

    /** 從沒試過、超過一天,或上次時間在未來(時鐘被調回)時要檢查。 */
    fun shouldAutoCheck(lastAttemptMs: Long?, nowMs: Long): Boolean =
        lastAttemptMs == null || nowMs < lastAttemptMs || nowMs - lastAttemptMs >= DAY_MS

    /** 主畫面要提示的版本:比目前新、且不是被略過的那一版;否則 null。 */
    fun banner(current: String, latest: LatestRelease?, skipped: Version?): LatestRelease? {
        val now = Version.parse(current) ?: return null
        val rel = latest ?: return null
        if (rel.version <= now) return null
        if (skipped != null && rel.version <= skipped) return null
        return rel
    }

    fun manual(current: String, latest: LatestRelease?): ManualResult {
        val rel = latest ?: return ManualResult.Failed
        val now = Version.parse(current) ?: return ManualResult.UpToDate
        return if (rel.version > now) ManualResult.NewVersion(rel) else ManualResult.UpToDate
    }
}
