package app.fukaha

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CobaltDownloadTest {
    @Test
    fun tunnelFilenameAndContentTypeSurviveTheActualDownload() = runTest {
        for ((filename, contentType, extension, mime) in listOf(
            listOf("clip.mp4", "application/octet-stream", "mp4", "video/mp4"),
            listOf("clip.mp4", "video/webm", "webm", "video/webm"),
            listOf("../../photo.png", "image/png", "png", "image/png"),
        )) {
            val directory = Files.createTempDirectory("fukaha-cobalt-test").toFile()
            val bytes = byteArrayOf(1, 2, 3, 4)
            val client = HttpClient(MockEngine { request ->
                if (request.url.encodedPath == "/") {
                    respond(
                        """{"status":"tunnel","url":"https://cobalt.test/tunnel?id=123","filename":"$filename"}""",
                        HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                } else {
                    respond(bytes, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, contentType))
                }
            }) { install(ContentNegotiation) { json() } }
            try {
                val result = assertIs<MediaDownloadResult.Success>(
                    CobaltClient(client, "https://cobalt.test").download("https://x.com/user/status/1", directory.path),
                )
                assertTrue(result.fileName.endsWith(".$extension"))
                assertEquals(mime, result.mimeType)
                val file = java.io.File(result.filePath)
                assertEquals(directory.canonicalPath, file.parentFile?.canonicalPath)
                assertTrue(bytes.contentEquals(file.readBytes()))
            } finally {
                client.close()
                directory.deleteRecursively()
            }
        }
    }

    @Test
    fun preferredFixerIsSelectedAfterShortLinkResolution() = runTest {
        val client = HttpClient(MockEngine { request ->
            if (request.url.host == "t.co") {
                respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://x.com/user/status/1"))
            } else respond("", HttpStatusCode.OK)
        }) { followRedirects = true }
        val processor = LinkProcessor(httpClient = client)
        try {
            val result = processor.prepare("https://t.co/short", FukahaSettings(
                preferredFixers = mapOf("x" to "https://twitterez.com"),
            ))
            assertEquals("https://twitterez.com/user/status/1", result?.embedUrl)
        } finally {
            processor.close()
        }
    }
}
