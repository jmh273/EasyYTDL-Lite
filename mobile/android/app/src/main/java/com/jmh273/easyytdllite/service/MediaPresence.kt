package com.jmh273.easyytdllite.service

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 「檔案還在嗎」(add-gain-numbering-and-history-v2 design D5):一次查出根目錄底下所有輸出檔的 content URI。
 * MP3 在 Audio 集合的 `Music/EasyYTDL-Lite/`,MP4 在 Video 集合的 `Movies/EasyYTDL-Lite/`(add-mp4-download design D7)。
 * 沒有 READ_MEDIA_*,只看得到 App 自己寫的檔案——剛好就是下載紀錄裡會有的那些。
 * 任一個查詢失敗時回傳 null(MUST NOT 回傳空集合:那會讓所有紀錄看起來都「檔案不在」),呼叫端沿用上一次的結果。
 */
object MediaPresence {
    suspend fun existingUris(context: Context): Set<String>? = withContext(Dispatchers.IO) {
        // 和 Pipeline 存檔用同一個集合,組出來的 URI 字串才會和 insert 回傳的一致。
        val audio = query(context, MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), "Music") ?: return@withContext null
        val video = query(context, MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), "Movies") ?: return@withContext null
        audio + video
    }

    private fun query(context: Context, collection: Uri, top: String): Set<String>? = runCatching {
        val result = HashSet<String>()
        val cursor = context.applicationContext.contentResolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
            arrayOf("$top/${Pipeline.ROOT_FOLDER}/%"),
            null,
        ) ?: return@runCatching null
        cursor.use { c ->
            while (c.moveToNext()) result += ContentUris.withAppendedId(collection, c.getLong(0)).toString()
        }
        result
    }.getOrNull()
}
