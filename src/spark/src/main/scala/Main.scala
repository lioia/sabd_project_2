import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.DataTypes
import queries.Query1
import queries.Query2
import org.apache.spark.sql.Dataset
import org.apache.spark.sql.Row
import org.apache.spark.sql.streaming.Trigger
import org.apache.spark.sql.streaming.OutputMode

object SABDSpark {
  def main(args: Array[String]): Unit = {
    if (args.length != 1) {
      println("No query argument was passed; expected 1 or 2")
      return
    }

    // Create spark session
    val spark = SparkSession.builder
      .appName(s"SABD Project 2 Query ${args(0)}")
      .getOrCreate()

    import spark.implicits._

    // Read from Kafka
    val df =
      spark.readStream
        .format("kafka")
        .option("kafka.bootstrap.servers", "broker:19092")
        .option("subscribe", "filtered")
        .option("startingOffsets", "earliest")
        .load()
        // Read value from Kafka as string
        .select(col("value").cast(DataTypes.StringType).as("csv"))
        .as[String]

    val parsed_df = df
      // split kafka value on ','
      .selectExpr("split(csv, ',') as fields")
      // select the necessary fields for both queries
      .select(
        $"fields" (0).cast(DataTypes.StringType).as("date"),
        $"fields" (1).cast(DataTypes.StringType).as("serial_number"),
        $"fields" (2).cast(DataTypes.StringType).as("model"),
        $"fields" (3).cast(DataTypes.IntegerType).as("failure"),
        $"fields" (4).cast(DataTypes.IntegerType).as("vault_id"),
        $"fields" (25).cast(DataTypes.DoubleType).as("s194_temperature_celsius")
      )
      // add new column for date parsed as timestamp (required by window function)
      .withColumn(
        "date_ts",
        to_timestamp(col("date"), "yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
      )

    val wnds: List[(Dataset[Row], String)] =
      if (args(0).toInt == 1)
        Query1.query(parsed_df)
      else if (args(0).toInt == 2)
        Query2.query(parsed_df)
      else
        List()

    val mongoUsername = sys.env("MONGO_USERNAME")
    val mongoPassword = sys.env("MONGO_PASSWORD")

    wnds
      // write each window to csv file
      .map { case (df, prefix) =>
        df.writeStream
          .format("mongodb")
          .option("forceDeleteTempCheckpointLocation", "true")
          .option(
            "spark.mongodb.connection.uri",
            s"mongodb://${mongoUsername}:${mongoPassword}@mongo:27017/"
          )
          .option("spark.mongodb.database", "spark")
          .option("spark.mongodb.collection", s"query_$prefix")
          .outputMode(OutputMode.Append)
          .option(
            "checkpointLocation",
            s"/opt/spark/work-dir/checkpoint/$prefix"
          )
          // defines the batch "size"
          .trigger(Trigger.ProcessingTime("3 minutes"))
          .queryName(prefix)
          .start
      }
      // start each window
      .foreach(_.awaitTermination)
  }
}
