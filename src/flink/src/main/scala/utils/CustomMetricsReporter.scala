package utils

import models.QueryOutput
import org.apache.flink.api.common.functions.RichMapFunction
import org.apache.flink.configuration.Configuration
import org.apache.flink.metrics.Gauge
import org.apache.flink.metrics.Counter

class CustomMetricsReporter extends RichMapFunction[QueryOutput, String] {
  // End-to-End Latency
  @transient private var latency: Long = _
  @transient private var throughputCounter: Counter = _

  override def open(parameters: Configuration): Unit = {
    super.open(parameters)
    getRuntimeContext.getMetricGroup
      .gauge[Long, Gauge[Long]](
        "custom_latency",
        new Gauge[Long] {
          def getValue(): Long = latency
        }
      )

    throughputCounter =
      getRuntimeContext.getMetricGroup.counter("custom_throughput")
  }

  def map(value: QueryOutput): String = {
    latency = System.currentTimeMillis() - value.ts
    throughputCounter.inc(value.tuples)
    value.output
  }
}
