package top.lvbyte.hexstudio.util

data class TagSpec(
    val name: String,
    val category: String,
    val description: String,
    val template: String,
    val block: Boolean = false
)

object StellarTags {
    const val CURSOR = "\u0001CURSOR\u0001"

    val specs: List<TagSpec> = listOf(
        TagSpec(
            "note", "块级", "彩色提示块",
            "{% note color:blue 标题 内容 %}"
        ),
        TagSpec(
            "folding", "块级", "可折叠面板",
            "{% folding color:blue 折叠标题 %}\n内容\n{% endfolding %}$CURSOR",
            block = true
        ),
        TagSpec(
            "tabs", "块级", "标签页",
            "{% tabs active:1 align:center %}\n<!-- tab 标签一 -->\n内容一\n<!-- tab 标签二 -->\n内容二\n{% endtabs %}$CURSOR",
            block = true
        ),
        TagSpec(
            "grid", "块级", "分栏网格",
            "{% grid c:2 %}\n<!-- cell -->\n左栏\n<!-- cell -->\n右栏\n{% endgrid %}$CURSOR",
            block = true
        ),
        TagSpec(
            "timeline", "块级", "时间线",
            "{% timeline %}\n<!-- node 2026-10-31 -->\n内容\n{% endtimeline %}$CURSOR",
            block = true
        ),
        TagSpec(
            "quot", "块级", "章节标题",
            "{% quot el:h2 章节标题 %}"
        ),
        TagSpec(
            "image", "媒体", "主题图片（自动长宽比）",
            "{% image https://image.lvbyte.top/file/xxx.jpg 说明文字 %}"
        ),
        TagSpec(
            "frame", "媒体", "手机外壳图片",
            "{% frame iphone11 img:https://image.lvbyte.top/file/xxx.jpg 说明 %}"
        ),
        TagSpec(
            "button", "链接", "按钮",
            "{% button 按钮文字 https://example.com %}"
        ),
        TagSpec(
            "link", "链接", "链接卡片",
            "{% link https://example.com %}"
        ),
        TagSpec(
            "copy", "内联", "可复制文本",
            "{% copy 要复制的文本 %}"
        ),
        TagSpec("u", "内联", "下划线", "{% u 文字 %}"),
        TagSpec("emp", "内联", "强调", "{% emp 文字 %}"),
        TagSpec("wavy", "内联", "波浪线", "{% wavy 文字 %}"),
        TagSpec("kbd", "内联", "键盘按键", "{% kbd Ctrl %}"),
        TagSpec("blur", "内联", "模糊（悬停显示）", "{% blur 文字 %}"),
        TagSpec("psw", "内联", "密码点", "{% psw 密码 %}")
    )

    /**
     * hexo-theme-stellar/scripts/tags/index.js 中注册的标签。
     * 站点另外注册了 steam / equipment，一并放行。
     */
    val knownTags: Set<String> = setOf(
        "tabs", "box", "folding", "folders", "grid", "swiper", "gallery", "banner", "dropdown",
        "friends", "albums", "posters", "sites", "ghcard", "gist", "toc", "timeline", "md", "chat",
        "checkbox", "radio", "copy", "emoji", "icon", "frame", "image", "link", "button", "mark",
        "navbar", "note", "poetry", "quot", "blockquote", "hashtag", "mbti", "okr", "audio",
        "video", "rating", "vote", "table", "tip", "reel", "paper",
        "u", "emp", "wavy", "kbd", "psw", "blur",
        "steam", "equipment"
    )

    val blockTags: Set<String> = setOf(
        "tabs", "box", "folding", "folders", "grid", "swiper", "gallery", "banner", "dropdown",
        "timeline", "chat", "poetry", "blockquote", "okr", "table"
    )

    private val tagRegex = Regex("""\{%\s*([a-zA-Z][\w-]*)([^%]*)%\}""")

    fun validate(body: String, frontMatterFields: Map<String, String>): List<String> {
        val issues = mutableListOf<String>()
        val stack = mutableListOf<String>()
        // 代码块里的标签是示例文本，不参与闭合校验
        val sanitized = body
            .replace(Regex("""```[\s\S]*?```"""), "")
            .replace(Regex("`[^`\n]*`"), "")
        tagRegex.findAll(sanitized).forEach { match ->
            val name = match.groupValues[1].lowercase()
            if (name == "about") {
                issues += "Stellar v2 已移除 {% about %} 标签，构建会直接失败"
            } else if (name.startsWith("end")) {
                val closing = name.removePrefix("end")
                if (stack.isEmpty()) {
                    issues += "多了一个 {% $name %}，没有对应的开始标签"
                } else if (stack.last() != closing) {
                    issues += "标签闭合不匹配：栈顶是 ${stack.last()}，这里却是 $name"
                } else {
                    stack.removeAt(stack.size - 1)
                }
            } else if (name !in knownTags) {
                issues += "未知标签 {% $name %}（如果不是本仓自定义标签，可能是拼写错误）"
            } else if (name in blockTags) {
                stack += name
            }
        }
        stack.forEach { issues += "{% $it %} 没有闭合（缺少 {% end$it %}）" }

        if (frontMatterFields["title"].isNullOrBlank()) issues += "front-matter 缺少 title"
        val abbrlink = frontMatterFields["abbrlink"]
        if (abbrlink.isNullOrBlank()) {
            issues += "front-matter 没有 abbrlink，永久链接会由插件随机生成"
        }
        return issues
    }
}
