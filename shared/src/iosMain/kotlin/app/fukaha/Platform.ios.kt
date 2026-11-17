package app.fukaha

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.create
import platform.Foundation.date
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile

actual object PlatformClock {
    actual fun epochMillis(): Long = (NSDate.date().timeIntervalSince1970 * 1000.0).toLong()
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual fun writeCacheFile(cacheDirPath: String, fileName: String, bytes: ByteArray): String {
    require(bytes.isNotEmpty()) { "Empty media response" }
    val fm = NSFileManager.defaultManager
    if (!fm.fileExistsAtPath(cacheDirPath)) {
        check(fm.createDirectoryAtPath(
            cacheDirPath,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )) { "Could not create media cache" }
    }
    val path = cacheDirPath.trimEnd('/') + "/" + fileName
    bytes.usePinned { pinned ->
        val data = NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        check(data.writeToFile(path, atomically = true)) { "Could not save media file" }
    }
    return path
}

fun defaultIosCacheDir(): String = NSTemporaryDirectory() + "fukaha"
