package app.fukaha

import kotlin.test.*

class UrlDiffTest {
    private fun changed(parts: List<UrlDiffPart>) = parts.filter { it.changed }.joinToString("") { it.text }

    @Test fun removedTrackingIsMarkedOnlyInTheOriginal() {
        val source = "https://instagram.com/reel/abc?utm_source=share&igsh=tracking"
        val clean = "https://instagram.com/reel/abc"
        val diff = UrlDiff.compare(source, clean)
        assertEquals("?utm_source=share&igsh=tracking", changed(diff.before))
        assertEquals("", changed(diff.after))
    }

    @Test fun embedHostAndWwwRemovalAreHighlightedWithoutMarkingThePostPath() {
        val diff = UrlDiff.compare("https://instagram.com/reel/abc", "https://vxinstagram.com/reel/abc")
        assertEquals("instagram", changed(diff.before))
        assertEquals("vxinstagram", changed(diff.after))
        assertEquals("www.", changed(UrlDiff.compare("https://www.x.com/a", "https://x.com/a").before))
    }

    @Test fun queryValuesEncodingAndUnicodeRemainExactOnBothSides() {
        val pairs = listOf(
            "https://x.com/a?tag=1&tag=2&utm_source=hi#片段" to "https://x.com/a?tag=1&tag=2#片段",
            "https://a.example/🚀/مرحبا?item=a%2Fb&n=1" to "https://b.example/🚀/مرحبا?item=a%2Fb&n=2",
            "https://youtube.com/watch?v=abc&t=42" to "https://youtu.be/abc?t=42",
            "" to "https://x.com/",
            "https://x.com/" to "",
        )
        for ((before, after) in pairs) {
            val diff = UrlDiff.compare(before, after)
            assertEquals(before, diff.before.joinToString("") { it.text })
            assertEquals(after, diff.after.joinToString("") { it.text })
        }
    }

    @Test fun identicalLinksHaveNoColoredParts() {
        val diff = UrlDiff.compare("https://x.com/a?keep=yes", "https://x.com/a?keep=yes")
        assertFalse(diff.before.any { it.changed })
        assertFalse(diff.after.any { it.changed })
    }

    @Test fun longInputUsesABoundedDiffAndPreservesSurrogatePairs() {
        val before = "https://x.com/" + "🚀".repeat(5000) + "?old=1"
        val after = before.replace("old=1", "new=2")
        val diff = UrlDiff.compare(before, after)
        assertTrue(diff.before.size <= 3)
        assertEquals(before, diff.before.joinToString("") { it.text })
        assertEquals(after, diff.after.joinToString("") { it.text })
        assertEquals("old=1", changed(diff.before))
    }
}
