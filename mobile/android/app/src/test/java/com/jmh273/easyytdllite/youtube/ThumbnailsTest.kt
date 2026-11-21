package com.jmh273.easyytdllite.youtube

import org.junit.Assert.assertEquals
import org.junit.Test

class ThumbnailsTest {
    @Test
    fun `影片縮圖網址由 id 組成`() {
        assertEquals("https://i.ytimg.com/vi/jNQXAC9IVRw/mqdefault.jpg", Thumbnails.video("jNQXAC9IVRw"))
        assertEquals("https://i.ytimg.com/vi/a-b_C1/mqdefault.jpg", Thumbnails.video("a-b_C1"))
    }
}
