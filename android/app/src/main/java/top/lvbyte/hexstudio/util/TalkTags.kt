package top.lvbyte.hexstudio.util

object TalkTags {
    // 注意：[ 必须转义，否则 Android/Java 正则会把它当嵌套字符类，直接抛 PatternSyntaxException。
    private val hashtag = Regex("""(^|[\s(\[（])#([\p{L}\p{N}_-]{1,30})""")

    fun extract(content: String): List<String> {
        return hashtag.findAll(content)
            .map { it.groupValues[2] }
            .filter { it.isNotBlank() && it != "SFCN" }
            .distinct()
            .take(10)
            .toList()
    }
}
