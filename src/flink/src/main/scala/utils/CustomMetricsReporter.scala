package utils

import org.apache.flink.api.common.functions.RichMapFunction
import org.apache.flink.configuration.Configuration
import org.apache.flink.metrics.Counter
import org.apache.flink.metrics.Gauge
import org.apache.flink.metrics.Meter
import org.apache.flink.metrics.MeterView

class CustomMetricsReporter extends RichMapFunction[String, String] {
  @transient private var customMeter: Meter = _

  override def open(parameters: Configuration): Unit = {
    super.open(parameters)
    customMeter =
      getRuntimeContext.getMetricGroup.meter("custom", new MeterView(60))
  }

  def map(value: String): String = {
    customMeter.markEvent()
    value
  }
}
