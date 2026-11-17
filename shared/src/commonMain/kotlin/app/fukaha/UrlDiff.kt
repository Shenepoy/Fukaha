package app.fukaha

data class UrlDiffPart(val text: String, val changed: Boolean)
data class UrlDiffResult(val before: List<UrlDiffPart>, val after: List<UrlDiffPart>)

/** Presentation-only diff. Joining either side always reproduces its exact input URL. */
object UrlDiff {
    private val token = Regex("[/?#&=.:]|[^/?#&=.:]+")
    private const val MAX_TOKENS = 256

    fun compare(before: String, after: String): UrlDiffResult {
        if (before == after) return UrlDiffResult(parts(before, false), parts(after, false))
        if (before.length > 8192 || after.length > 8192) return boundedDiff(before, after)
        val left = token.findAll(before).take(MAX_TOKENS + 1).map { it.value }.toList()
        val right = token.findAll(after).take(MAX_TOKENS + 1).map { it.value }.toList()
        if (left.size > MAX_TOKENS || right.size > MAX_TOKENS) return boundedDiff(before, after)
        val common = Array(left.size + 1) { IntArray(right.size + 1) }
        for (i in left.indices.reversed()) for (j in right.indices.reversed()) {
            common[i][j] = if (left[i] == right[j]) 1 + common[i + 1][j + 1]
                else maxOf(common[i + 1][j], common[i][j + 1])
        }
        val old = mutableListOf<UrlDiffPart>()
        val new = mutableListOf<UrlDiffPart>()
        var i = 0
        var j = 0
        while (i < left.size || j < right.size) {
            when {
                i < left.size && j < right.size && left[i] == right[j] -> {
                    old.append(left[i++], false)
                    new.append(right[j++], false)
                }
                i < left.size && (j == right.size || common[i + 1][j] >= common[i][j + 1]) -> old.append(left[i++], true)
                else -> new.append(right[j++], true)
            }
        }
        return UrlDiffResult(old, new)
    }

    private fun boundedDiff(before: String, after: String): UrlDiffResult {
        var prefix = 0
        while (prefix < minOf(before.length, after.length) && before[prefix] == after[prefix]) prefix++
        if (prefix > 0 && before[prefix - 1].isHighSurrogate()) prefix--
        var suffix = 0
        while (suffix < minOf(before.length, after.length) - prefix &&
            before[before.lastIndex - suffix] == after[after.lastIndex - suffix]) suffix++
        if (suffix > 0 && before[before.length - suffix].isLowSurrogate()) suffix--
        fun split(value: String) = buildList<UrlDiffPart> {
            append(value.substring(0, prefix), false)
            append(value.substring(prefix, value.length - suffix), true)
            append(value.substring(value.length - suffix), false)
        }
        return UrlDiffResult(split(before), split(after))
    }

    private fun parts(value: String, changed: Boolean) = if (value.isEmpty()) emptyList() else listOf(UrlDiffPart(value, changed))

    private fun MutableList<UrlDiffPart>.append(text: String, changed: Boolean) {
        if (text.isEmpty()) return
        if (lastOrNull()?.changed == changed) this[lastIndex] = UrlDiffPart(last().text + text, changed)
        else add(UrlDiffPart(text, changed))
    }
}
