package queries

import java.time.Duration
import scala.math

import org.apache.flink.api.common.functions.ReduceFunction
import org.apache.flink.streaming.api.datastream.DataStreamSource
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.datastream.KeyedStream
import org.apache.flink.streaming.api.datastream.WindowedStream
import org.apache.flink.streaming.api.windowing.assigners.WindowAssigner
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows
import org.apache.flink.streaming.api.windowing.assigners.GlobalWindows

import models.KafkaTuple
import models.QueryReturn

// TODO: convert reduce to aggregate
object Query1 {
  private case class Internal(
      val vault_id: Int,
      val count: Int,
      val mean: Float,
      val m2: Float,
      val date: String
  )

  private def internalFromKafka(obj: KafkaTuple): Internal =
    new Internal(obj.vault_id, 1, obj.temperature, 1f, obj.date)

  private class ReduceStats extends ReduceFunction[Internal] {
    def reduce(value1: Internal, value2: Internal): Internal = {
      val count = value1.count + value2.count
      val delta = value1.mean - value2.mean
      val mean = value1.mean + delta / count
      val delta2 = value2.mean - mean
      val m2 = value1.m2 + delta * delta2
      val date = scala.math.Ordering.String.min(value1.date, value2.date)

      // value1.vault_id should be equal to value2.vault_id
      return new Internal(value1.vault_id, count, mean, m2, date)
    }
  }

  private def toCsvString(obj: Internal): String = {
    val stddev = math.sqrt(obj.m2 / obj.count).floatValue()
    return s"${obj.date},${obj.vault_id},${obj.count},${obj.mean},$stddev"
  }

  private def impl(
      ds: KeyedStream[Internal, Int],
      duration: Long
  ): DataStream[String] = {
    return ds
      .window(TumblingEventTimeWindows.of(Duration.ofDays(duration)))
      .reduce(new ReduceStats())
      .map(x => toCsvString(x))
  }

  def query_1(ds: DataStream[KafkaTuple]): List[QueryReturn] = {
    val working_ds = ds
      .filter(x => (x.vault_id >= 1000 && x.vault_id <= 1020))
      .map(x => internalFromKafka(x))
      .keyBy(_.vault_id)

    return List(
      new QueryReturn(impl(working_ds, 1L), "query1_1"),
      new QueryReturn(impl(working_ds, 3L), "query1_3"),
      new QueryReturn(impl(working_ds, 23L), "query1_23")
    )
  }
}
