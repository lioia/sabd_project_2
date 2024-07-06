package queries

import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.Dataset
import org.apache.spark.sql.Row
import org.apache.spark.sql.functions._
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.Column
import org.apache.spark.sql.streaming.Trigger
import scala.collection.mutable.ListBuffer

object Query2 {
  private def impl(df: Dataset[Row], wnd: String): DataFrame = {
    return df
      // select only the necessary columns
      .select(
        "date",
        "date_ts",
        "vault_id",
        "failure",
        "model",
        "serial_number"
      )
      // add watermark
      .withWatermark("date_ts", wnd)
      .groupBy(window(col("date_ts"), wnd), col("vault_id"))
      // calculate sum of failures and list of (model, serial_number)
      .agg(
        count("failure").alias("failures"),
        collect_set(concat_ws(",", col("model"), col("serial_number")))
          .as("hdds")
      )
      // order by failures
      .orderBy(desc("failures"))
      // create ranking
      .limit(10)
      // select ts,vault_id,failures,list(model, serial_number)
      .select(
        col("window.start").as("ts"),
        col("vault_id"),
        col("failures"),
        col("hdds")
      )
      // group by timestamp
      .groupBy("ts")
      // aggregate all the vaults with the same timestamp into a list
      .agg(
        collect_list(struct(col("vault_id"), col("failures"), col("hdds")))
          .as("rankings")
      )
      // select as ts,ranking
      .select(
        col("ts") +:
          (1 to 10).flatMap(i =>
            Seq(
              expr(s"if(size(rankings) >= $i, rankings[${i - 1}].vault_id, '')")
                .alias(s"vault_id_$i"),
              expr(s"if(size(rankings) >= $i, rankings[${i - 1}].failures, 0)")
                .alias(s"failures_$i"),
              expr(
                s"if(size(rankings) >= $i, rankings[${i - 1}].hdds, array())"
              ).alias(s"hdss$i")
            )
          ): _*
      )
  }

  def query(df: Dataset[Row]): List[(Dataset[Row], String)] = {
    // Filter only vaults with failures
    val filtered_df = df.filter(col("failure") > 0)

    return List(
      (impl(df, "1 day"), "query2_1"),
      (impl(df, "3 days"), "query2_3"),
      (impl(df, "23 days"), "query2_23")
    )
  }
}
