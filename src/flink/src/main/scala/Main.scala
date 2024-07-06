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
import utils.CustomMetricsReporter

import java.time.Duration

object SABD {
  def main(args: Array[String]): Unit = {
    if (args.length != 1) {
      println("No query argument was passed; expected 1 or 2")
      return
    }

    // creating execution environment
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setRuntimeMode(RuntimeExecutionMode.STREAMING)
    env.enableCheckpointing(30 * 1000) // Checkpoint every 30 seconds

    // Creating Kafka Source
    val source = KafkaSource
      .builder[KafkaTuple]
      .setBootstrapServers("broker:19092")
      .setTopics("filtered")
      .setGroupId(s"flink_${args(0)}")
      .setStartingOffsets(OffsetsInitializer.earliest())
      .setValueOnlyDeserializer(new KafkaTupleDeserializer)
      .build

    // Creating Watermark Strategy with custom timestamp assigner
    val watermarkStrategy = WatermarkStrategy
      .forMonotonousTimestamps()
      .withTimestampAssigner(new KafkaTupleTimestampAssigner)

    // Creating Datastream
    val ds = env.fromSource(source, watermarkStrategy, "kafka_source")

    // Run query
    val wnds: List[QueryReturn] =
      if (args(0).toInt == 1)
        Query1.query(ds)
      else if (args(0).toInt == 2)
        Query2.query(ds)
      else
        List()

    for (wnd <- wnds) {
      // Creating file sink for this window
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

      wnd.window
        // Add metrics
        .map(new CustomMetricsReporter)
        .name(wnd.prefix)
        .sinkTo(fileSink)
    }

    // Execute query
    env.execute
  }
}
