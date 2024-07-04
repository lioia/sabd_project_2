package queries

import models.KafkaTuple
import models.QueryReturn
import org.apache.flink.api.common.functions.AggregateFunction
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.datastream.DataStreamSource
import org.apache.flink.streaming.api.datastream.KeyedStream
import org.apache.flink.streaming.api.datastream.WindowedStream
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction
import org.apache.flink.streaming.api.windowing.assigners.GlobalWindows
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows
import org.apache.flink.streaming.api.windowing.assigners.WindowAssigner
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector
import utils.Converters

import java.time.Duration
import scala.collection.JavaConverters._
import scala.math

object Query1 {
  private case class Accumulator(count: Int, mean: Float, m2: Float) {
    def stdDev: Double = if (count > 1) math.sqrt(m2 / (count - 1)) else 0.0
  }
  private case class Result(start: Long, vault_id: Int, acc: Accumulator)

  private class AggregateStats
      extends AggregateFunction[KafkaTuple, Accumulator, Accumulator] {
    def createAccumulator(): Accumulator = new Accumulator(0, 0, 0)

    def add(value: KafkaTuple, acc: Accumulator): Accumulator = {
      val count = acc.count + 1
      val delta = value.temperature - acc.mean
      val mean = acc.mean + delta / count
      val delta2 = value.temperature - mean
      val m2 = acc.m2 + delta * delta2
      new Accumulator(count, mean, m2)
    }

    def getResult(acc: Accumulator): Accumulator = acc

    def merge(a: Accumulator, b: Accumulator): Accumulator = {
      val count = a.count + b.count
      val delta = b.mean - a.mean
      val mean = a.mean + delta * b.count / count
      val m2 = a.m2 + b.m2 + delta * delta * a.count * b.count / count
      Accumulator(count, mean, m2)
    }
  }

  private class WindowResultFunction()
      extends ProcessWindowFunction[
        Accumulator,
        Result,
        Int,
        TimeWindow
      ] {
    override def process(
        key: Int,
        context: ProcessWindowFunction[
          Accumulator,
          Result,
          Int,
          TimeWindow
        ]#Context,
        elements: java.lang.Iterable[Accumulator],
        out: Collector[Result]
    ): Unit = {
      val agg = elements.iterator().next
      out.collect(Result(context.window.getStart, key, agg))
    }
  }

  private def impl(
      ds: KeyedStream[KafkaTuple, Int],
      duration: Long
  ): DataStream[String] = {
    return ds
      .window(TumblingEventTimeWindows.of(Duration.ofDays(duration)))
      .aggregate(new AggregateStats(), new WindowResultFunction())
      .map(x => {
        val date = Converters.milliToStringDate(x.start)
        f"$date,${x.vault_id},${x.acc.count},${x.acc.mean}%.3f,${x.acc.stdDev}%.3f"
      })
  }

  def query_1(ds: DataStream[KafkaTuple]): List[QueryReturn] = {
    val working_ds = ds
      .filter(x => (x.vault_id >= 1000 && x.vault_id <= 1020))
      .keyBy(_.vault_id)

    return List(
      new QueryReturn(impl(working_ds, 1L), "query1_1"),
      new QueryReturn(impl(working_ds, 3L), "query1_3"),
      new QueryReturn(impl(working_ds, 23L), "query1_23")
    )
  }
}
