import models.KafkaTuple
import models.KafkaTupleDeserializer
import models.KafkaTupleTimestampAssigner
import models.QueryReturn
import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.connector.kafka.source.KafkaSource
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import queries.Query1
import queries.Query2
import utils.CustomMetricsReporter

import utils.SingleFileSink

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
      wnd.window
        // Add metrics
        .map(new CustomMetricsReporter)
        .name(wnd.prefix)
        .sinkTo(new SingleFileSink(s"/opt/flink/output/${wnd.prefix}_1.csv"))
    }

    // Execute query
    env.execute
  }
}
