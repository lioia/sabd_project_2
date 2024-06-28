from typing import List, Tuple
from pyspark.sql import DataFrame, GroupedData
from pyspark.sql.functions import col, collect_set, count, window


# TODO: this does not output the expected result
def __query(df: GroupedData) -> DataFrame:
    return (
        # aggregate by failure, models and serial_numbers
        df.agg(
            count("failure").alias("failures"),
            collect_set("model").alias("models"),
            collect_set("serial_number").alias("serial_numbers"),
        )
        # order by failures
        .orderBy(col("failures").desc())
        # limit to ranking
        .limit(10)
    )


def query_2(df: DataFrame) -> List[Tuple[DataFrame, str]]:
    # filter only failures
    filtered_df = df.filter(df["failure"] > 0)

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
