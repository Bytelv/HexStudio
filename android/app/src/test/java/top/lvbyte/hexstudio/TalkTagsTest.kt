package top.lvbyte.hexstudio

import org.junit.Assert.assertEquals
import org.junit.Test
import top.lvbyte.hexstudio.util.TalkTags

class TalkTagsTest {
    @Test
    fun parsesChineseAndAsciiTagsWithoutRegexCrash() {
        val tags = TalkTags.extract("今天去爬山 #日常 #Android #SFCN")
        assertEquals(listOf("日常", "Android"), tags)
    }

    @Test
    fun handlesBracketsAndNewlines() {
        val tags = TalkTags.extract("写下一条\n（#随手记）和 [#折腾]")
        assertEquals(listOf("随手记", "折腾"), tags)
    }
}
