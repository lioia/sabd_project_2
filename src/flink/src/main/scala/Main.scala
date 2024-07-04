import models.KafkaTuple
import models.KafkaTupleDeserializer
import models.KafkaTupleTimestampAssigner
import models.QueryReturn
import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.serialization.SimpleStringEncoder
import org.apache.flink.configuration.MemorySize
import org.apache.flink.connector.file.sink.FileSink
import org.apache.flink.connector.kafka.source.KafkaSource
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer
import org.apache.flink.core.fs.Path
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.sink.filesystem.bucketassigners.BasePathBucketAssigner
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.DefaultRollingPolicy
import queries.Query1
import queries.Query2

import java.nio.charset.StandardCharsets
import java.time.Duration
import scala.collection.mutable.ListBuffer

object SABD {
  def main(args: Array[String]): Unit = {
    if (args.length != 1) {
      println("No query argument was passed; expected 1 or 2")
      return
    }

    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setRuntimeMode(RuntimeExecutionMode.STREAMING)
    env.setParallelism(1)

    val source = KafkaSource
      .builder[KafkaTuple]
      .setBootstrapServers("broker:19092")
      .setTopics("filtered")
      .setGroupId(s"flink_${args(0)}")
      .setStartingOffsets(OffsetsInitializer.earliest())
      .setValueOnlyDeserializer(new KafkaTupleDeserializer)
      .build

    val watermarkStrategy = WatermarkStrategy
      .forMonotonousTimestamps()
      .withTimestampAssigner(new KafkaTupleTimestampAssigner)

    val ds = env.fromSource(source, watermarkStrategy, "kafka_source")

    val wnds: ListBuffer[QueryReturn] = ListBuffer()

    if (args(0).toInt == 1)
      wnds ++= Query1.query_1(ds)
    else if (args(0).toInt == 2)
      wnds ++= Query2.query_2(ds)

    for (wnd <- wnds) {
      val fileSink = FileSink
        .forRowFormat[String](
          new Path(s"/opt/flink/output/${wnd.prefix}"),
          new SimpleStringEncoder[String]("UTF-8")
        )
        .withRollingPolicy(
          DefaultRollingPolicy.builder
            .withRolloverInterval(Duration.ofMinutes(1))
            .withInactivityInterval(Duration.ofSeconds(30))
            .withMaxPartSize(MemorySize.ofMebiBytes(20))
            .build()
        )
        .withBucketAssigner(new BasePathBucketAssigner)
        .build
      wnd.window.sinkTo(fileSink)
    }

    env.execute
  }
}
