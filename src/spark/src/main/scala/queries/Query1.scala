package queries

import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.Dataset
import org.apache.spark.sql.Row
import org.apache.spark.sql.functions._

object Query1 {
  private def impl(df: Dataset[Row], wnd: Long): DataFrame = {
    return df
      // select only the necessary columns
      .select("date", "date_ts", "vault_id", "s194_temperature_celsius")
      // add watermark
      .withWatermark("date_ts", "3 minutes")
      .groupBy(
        window(col("date_ts"), s"$wnd days"),
        col("vault_id"),
        col("date")
      )
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
      (impl(filtered_df, 1), "query1_1"),
      (impl(filtered_df, 3), "query1_3"),
      (impl(filtered_df, 23), "query1_23")
    )
  }
}
