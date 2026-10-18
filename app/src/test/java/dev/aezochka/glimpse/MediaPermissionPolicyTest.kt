package dev.aezochka.glimpse

import dev.aezochka.glimpse.ui.hasMediaAccess
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPermissionPolicyTest {
    @Test
    fun legacyAndroidRequiresStoragePermission() {
        assertTrue(hasMediaAccess(32, legacyStorageGranted = true))
        assertFalse(hasMediaAccess(32, legacyStorageGranted = false, imagesGranted = true))
    }

    @Test
    fun android13AcceptsEitherImageOrVideoPermission() {
        assertTrue(hasMediaAccess(33, imagesGranted = true))
        assertTrue(hasMediaAccess(33, videosGranted = true))
        assertFalse(hasMediaAccess(33, visualSelectedGranted = true))
        assertFalse(hasMediaAccess(33))
    }

    @Test
    fun android14AcceptsPartialVisualMediaPermission() {
        assertTrue(hasMediaAccess(34, visualSelectedGranted = true))
        assertTrue(hasMediaAccess(34, videosGranted = true))
        assertFalse(hasMediaAccess(34))
    }
}