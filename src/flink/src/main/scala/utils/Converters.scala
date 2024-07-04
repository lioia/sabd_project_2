package utils

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object Converters {
  def milliToStringDate(milli: Long): String =
    DateTimeFormatter
      .ofPattern("yyyy-MM-dd")
      .withZone(ZoneOffset.UTC)
      .format(Instant.ofEpochMilli(milli))

  def csvDateToTimestamp(date: String): Long =
    LocalDateTime
      .parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS"))
      .toInstant(ZoneOffset.UTC)
      .toEpochMilli()
}
