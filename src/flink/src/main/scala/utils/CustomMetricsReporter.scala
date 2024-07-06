package utils

import models.QueryOutput
import org.apache.flink.api.common.functions.RichMapFunction
import org.apache.flink.configuration.Configuration
import org.apache.flink.metrics.Gauge
import org.apache.flink.metrics.Counter

class CustomMetricsReporter extends RichMapFunction[QueryOutput, String] {
  // counter for throughput
  private var throughputCounter: Counter = _
  // gauge for latency
  private var minLatencyGauge: Gauge[Long] = _
  private var maxLatencyGauge: Gauge[Long] = _
  @transient private var lastMinTs: Long = _
  @transient private var lastMaxTs: Long = _

  override def open(parameters: Configuration): Unit = {
    super.open(parameters)
    throughputCounter =
      getRuntimeContext.getMetricGroup.counter("custom_throughput")
    minLatencyGauge = new Gauge[Long] {
      def getValue(): Long = lastMinTs / 1000
    }
    maxLatencyGauge = new Gauge[Long] {
      def getValue(): Long = lastMaxTs / 1000
    }
    getRuntimeContext.getMetricGroup
      .gauge[Long, Gauge[Long]]("custom_min_latency", minLatencyGauge)
    getRuntimeContext.getMetricGroup
      .gauge[Long, Gauge[Long]]("custom_max_latency", maxLatencyGauge)
  }

  def map(value: QueryOutput): String = {
    throughputCounter.inc()
    lastMinTs = System.currentTimeMillis() - value.minTs
    lastMaxTs = System.currentTimeMillis() - value.maxTs
    value.output
  }
}
