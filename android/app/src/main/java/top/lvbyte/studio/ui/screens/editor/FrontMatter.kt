package top.lvbyte.studio.ui.screens.editor

import top.lvbyte.studio.util.TimeUtils

data class FrontMatterData(
    val title: String = "",
    val date: String = "",
    val updated: String = "",
    val abbrlink: String = "",
    val description: String = "",
    val cover: String = "",
    val tags: String = "",
    val categories: String = "",
    val activeMenu: String = "post"
)

data class DocParts(
    val frontMatter: String,
    val body: String,
    val fields: Map<String, String>
)

object FrontMatter {
    private val knownOrder = listOf(
        "title", "date", "updated", "abbrlink", "active_menu",
        "description", "cover", "tags", "categories"
    )

    private val topLevelKey = Regex("^([A-Za-z0-9_-]+):(.*)$")

    fun split(raw: String): DocParts {
        val normalized = raw.replace("\r\n", "\n")
        val lines = normalized.split("\n")
        if (lines.firstOrNull()?.trim() == "---") {
            val end = (1 until lines.size).firstOrNull { lines[it].trim() == "---" }
            if (end != null) {
                val front = lines.subList(0, end + 1).joinToString("\n")
                val body = if (end + 1 < lines.size) lines.subList(end + 1, lines.size).joinToString("\n") else ""
                return DocParts(front, body, fields(front))
            }
        }
        return DocParts("", normalized, emptyMap())
    }

    fun fields(frontMatter: String): Map<String, String> {
        val map = linkedMapOf<String, String>()
        val inner = delimitedLines(frontMatter)
        for (line in inner) {
            val match = topLevelKey.find(line) ?: continue
            val key = match.groupValues[1]
            val rawValue = match.groupValues[2].trim()
            map[key] = rawValue
        }
        return map
    }

    fun dataFromFields(frontMatter: String): FrontMatterData {
        val fields = fields(frontMatter)
        fun scalar(key: String): String {
            val raw = fields[key] ?: return ""
            if (raw == ">" || raw == ">-" || raw == "|" || raw == "|-") {
                val lines = delimitedLines(frontMatter)
                val idx = lines.indexOfFirst { it.startsWith("$key:") }
                if (idx >= 0) {
                    val child = mutableListOf<String>()
                    for (i in idx + 1 until lines.size) {
                        val line = lines[i]
                        if (line.isBlank()) continue
                        if (!line.startsWith(" ") && !line.startsWith("\t")) break
                        child += line.trim()
                    }
                    return child.joinToString(" ")
                }
            }
            return unquote(raw)
        }

        return FrontMatterData(
            title = scalar("title"),
            date = scalar("date"),
            updated = scalar("updated"),
            abbrlink = scalar("abbrlink"),
            description = scalar("description"),
            cover = scalar("cover"),
            tags = listValues(frontMatter, "tags").joinToString(", "),
            categories = listValues(frontMatter, "categories").joinToString(", "),
            activeMenu = scalar("active_menu").ifBlank { "post" }
        )
    }

    fun newPost(
        title: String,
        abbrlink: String,
        tags: String,
        categories: String,
        description: String,
        cover: String
    ): String {
        val now = TimeUtils.frontMatterNow()
        val data = FrontMatterData(
            title = title,
            date = now,
            updated = now,
            abbrlink = abbrlink,
            description = description,
            cover = cover,
            tags = tags,
            categories = categories,
            activeMenu = "post"
        )
        return update("---\n---\n", data) + "\n"
    }

    fun update(frontMatter: String, data: FrontMatterData): String {
        val blocks = parseBlocks(frontMatter)
        val out = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for ((key, block) in blocks) {
            if (key in knownOrder) {
                if (seen.add(key)) {
                    renderBlock(key, data)?.let { out += it }
                }
            } else {
                out += block.joinToString("\n")
            }
        }
        for (key in knownOrder) {
            if (key !in seen) {
                renderBlock(key, data)?.let { out += it }
            }
        }
        val body = out.filter { it.isNotBlank() }.joinToString("\n")
        return "---\n$body\n---\n"
    }

