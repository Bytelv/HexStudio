package top.lvbyte.hexstudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import top.lvbyte.hexstudio.ui.screens.editor.FrontMatter
import top.lvbyte.hexstudio.ui.screens.editor.FrontMatterData

class FrontMatterTest {
    private val sample = """
        ---
        title: 旧标题
        date: '2026-01-01T10:00:00.000+08:00'
        abbrlink: old-post
        active_menu: post
        tags: [折腾, Android]
        categories:
          - - 项目分享
        cover: https://image.lvbyte.top/file/a.jpg
        custom_field: keep-me
        comments:
          enabled: false
        ---

        正文内容
    """.trimIndent()

    @Test
    fun splitKeepsBodyAndReadsFields() {
        val parts = FrontMatter.split(sample)
        assertTrue(parts.frontMatter.startsWith("---"))
        assertTrue(parts.body.contains("正文内容"))
        assertEquals("旧标题", FrontMatter.dataFromFields(parts.frontMatter).title)
        assertEquals("old-post", FrontMatter.dataFromFields(parts.frontMatter).abbrlink)
    }

    @Test
    fun updateRewritesKnownFieldsAndKeepsUnknown() {
        val updated = FrontMatter.update(
            FrontMatter.split(sample).frontMatter,
            FrontMatterData(
                title = "新标题",
                date = "2026-10-31T12:00:00.000+08:00",
                updated = "2026-10-31T12:00:00.000+08:00",
                abbrlink = "new-post",
                description = "摘要",
                cover = "https://image.lvbyte.top/file/b.jpg",
                tags = "折腾, 博客",
                categories = "项目分享, 折腾",
                activeMenu = "post"
            )
        )
        assertTrue(updated.contains("title: 新标题"))
        assertTrue(updated.contains("abbrlink: new-post"))
        assertTrue(updated.contains("tags: [折腾, 博客]"))
        assertTrue(updated.contains("- - 项目分享"))
        assertTrue(updated.contains("- - 折腾"))
        assertTrue(updated.contains("custom_field: keep-me"))
        assertTrue(updated.contains("comments:"))
        assertTrue(updated.contains("enabled: false"))
    }

    @Test
    fun newPostHasStellarFields() {
        val content = FrontMatter.newPost(
            title = "Hello",
            abbrlink = "hello-123",
            tags = "Android",
            categories = "项目分享",
            description = "desc",
            cover = "https://image.lvbyte.top/file/c.jpg"
        )
        assertTrue(content.startsWith("---"))
        assertTrue(content.contains("title: Hello"))
        assertTrue(content.contains("abbrlink: hello-123"))
        assertTrue(content.contains("active_menu: post"))
        assertTrue(content.contains("categories:"))
    }

    @Test
    fun listValuesSupportsInlineAndBlock() {
        val front = FrontMatter.split(sample).frontMatter
        assertEquals(listOf("折腾", "Android"), FrontMatter.listValues(front, "tags"))
        assertEquals(listOf("项目分享"), FrontMatter.listValues(front, "categories"))
    }
}
