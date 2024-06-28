from typing import List, Tuple
from pyspark.sql import DataFrame, GroupedData
from pyspark.sql.functions import avg, count, stddev, window


def __query(df: GroupedData) -> DataFrame:
    # calculate count, mean and stddev
    return df.agg(
        count("*").alias("count"),
        avg("s194_temperature_celsius").alias("mean_s194"),
        stddev("s194_temperature_celsius").alias("stddev_s194"),
    )


def query_1(df: DataFrame) -> List[Tuple[DataFrame, str]]:
    # filter vault_id in [1000, 1020]
    filtered_df = df.filter((df["vault_id"] >= 1000) & (df["vault_id"] <= 1020))

    window_1_day = (
        # set window size (1 day)
        filtered_df.withWatermark("date", "1 day")
        # group by window size and vault_id
        .groupBy(window("date", "1 day"), "vault_id")
    )
    window_3_days = (
        # set window size (1 day)
        filtered_df.withWatermark("date", "3 days")
        # group by window size and vault_id
        .groupBy(window("date", "3 days"), "vault_id")
    )

    # group only by vault_id
    window_all = filtered_df.groupBy("vault_id")

    return [
        (__query(window_1_day), "query_1_day_1"),
        (__query(window_3_days), "query_1_days_3"),
        (__query(window_all), "query_1_days_all"),
    ]
