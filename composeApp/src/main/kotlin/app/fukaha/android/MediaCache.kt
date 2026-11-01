package app.fukaha.android

import java.io.File

/** A chooser returning does not mean its recipient has read the granted URI. */
internal object MediaCache {
    private const val RETENTION_MS = 24L * 60 * 60 * 1000

    fun deleteExpired(directory: File, now: Long = System.currentTimeMillis()) {
        directory.listFiles()?.filter { it.isFile && now - it.lastModified() >= RETENTION_MS }
            ?.forEach { it.delete() }
    }
}
