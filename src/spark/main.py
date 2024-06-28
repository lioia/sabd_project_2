import argparse
from typing import List, Tuple

from pyarrow import StructType
from pyspark.sql import DataFrame, SparkSession
from pyspark.sql.functions import col, from_json
from pyspark.sql.types import (
    FloatType,
    IntegerType,
    StringType,
    StructField,
)

from query_1 import query_1
from query_2 import query_2


def main():
    # create argument parser for query selection
    parser = argparse.ArgumentParser(description="SABD Project 2")
    parser.add_argument("query", type=int, choices=[1, 2], help="Query to Run")
    # parse args
    args = parser.parse_args()

    spark = SparkSession.Builder().appName("sabd").getOrCreate()
    df = (
        # read from Kafka
        spark.readStream.format("kafka")
        # at broker:19092
        .option("kafka.bootstrap.servers", "broker:19092")
        # from filtered topic
        .option("subscribe", "filtered")
        .load()
    )

    # schema of the required fields
    schema = StructType(
        [
            StructField("date", StringType(), True),
            StructField("vault_id", IntegerType(), True),
            StructField("model", StringType(), True),
            StructField("serial_number", StringType(), True),
            StructField("failure", IntegerType(), True),
            StructField("s194_temperature_celsius", FloatType(), True),
        ]
    )

    parsed_df = (
        # parse json value from Kafka
        df.select(from_json(col("value").cast("string"), schema).alias("data"))
        # select the data
        .select("data.*")
    )

    # windows with prefix name
    wnds: List[Tuple[DataFrame, str]] = []

    if args.query == 1:
        wnds += query_1(parsed_df)
    if args.query == 2:
        wnds += query_2(parsed_df)

    for wnd, prefix in wnds:
        output = f"/app/output/{prefix}.csv"
        # write to csv file
        wnd.writeStream.format("csv").option("path", output).start()

    spark.streams.awaitAnyTermination()


if __name__ == "__main__":
    main()
