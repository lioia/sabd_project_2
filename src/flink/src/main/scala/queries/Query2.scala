package queries

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.math

import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

import org.apache.flink.api.common.functions.AggregateFunction
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.datastream.KeyedStream
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows
import org.apache.flink.streaming.api.functions.windowing.ProcessAllWindowFunction
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector

import models.KafkaTuple
import models.QueryReturn

object Query2 {
  private case class Internal(
      val vault_id: Int,
      val failures: Int,
      val hdds: mutable.Set[(String, String)] // Set((model, serial_number))
  )

  private def internalFromKafka(obj: KafkaTuple): Internal =
    new Internal(obj.vault_id, obj.failure, mutable.Set())

  private class ProcessRanking
      extends ProcessAllWindowFunction[Internal, String, TimeWindow] {
    override def process(
        context: ProcessAllWindowFunction[Internal, String, TimeWindow]#Context,
        elements: java.lang.Iterable[Internal],
        out: Collector[String]
    ): Unit = {
      val sortedVaults = elements.asScala.toList.sortBy(_.failures).take(10)
      val date =
        DateTimeFormatter
          .ofPattern("yyyy-MM-dd")
          .withZone(ZoneOffset.UTC)
          .format(Instant.ofEpochMilli(context.window.getStart))

      var result = s"$date"
      for (vault <- sortedVaults) {
        result += s",${vault.vault_id},${vault.failures}"
        for ((model, serial_number) <- vault.hdds) {
          result += s",$model,$serial_number"
        }
      }
      out.collect(s"$result\n")
    }
  }

  private class TupleAggregate
      extends AggregateFunction[KafkaTuple, Internal, Internal] {
    def createAccumulator(): Internal = new Internal(0, 0, mutable.Set())

    def add(value: KafkaTuple, accumulator: Internal): Internal =
      Internal(
        value.vault_id,
        accumulator.failures + value.failure,
        accumulator.hdds + ((value.model, value.serial_number))
      )

    def getResult(accumulator: Internal): Internal = accumulator

    def merge(a: Internal, b: Internal): Internal =
      Internal(
        a.vault_id,
        a.failures + b.failures,
        a.hdds ++ b.hdds
      )
  }

  private def impl(
      ds: KeyedStream[KafkaTuple, Int],
      duration: Long
  ): DataStream[String] = {
    return ds
      .window(TumblingEventTimeWindows.of(Duration.ofDays(duration)))
      .aggregate(new TupleAggregate())
      .windowAll(TumblingEventTimeWindows.of(Duration.ofDays(duration)))
      .process(new ProcessRanking())
  }

  def query_2(ds: DataStream[KafkaTuple]): List[QueryReturn] = {
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
