package com.jmh273.easyytdllite.youtube

enum class VideoState { SELECTABLE, IN_QUEUE, DOWNLOADED }

/** 清單影片的勾選(EasyYTDL design D6,沿用)。已下載優先於已在佇列;兩者都不能勾選。 */
class VideoSelection(private val inQueue: Set<String>, private val downloaded: Set<String>) {
    fun state(id: String): VideoState = when (id) {
        in downloaded -> VideoState.DOWNLOADED
        in inQueue -> VideoState.IN_QUEUE
        else -> VideoState.SELECTABLE
    }

    fun toggle(selected: Set<String>, id: String): Set<String> = when {
        state(id) != VideoState.SELECTABLE -> selected - id
        id in selected -> selected - id
        else -> selected + id
    }

    /** 依清單順序(新到舊)回傳已勾選、而且現在仍可勾選的影片。 */
    fun toAdd(videos: List<Video>, selected: Set<String>): List<Video> =
        videos.filter { it.id in selected && state(it.id) == VideoState.SELECTABLE }

    /** 「全選」:只勾現在可以勾選的影片。 */
    fun selectAll(videos: List<Video>): Set<String> =
        videos.filter { state(it.id) == VideoState.SELECTABLE }.mapTo(LinkedHashSet()) { it.id }
}
