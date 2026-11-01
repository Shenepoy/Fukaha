package app.fukaha

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@OptIn(ExperimentalForeignApi::class)
class IosCacheTest {
    @Test fun successfulWriteCreatesTheFileAndEmptyMediaIsRejected() {
        val directory = NSTemporaryDirectory() + "fukaha-test-" + NSUUID().UUIDString
        val manager = NSFileManager.defaultManager
        try {
            val path = writeCacheFile(directory, "video.mp4", byteArrayOf(1, 2, 3))
            val contents = assertNotNull(manager.contentsAtPath(path))
            assertEquals(3uL, contents.length)
            assertFailsWith<IllegalArgumentException> { writeCacheFile(directory, "empty.mp4", byteArrayOf()) }
        } finally {
            manager.removeItemAtPath(directory, null)
        }
    }

    @Test fun unwritableDestinationIsAnErrorInsteadOfASuccessfulPath() {
        val directory = NSTemporaryDirectory() + "fukaha-test-" + NSUUID().UUIDString
        val manager = NSFileManager.defaultManager
        try {
            val file = writeCacheFile(directory, "file", byteArrayOf(1))
            // A regular file cannot be used as a directory, even on a writable device.
            assertFailsWith<IllegalStateException> { writeCacheFile(file, "video.mp4", byteArrayOf(1)) }
            assertFailsWith<IllegalStateException> { writeCacheFile("$file/child", "video.mp4", byteArrayOf(1)) }
        } finally {
            manager.removeItemAtPath(directory, null)
        }
    }
}
