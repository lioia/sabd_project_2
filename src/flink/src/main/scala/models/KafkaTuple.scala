package models

import org.apache.flink.api.common.serialization.DeserializationSchema
import java.nio.charset.StandardCharsets
import org.apache.flink.api.common.typeinfo.TypeInformation
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner

case class KafkaTuple(
    val date: String,
    val serial_number: String,
    val model: String,
    val failure: Int,
    val vault_id: Int,
    val temperature: Float
) {
  private val formatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")

  def timestamp(): Long = {
    return LocalDateTime
      .parse(date, formatter)
      .toInstant(ZoneOffset.UTC)
      .toEpochMilli()
  }
}

class KafkaTupleDeserializer
    extends DeserializationSchema[KafkaTuple]
    with Serializable {
  override def deserialize(message: Array[Byte]): KafkaTuple = {
    val line = new String(message, StandardCharsets.UTF_8)
    val fields = line.split(",")
    KafkaTuple(
      fields(0),
      fields(1),
      fields(2),
      fields(3).toInt,
      fields(4).toInt,
      fields(25).toFloat
    )
  }

  override def isEndOfStream(nextElement: KafkaTuple): Boolean = false

  override def getProducedType: TypeInformation[KafkaTuple] =
    TypeInformation.of(classOf[KafkaTuple])
}

class KafkaTupleTimestampAssigner
    extends SerializableTimestampAssigner[KafkaTuple] {
  override def extractTimestamp(
      value: KafkaTuple,
      recordTimestamp: Long
  ): Long = value.timestamp();
}
