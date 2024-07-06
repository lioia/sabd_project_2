package queries

import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.Dataset
import org.apache.spark.sql.Row
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.Trigger
import org.apache.spark.sql.streaming.StreamingQuery

object Query1 {
  private def impl(df: Dataset[Row], wnd: String): DataFrame = {
    return df
      // select only the necessary columns
      .select("date", "date_ts", "vault_id", "s194_temperature_celsius")
      // add watermark
      .withWatermark("date_ts", wnd)
      .groupBy(window(col("date_ts"), wnd), col("vault_id"), col("date"))
      // calculate count, avg, stddev
      .agg(
        count("*").alias("count"),
        avg("s194_temperature_celsius").alias("mean_s194"),
        stddev("s194_temperature_celsius").alias("stddev_s194")
      )
      // select output columns
      .select("date", "vault_id", "count", "mean_s194", "stddev_s194")
  }

  def query(df: Dataset[Row]): List[(Dataset[Row], String)] = {
    // Filter vaults in [1000, 1020]
    val filtered_df =
      df.filter(col("vault_id") >= 1000 && col("vault_id") <= 1020)

    return List(
      (impl(df, "1 day"), "query1_1"),
      (impl(df, "3 days"), "query1_3"),
      (impl(df, "23 days"), "query1_23")
    )
  }
}
