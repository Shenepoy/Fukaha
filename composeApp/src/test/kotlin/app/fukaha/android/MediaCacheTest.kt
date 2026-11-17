package app.fukaha.android

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaCacheTest {
    @Test
    fun aLaterLaunchKeepsRecentShareFilesAndExpiresOldOnes() {
        val directory = Files.createTempDirectory("fukaha-media-test").toFile()
        try {
            val now = System.currentTimeMillis()
            val recent = directory.resolve("recent.mp4").apply { writeText("media"); setLastModified(now) }
            val old = directory.resolve("old.mp4").apply { writeText("media"); setLastModified(now - 25L * 60 * 60 * 1000) }
            MediaCache.deleteExpired(directory, now)
            assertTrue(recent.isFile, "Recipients must be able to open a file after the chooser appears")
            assertFalse(old.exists())
        } finally {
            directory.deleteRecursively()
        }
    }
}
