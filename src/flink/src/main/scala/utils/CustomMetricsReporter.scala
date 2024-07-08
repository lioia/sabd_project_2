package utils

import models.QueryOutput
import org.apache.flink.api.common.functions.RichMapFunction
import org.apache.flink.configuration.Configuration
import org.apache.flink.metrics.Gauge

class CustomMetricsReporter extends RichMapFunction[QueryOutput, String] {
  // End-to-End Latency
  @transient private var latency: Long = _

  override def open(parameters: Configuration): Unit = {
    super.open(parameters)
    getRuntimeContext.getMetricGroup
      .gauge[Long, Gauge[Long]](
        "custom_latency",
        new Gauge[Long] {
          def getValue(): Long = latency
        }
      )
  }

  def map(value: QueryOutput): String = {
    latency = System.currentTimeMillis() - value.ts
    value.output
  }
}
