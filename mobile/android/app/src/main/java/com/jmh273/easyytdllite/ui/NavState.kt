package com.jmh273.easyytdllite.ui

/** 主畫面的三個頂部分頁(split-lite-and-list-sources design D4)。名稱會存進 savedInstanceState。 */
enum class Tab { SEARCH, URL, QUEUE }

/** 目前的分頁,以及設定頁是否開著。純邏輯。設定頁開著時,關掉會回到原本的分頁。 */
data class NavState(val tab: Tab = Tab.SEARCH, val settingsOpen: Boolean = false) {
    fun select(t: Tab): NavState = copy(tab = t, settingsOpen = false)

    fun openSettings(): NavState = copy(settingsOpen = true)

    fun closeSettings(): NavState = copy(settingsOpen = false)

    /** 分享單支影片或點下載通知:到「佇列」。 */
    fun onShareVideo(): NavState = select(Tab.QUEUE)

    /** 分享播放清單或頻道網址:到「網址」。 */
    fun onShareList(): NavState = select(Tab.URL)

    fun encode(): String = "${tab.name}:${if (settingsOpen) 1 else 0}"

    companion object {
        /** 壞值回到預設。 */
        fun decode(s: String?): NavState {
            val parts = s?.split(":") ?: return NavState()
            if (parts.size != 2) return NavState()
            val tab = Tab.entries.firstOrNull { it.name == parts[0] } ?: return NavState()
            val settings = when (parts[1]) {
                "0" -> false
                "1" -> true
                else -> return NavState()
            }
            return NavState(tab, settings)
        }
    }
}
