package queries

import models.KafkaTuple
import models.QueryReturn
import org.apache.flink.api.common.functions.AggregateFunction
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.datastream.KeyedStream
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector
import utils.Converters

import java.time.Duration
import scala.math
import models.QueryOutput

object Query1 {
  private case class Accumulator(
      minTs: Long,
      maxTs: Long,
      count: Int,
      mean: Float,
      m2: Float
  ) {
    def stdDev: Double = if (count > 1) math.sqrt(m2 / (count - 1)) else 0.0
  }
  private case class Result(start: Long, vault_id: Int, acc: Accumulator)

  // Computes stats
  private class AggregateStats
      extends AggregateFunction[KafkaTuple, Accumulator, Accumulator] {
    def createAccumulator(): Accumulator =
      new Accumulator(Long.MaxValue, Long.MinValue, 0, 0, 0)

    // Welford Algorithm
    def add(value: KafkaTuple, acc: Accumulator): Accumulator = {
      val minTs = math.min(value.ts, acc.minTs)
      val maxTs = math.max(value.ts, acc.maxTs)
      val count = acc.count + 1
      val delta = value.temperature - acc.mean
      val mean = acc.mean + delta / count
      val delta2 = value.temperature - mean
      val m2 = acc.m2 + delta * delta2
      new Accumulator(minTs, maxTs, count, mean, m2)
    }

    def getResult(acc: Accumulator): Accumulator = acc

    // Welford Algorithm
    def merge(a: Accumulator, b: Accumulator): Accumulator = {
      val minTs = math.min(a.minTs, b.minTs)
      val maxTs = math.max(a.maxTs, b.maxTs)
      val count = a.count + b.count
      val delta = b.mean - a.mean
      val mean = a.mean + delta * b.count / count
      val m2 = a.m2 + b.m2 + delta * delta * a.count * b.count / count
      Accumulator(minTs, maxTs, count, mean, m2)
    }
  }

  // Window function executed with AggregateStats; adds vault_id and window start
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
      duration: Long,
      offset: Long
  ): DataStream[QueryOutput] = {
    // For the 3 and 23 days window, there needs to be an offset of 2 days
    // because Flink windows start at Epoch (so it needs to be correctly offset-ed)
    return ds
      .window(
        TumblingEventTimeWindows
          .of(Duration.ofDays(duration), Duration.ofDays(offset))
      )
      // compute stats
      .aggregate(new AggregateStats(), new WindowResultFunction())
      // map into csv output (minTs and maxTs are used by queries)
      .map(x => {
        val date = Converters.milliToStringDate(x.start)
        new QueryOutput(
          x.acc.minTs,
          x.acc.maxTs,
          f"$date,${x.vault_id},${x.acc.count},${x.acc.mean}%.3f,${x.acc.stdDev}%.3f"
        )
      })
  }

  def query(ds: DataStream[KafkaTuple]): List[QueryReturn] = {
    // Filter vaults in [1000, 1020]
    val working_ds = ds
      .filter(x => (x.vault_id >= 1000 && x.vault_id <= 1020))
      // key by vault_id
      .keyBy(_.vault_id)

    return List(
      new QueryReturn(impl(working_ds, 1, 0), "query_1"),
      new QueryReturn(impl(working_ds, 3, 2), "query_3"),
      new QueryReturn(impl(working_ds, 23, 2), "query_23")
    )
  }
}