    fun listValues(frontMatter: String, key: String): List<String> {
        val fields = fields(frontMatter)
        val raw = fields[key]?.trim().orEmpty()
        if (raw.isEmpty()) {
            // 可能是块状列表
            val lines = delimitedLines(frontMatter)
            val idx = lines.indexOfFirst { it.startsWith("$key:") }
            if (idx < 0) return emptyList()
            val values = mutableListOf<String>()
            for (i in idx + 1 until lines.size) {
                val line = lines[i]
                if (line.isBlank()) continue
                if (!line.startsWith(" ") && !line.startsWith("\t")) break
                val trimmed = line.trim()
                if (!trimmed.startsWith("-")) continue
                var value = trimmed.removePrefix("-").trim()
                if (value.startsWith("-")) value = value.removePrefix("-").trim()
                if (value.isNotEmpty()) values += unquote(value)
            }
            return values
        }
        if (raw.startsWith("[") && raw.endsWith("]")) {
            return raw.substring(1, raw.length - 1)
                .split(",")
                .map { unquote(it.trim()) }
                .filter { it.isNotEmpty() }
        }
        return emptyList()
    }

    private fun renderBlock(key: String, data: FrontMatterData): String? {
        return when (key) {
            "title" -> "title: ${scalar(data.title)}"
            "date" -> data.date.trim().takeIf { it.isNotEmpty() }?.let { "date: ${scalar(it, true)}" }
            "updated" -> data.updated.trim().takeIf { it.isNotEmpty() }?.let { "updated: ${scalar(it, true)}" }
            "abbrlink" -> data.abbrlink.trim().takeIf { it.isNotEmpty() }?.let { "abbrlink: ${scalar(it)}" }
            "active_menu" -> data.activeMenu.trim().takeIf { it.isNotEmpty() }?.let { "active_menu: ${scalar(it)}" }
            "description" -> data.description.trim().takeIf { it.isNotEmpty() }?.let { "description: ${scalar(it)}" }
            "cover" -> data.cover.trim().takeIf { it.isNotEmpty() }?.let { "cover: ${scalar(it)}" }
            "tags" -> {
                val tags = splitList(data.tags)
                if (tags.isEmpty()) null else "tags: [${tags.joinToString(", ") { scalar(it) }}]"
            }
            "categories" -> {
                val categories = splitList(data.categories)
                if (categories.isEmpty()) null
                else buildString {
                    append("categories:")
                    categories.forEach { append("\n  - - ").append(scalar(it)) }
                }
            }
            else -> null
        }
    }

    private fun splitList(value: String): List<String> =
        value.split(',', '，').map { it.trim() }.filter { it.isNotEmpty() }

    private fun scalar(value: String, forceQuote: Boolean = false): String {
        val text = value.trim()
        if (text.isEmpty()) return "''"
        val needQuote = forceQuote ||
            text.startsWith("-") || text.startsWith("?") || text.startsWith(":") ||
            text.startsWith("[") || text.startsWith("{") || text.startsWith("#") ||
            text.startsWith("&") || text.startsWith("*") || text.startsWith("!") ||
            text.startsWith("|") || text.startsWith(">") || text.startsWith("@") ||
            text.startsWith("`") || text.contains(": ") || text.contains(" #") ||
            text.contains("\n") || text.equals("true", true) || text.equals("false", true) ||
            text.equals("null", true) || text.toDoubleOrNull() != null
        return if (needQuote) "'" + text.replace("'", "''") + "'" else text
    }

    private fun parseBlocks(frontMatter: String): List<Pair<String, List<String>>> {
        val lines = delimitedLines(frontMatter)
        val blocks = mutableListOf<Pair<String, MutableList<String>>>()
        var currentKey: String? = null
        var current: MutableList<String>? = null
        for (line in lines) {
            val match = if (line.isNotEmpty() && !line.startsWith(" ") && !line.startsWith("\t")) {
                topLevelKey.find(line)
            } else {
                null
            }
            if (match != null) {
                currentKey = match.groupValues[1]
                current = mutableListOf(line)
                blocks += currentKey to current
            } else {
                if (current == null) {
                    current = mutableListOf()
                    blocks += "" to current
                }
                current += line
            }
        }
        return blocks.map { (key, block) -> key to block.toList() }
    }

    private fun delimitedLines(frontMatter: String): List<String> {
        val normalized = frontMatter.replace("\r\n", "\n")
        if (!normalized.startsWith("---")) return normalized.split("\n")
        val lines = normalized.split("\n")
        val end = (1 until lines.size).firstOrNull { lines[it].trim() == "---" } ?: return emptyList()
        return lines.subList(1, end)
    }

    private fun unquote(value: String): String {
        val text = value.trim()
        if (text.length >= 2 && text.startsWith("'") && text.endsWith("'")) {
            return text.substring(1, text.length - 1).replace("''", "'")
        }
        if (text.length >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            return text.substring(1, text.length - 1)
        }
        return text
    }
}
