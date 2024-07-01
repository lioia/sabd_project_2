import argparse
from typing import List, Tuple

from pyspark.sql import DataFrame, SparkSession
from pyspark.sql.functions import col, from_json
from pyspark.sql.types import (
    FloatType,
    IntegerType,
    StringType,
    StructField,
    StructType,
)

from query_1 import query_1, save_query_1
from query_2 import query_2, save_query_2


def main():
    # create argument parser for query selection
    parser = argparse.ArgumentParser(description="SABD Project 2")
    parser.add_argument("query", type=int, choices=[1, 2], help="Query to Run")
    # parse args
    args = parser.parse_args()

    spark = SparkSession.Builder().appName(f"sabd_{args.query}").getOrCreate()
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
    wnds_query_1: List[Tuple[DataFrame, str]] = []
    wnds_query_2: List[Tuple[DataFrame, str]] = []

    if args.query == 1:
        wnds_query_1 = query_1(parsed_df)
    if args.query == 2:
        wnds_query_2 = query_2(parsed_df)

    save_query_1(wnds_query_1)
    save_query_2(wnds_query_2)

    spark.streams.awaitAnyTermination()


if __name__ == "__main__":
    main()
