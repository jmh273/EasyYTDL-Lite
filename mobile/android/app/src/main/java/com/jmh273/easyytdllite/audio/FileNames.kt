package com.jmh273.easyytdllite.audio

/** 由影片標題決定檔名。純邏輯。 */
object FileNames {
    /** 檔名主體(不含副檔名)的 UTF-8 位元組上限;檔案系統上限是 255 位元組,留空間給序號與副檔名。 */
    const val MAX_STEM_BYTES = 200

    private val ILLEGAL = Regex("""[/\\:*?"<>|\p{Cntrl}]""")
    private val SPACES = Regex("""\s+""")
    private val SEQUENCE = Regex("""^(\d+)_""")

    /** 去掉不能用的字元、合併空白、截短;結果為空時用 [fallback]。 */
    fun stem(title: String?, fallback: String): String {
        val cleaned = (title ?: "")
            .replace(ILLEGAL, " ")
            .replace(SPACES, " ")
            .trim()
            .trimStart('.')
            .trim()
        return truncate(cleaned.ifEmpty { fallback })
    }

    /** 以 UTF-8 位元組截短,不切斷字元(含代理對)。 */
    private fun truncate(s: String): String {
        if (s.toByteArray(Charsets.UTF_8).size <= MAX_STEM_BYTES) return s
        val sb = StringBuilder()
        var bytes = 0
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val chunk = String(Character.toChars(cp))
            val n = chunk.toByteArray(Charsets.UTF_8).size
            if (bytes + n > MAX_STEM_BYTES) break
            sb.append(chunk)
            bytes += n
            i += Character.charCount(cp)
        }
        return sb.toString().trim()
    }

    /** 檔名開頭「數字 + 底線」的序號;不符合回傳 null。 */
    fun sequenceOf(name: String): Int? = SEQUENCE.find(name)?.groupValues?.get(1)?.toIntOrNull()

    /** 現有檔名中最大序號加 1;沒有帶序號的檔案時回傳 1。 */
    fun nextSequence(existing: Set<String>): Int = (existing.mapNotNull(::sequenceOf).maxOrNull() ?: 0) + 1

    /** `nn_<stem>`:1–99 補成 2 位數,100 以上照實際位數。 */
    fun numbered(stem: String, n: Int): String = "%02d_%s".format(java.util.Locale.ROOT, n, stem)

    /** `<stem>.<ext>`;[existing] 已有同名檔時加「 (2)」「 (3)」…(比對不分大小寫)。 */
    fun unique(stem: String, existing: Set<String>, ext: String = "mp3"): String {
        val taken = existing.map { it.lowercase() }.toSet()
        var name = "$stem.$ext"
        var n = 2
        while (name.lowercase() in taken) {
            name = "$stem ($n).$ext"
            n++
        }
        return name
    }
}
