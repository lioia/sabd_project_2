package queries

import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.Dataset
import org.apache.spark.sql.Row
import org.apache.spark.sql.functions._

object Query1 {
  private def impl(df: Dataset[Row], wnd: Long, offset: Long): DataFrame = {
    return df
      .groupBy(
        window(col("date_ts"), s"$wnd days", s"$wnd days", s"$offset days"),
        col("vault_id")
      )
      // calculate count, avg, stddev
      .agg(
        count("*").alias("count"),
        avg("s194_temperature_celsius").alias("mean_s194"),
        stddev("s194_temperature_celsius").alias("stddev_s194")
      )
      // select output columns
      .select(
        date_format(col("window.start"), "yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
          .as("ts"),
        col("vault_id"),
        col("count"),
        col("mean_s194"),
        col("stddev_s194")
      )
  }

  def query(df: Dataset[Row]): List[(Dataset[Row], String)] = {
    // Filter vaults in [1000, 1020]
    val filtered_df =
      df.filter(col("vault_id") >= 1000 && col("vault_id") <= 1020)
        // select only the necessary columns
        .select("date_ts", "vault_id", "s194_temperature_celsius")

    return List(
      (impl(filtered_df, 1, 0), "query_1"),
      (impl(filtered_df, 3, 2), "query_3"),
      (impl(filtered_df, 23, 13), "query_23")
    )
  }
}
