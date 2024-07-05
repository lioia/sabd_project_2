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
  private case class Internal(
      val minTs: Long,
      val maxTs: Long,
      val vault_id: Int,
      val failures: Int,
      val hdds: mutable.Set[(String, String)] // Set((model, serial_number))
  )

  private class ProcessRanking
      extends ProcessAllWindowFunction[Internal, QueryOutput, TimeWindow] {
    override def process(
        context: ProcessAllWindowFunction[
          Internal,
          QueryOutput,
          TimeWindow
        ]#Context,
        elements: java.lang.Iterable[Internal],
        out: Collector[QueryOutput]
    ): Unit = {
      val sortedVaults = elements.asScala.toList.sortBy(_.failures).take(10)
      val date =
        DateTimeFormatter
          .ofPattern("yyyy-MM-dd")
          .withZone(ZoneOffset.UTC)
          .format(Instant.ofEpochMilli(context.window.getStart))

      var minTs = Long.MaxValue
      var maxTs = Long.MinValue

      var result = s"$date"
      for (vault <- sortedVaults) {
        result += s",${vault.vault_id},${vault.failures}"
        for ((model, serial_number) <- vault.hdds) {
          result += s",$model,$serial_number"
        }
        // minTs = math.min(minTs, vault.)
        minTs = math.min(minTs, vault.minTs)
        maxTs = math.max(maxTs, vault.maxTs)
      }
      out.collect(new QueryOutput(minTs, maxTs, s"$result\n"))
    }
  }

  private class TupleAggregate
      extends AggregateFunction[KafkaTuple, Internal, Internal] {
    def createAccumulator(): Internal =
      new Internal(Long.MaxValue, Long.MinValue, 0, 0, mutable.Set())

    def add(value: KafkaTuple, acc: Internal): Internal =
      Internal(
        math.min(value.ts, acc.minTs),
        math.max(value.ts, acc.maxTs),
        value.vault_id,
        acc.failures + value.failure,
        acc.hdds + ((value.model, value.serial_number))
      )

    def getResult(acc: Internal): Internal = acc

    def merge(a: Internal, b: Internal): Internal =
      Internal(
        math.min(a.minTs, b.minTs),
        math.max(a.maxTs, b.maxTs),
        a.vault_id,
        a.failures + b.failures,
        a.hdds ++ b.hdds
      )
  }

  private def impl(
      ds: KeyedStream[KafkaTuple, Int],
      duration: Long
  ): DataStream[QueryOutput] = {
    return ds
      .window(TumblingEventTimeWindows.of(Duration.ofDays(duration)))
      .aggregate(new TupleAggregate())
      .windowAll(TumblingEventTimeWindows.of(Duration.ofDays(duration)))
      .process(new ProcessRanking())
  }

  def query(ds: DataStream[KafkaTuple]): List[QueryReturn] = {
    val working_ds = ds
      .filter(_.failure > 0)
      .keyBy(_.vault_id)

    return List(
      new QueryReturn(impl(working_ds, 1L), "query2_1"),
      new QueryReturn(impl(working_ds, 3L), "query2_3"),
      new QueryReturn(impl(working_ds, 23L), "query2_23")
    )
  }
}
