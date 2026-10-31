package top.lvbyte.studio.util

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeUtils {
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val dayFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)
    private val weekdayFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.CHINA)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA)
    private val frontMatterFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)

    fun dayLabel(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(dayFormatter)

    fun weekdayLabel(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(weekdayFormatter)

    fun timeLabel(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(timeFormatter)

    fun dateTime(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(dateTimeFormatter)

    fun dayKey(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().toString()

    fun frontMatterNow(): String = OffsetDateTime.now(zone).format(frontMatterFormatter)

    fun nowMillis(): Long = System.currentTimeMillis()
}
