package top.lvbyte.studio.ui.screens.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

class MarkdownHighlightTransformation(
    private val primary: Color,
    private val secondary: Color,
    private val codeBackground: Color,
    private val muted: Color
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val builder = AnnotatedString.Builder(raw)

        fun addStyle(start: Int, end: Int, style: SpanStyle) {
            if (start < end && start >= 0 && end <= raw.length) {
                builder.addStyle(style, start, end)
            }
        }

        val tagStyle = SpanStyle(color = primary, fontWeight = FontWeight.SemiBold)
        Regex("""\{%[\s\S]*?%\}""").findAll(raw).forEach { addStyle(it.range.first, it.range.last + 1, tagStyle) }

        val headingStyle = SpanStyle(color = primary, fontWeight = FontWeight.Bold)
        Regex("""(?m)^#{1,6} .*$""").findAll(raw).forEach { addStyle(it.range.first, it.range.last + 1, headingStyle) }

        val codeStyle = SpanStyle(background = codeBackground, color = secondary)
        Regex("""```[\s\S]*?```""").findAll(raw).forEach { addStyle(it.range.first, it.range.last + 1, codeStyle) }
        Regex("""`[^`\n]+`""").findAll(raw).forEach { addStyle(it.range.first, it.range.last + 1, codeStyle) }

        val linkStyle = SpanStyle(color = secondary)
        Regex("""\[[^\]]*\]\([^)]*\)""").findAll(raw).forEach { addStyle(it.range.first, it.range.last + 1, linkStyle) }

        val boldStyle = SpanStyle(fontWeight = FontWeight.Bold)
        Regex("""\*\*[^*\n]+\*\*""").findAll(raw).forEach { addStyle(it.range.first, it.range.last + 1, boldStyle) }

        val yamlKeyStyle = SpanStyle(color = secondary, fontWeight = FontWeight.Medium)
        Regex("""(?m)^[A-Za-z0-9_-]+:""").findAll(raw).forEach { addStyle(it.range.first, it.range.last + 1, yamlKeyStyle) }

        val quoteStyle = SpanStyle(color = muted)
        Regex("""(?m)^>.*$""").findAll(raw).forEach { addStyle(it.range.first, it.range.last + 1, quoteStyle) }

        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}
