package com.jmh273.easyytdllite.storage

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 下載檔的存放位置:`Music/<根目錄>/YYYYMMDD/`(以下載當天日期命名)。
 * 回傳 MediaStore `RELATIVE_PATH` 用的相對路徑,結尾帶斜線。純邏輯,不碰 Android。
 */
object DailyFolder {
    private val DAY: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE

    /** MP4:`Movies/<根目錄>/YYYYMMDD_MP4/`(add-mp4-download design D5)。 */
    fun videoPath(rootName: String, date: LocalDate): String = "Movies/${checked(rootName)}/${date.format(DAY)}_MP4/"

    fun relativePath(rootName: String, date: LocalDate): String = "Music/${checked(rootName)}/${date.format(DAY)}/"

    private fun checked(rootName: String): String {
        val root = rootName.trim()
        require(root.isNotEmpty() && root != "." && root != ".." && '/' !in root && '\\' !in root) {
            "根目錄名稱不可空白,也不可含路徑分隔字元:'$rootName'"
        }
        return root
    }
}
