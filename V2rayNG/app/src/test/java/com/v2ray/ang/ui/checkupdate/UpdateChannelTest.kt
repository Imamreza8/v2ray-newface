package com.v2ray.ang.ui.checkupdate

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UpdateChannelTest {
    @Test
    fun onlyNewfaceUsesItsOwnUpdatePage() {
        assertTrue(usesNewfaceUpdates("newface"))
        assertFalse(usesNewfaceUpdates("fdroid"))
        assertFalse(usesNewfaceUpdates("playstore"))
        assertFalse(usesNewfaceUpdates(""))
    }
}
