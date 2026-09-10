package com.sitecam.app.feature.help

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GuideSection(
    val title: String,
    val lines: List<String>
)

/** Reads the guide bundled from the single source at docs/USER_GUIDE.md. */
class UserGuideRepository(private val context: Context) {

    suspend fun loadSections(): Result<List<GuideSection>> = withContext(Dispatchers.IO) {
        runCatching {
            context.assets.open("docs/USER_GUIDE.md").bufferedReader().use { reader ->
                GuideMarkdownParser.parse(reader.readText())
            }
        }
    }
}

object GuideMarkdownParser {

    private val headingPattern = Regex("^(#{1,3})\\s+(.+?)\\s*$")
    private val orderedItemPattern = Regex("^\\s*\\d+[.)]\\s+(.+)$")

    fun parse(markdown: String): List<GuideSection> {
        val sections = mutableListOf<GuideSection>()
        var currentTitle = "使用指南"
        val currentLines = mutableListOf<String>()
        var inCodeBlock = false

        fun flush() {
            val cleaned = currentLines
                .map(::cleanLine)
                .filter { it.isNotBlank() }
            if (cleaned.isNotEmpty()) {
                sections += GuideSection(currentTitle, cleaned.toList())
            }
            currentLines.clear()
        }

        markdown.lineSequence().forEach { rawLine ->
            val line = rawLine.trimEnd()
            if (line.trim().startsWith("``")) {
                inCodeBlock = !inCodeBlock
                return@forEach
            }
            if (inCodeBlock) {
                currentLines += line
                return@forEach
            }

            val heading = headingPattern.matchEntire(line.trim())
            if (heading != null) {
                val level = heading.groupValues[1].length
                val title = cleanLine(heading.groupValues[2])
                if (level <= 2) {
                    flush()
                    currentTitle = title
                }
                return@forEach
            }
            if (line.trim() == "---") return@forEach
            currentLines += line
        }
        flush()

        return sections.ifEmpty {
            listOf(GuideSection("使用指南", listOf("暂时无法读取帮助内容，请稍后重试。")))
        }
    }

    private fun cleanLine(line: String): String {
        val trimmed = line.trim()
        if (trimmed.isBlank()) return ""
        val withoutQuote = trimmed.removePrefix("> ")
        val listLine = when {
            withoutQuote.startsWith("- ") -> "• ${withoutQuote.removePrefix("- ").trim()}"
            withoutQuote.startsWith("* ") -> "• ${withoutQuote.removePrefix("* ").trim()}"
            orderedItemPattern.matches(withoutQuote) -> {
                "• ${orderedItemPattern.matchEntire(withoutQuote)?.groupValues?.get(1).orEmpty()}"
            }
            else -> withoutQuote
        }
        return listLine
            .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
            .replace("**", "")
            .replace("__", "")
            .replace("`", "")
            .trim()
    }
}
