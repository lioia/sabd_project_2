package queries

import models.KafkaTuple
import models.QueryOutput
import models.QueryReturn
import org.apache.flink.api.common.functions.AggregateFunction
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.datastream.KeyedStream
import org.apache.flink.streaming.api.functions.windowing.ProcessAllWindowFunction
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector

import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.math

object Query2 {
  private case class Accumulator(
      count: Long,
      ts: Long,
      vault_id: Int,
      failures: Int,
      hdds: mutable.Set[(String, String)] // Set((model, serial_number))
  )

  // Format csv output
  private class ProcessRanking
      extends ProcessAllWindowFunction[Accumulator, QueryOutput, TimeWindow] {
    override def process(
        context: ProcessAllWindowFunction[
          Accumulator,
          QueryOutput,
          TimeWindow
        ]#Context,
        elements: java.lang.Iterable[Accumulator],
        out: Collector[QueryOutput]
    ): Unit = {
      val sortedVaults =
        elements.asScala.toList.sortBy(-_.failures).take(10)

      var count = 0L
      var ts = Long.MinValue

      var result = DateTimeFormatter
        .ofPattern("yyyy-MM-dd")
        .withZone(ZoneOffset.UTC)
        .format(Instant.ofEpochMilli(context.window.getStart))
      for (vault <- sortedVaults) {
        result += s",${vault.vault_id},${vault.failures},("
        result += vault.hdds
          .map { case (model, serial_number) =>
            s"$model,$serial_number"
          }
          .mkString(",")
        result += ")"
        count += vault.count
        ts = math.max(ts, vault.ts)
      }
      out.collect(new QueryOutput(count, ts, result))
    }
  }

  // Compute failure count and list of failed hdds
  private class TupleAggregate
      extends AggregateFunction[KafkaTuple, Accumulator, Accumulator] {
    def createAccumulator(): Accumulator =
      new Accumulator(0, Long.MinValue, 0, 0, mutable.Set())

    def add(value: KafkaTuple, acc: Accumulator): Accumulator =
      Accumulator(
        acc.count + 1,
        math.max(value.ts, acc.ts),
        value.vault_id,
        acc.failures + value.failure,
        acc.hdds + ((value.model, value.serial_number))
      )

    def getResult(acc: Accumulator): Accumulator = acc

    def merge(a: Accumulator, b: Accumulator): Accumulator =
      Accumulator(
        a.count + b.count,
        math.max(a.ts, b.ts),
        a.vault_id,
        a.failures + b.failures,
        a.hdds ++ b.hdds
      )
  }

  def impl(
      ds: KeyedStream[KafkaTuple, Int],
      duration: Long,
      offset: Long
  ): DataStream[QueryOutput] = {
    return ds
      // Same as Query 1
      .window(
        TumblingEventTimeWindows
          .of(Duration.ofDays(duration), Duration.ofDays(offset))
      )
      // Compute aggregation
      .aggregate(new TupleAggregate)
      // compute ranking
      .windowAll(
        TumblingEventTimeWindows
          .of(Duration.ofDays(duration), Duration.ofDays(offset))
      )
      .process(new ProcessRanking)
  }

  def query(ds: DataStream[KafkaTuple]): List[QueryReturn] = {
    // Filter only tuples with a failure
    val working_ds = ds
      .filter(_.failure > 0)
      .keyBy(_.vault_id)

    return List(
      new QueryReturn(impl(working_ds, 1, 0), "query_1"),
      new QueryReturn(impl(working_ds, 3, 2), "query_3"),
      new QueryReturn(impl(working_ds, 23, 13), "query_23")
    )
  }
}
