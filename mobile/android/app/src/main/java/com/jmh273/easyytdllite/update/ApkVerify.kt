package com.jmh273.easyytdllite.update

import java.io.File
import java.security.MessageDigest

/** 下載的 APK 驗證與舊檔清理(add-in-app-update design D2)。純邏輯。 */
object ApkVerify {
    private val APK_NAME = Regex("""^app-release-v(\d+\.\d+\.\d+)\.apk$""")

    fun sha256Hex(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** 大小與 SHA-256 都和 Release 提供的相同。 */
    fun matches(file: File, asset: ApkAsset): Boolean =
        file.length() == asset.size && sha256Hex(file).equals(asset.sha256, ignoreCase = true)

    /** `cacheDir/update/` 裡該刪的檔名:版號不大於 [current] 的 APK,以及沒完成的 `.part`。其他檔案不動。 */
    fun stale(names: List<String>, current: String): List<String> {
        val now = Version.parse(current)
        return names.filter { name ->
            if (name.endsWith(".part")) return@filter true
            val v = APK_NAME.matchEntire(name)?.groupValues?.get(1)?.let { Version.parse(it) } ?: return@filter false
            now != null && v <= now
        }
    }
}
