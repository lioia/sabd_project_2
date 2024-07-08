import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.DataTypes
import queries.Query1
import queries.Query2
import org.apache.spark.sql.Row
import org.apache.spark.sql.streaming.Trigger
import org.apache.spark.sql.streaming.OutputMode
import org.apache.spark.sql.streaming.DataStreamWriter

object SABDSpark {
  def main(args: Array[String]): Unit = {
    val mongoUsername = sys.env("MONGO_USERNAME")
    val mongoPassword = sys.env("MONGO_PASSWORD")

    if (args.length != 1 || mongoUsername == "" || mongoPassword == "") {
      println("No query argument was passed; expected 1 or 2")
      println("Make sure to add MONGO_USERNAME and MONGO_PASSWORD as env vars")
      return
    }

    // Create spark session
    val spark = SparkSession.builder
      .appName(s"SABD Project 2 Query ${args(0)}")
      .getOrCreate()

    if (args(0).toInt == 2)
      spark.conf.set(
        "spark.sql.streaming.statefulOperator.checkCorrectness.enabled",
        "false"
      )

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
        to_date(col("date"), "yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
          .cast(DataTypes.TimestampType)
      )
      // add watermark
      .withWatermark("date_ts", "3 minutes")

    val wnds: List[(DataStreamWriter[Row], String)] =
      if (args(0).toInt == 1)
        Query1.query(parsed_df).map { case (df, prefix) =>
          (
            df.writeStream
              .format("mongodb")
              .option("forceDeleteTempCheckpointLocation", "true")
              .option(
                "spark.mongodb.connection.uri",
                s"mongodb://${mongoUsername}:${mongoPassword}@mongo:27017/"
              )
              .option("spark.mongodb.database", "spark")
              .option("spark.mongodb.collection", s"${prefix}_${args(0)}")
              .outputMode(OutputMode.Append)
              .option(
                "checkpointLocation",
                s"/opt/spark/work-dir/checkpoint/${prefix}_${args(0)}"
              ),
            prefix
          )
        }
      else if (args(0).toInt == 2)
        Query2.query(parsed_df).map { case (df, prefix) =>
          (
            df.writeStream
              .format("console")
              .outputMode(OutputMode.Complete)
              .option("truncate", "false"),
            prefix
          )
        }
      else
        List()

    wnds
      .map { case (df, prefix) =>
        // defines the batch "size"
        df.trigger(Trigger.ProcessingTime("3 minutes"))
          .queryName(prefix)
          .start
      }
      // start each window
      .foreach(_.awaitTermination)
  }
}
