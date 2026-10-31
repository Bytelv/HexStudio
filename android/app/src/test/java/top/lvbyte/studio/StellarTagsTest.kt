package top.lvbyte.studio

import org.junit.Assert.assertTrue
import org.junit.Test
import top.lvbyte.studio.util.StellarTags

class StellarTagsTest {
    @Test
    fun balancedTagsPass() {
        val body = """
            {% note color:blue 标题 内容 %}
            {% folding color:blue 展开 %}
            内容
            {% endfolding %}
            {% tabs %}
            <!-- tab A -->
            A
            {% endtabs %}
        """.trimIndent()
        val issues = StellarTags.validate(body, mapOf("title" to "T", "abbrlink" to "a"))
        assertTrue(issues.toString(), issues.isEmpty())
    }

    @Test
    fun unclosedBlockIsReported() {
        val issues = StellarTags.validate(
            "{% folding color:blue 标题 %}\n没有闭合",
            mapOf("title" to "T", "abbrlink" to "a")
        )
        assertTrue(issues.any { it.contains("没有闭合") })
    }

    @Test
    fun unknownTagIsReported() {
        val issues = StellarTags.validate(
            "{% not-a-real-tag foo %}",
            mapOf("title" to "T", "abbrlink" to "a")
        )
        assertTrue(issues.any { it.contains("未知标签") })
    }

    @Test
    fun aboutTagIsForbiddenInV2() {
        val issues = StellarTags.validate(
            "{% about %}",
            mapOf("title" to "T", "abbrlink" to "a")
        )
        assertTrue(issues.any { it.contains("about") })
    }
}
