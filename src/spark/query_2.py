from typing import List, Tuple
from pyspark.sql import DataFrame, GroupedData, SparkSession
from pyspark.sql.functions import col, collect_set, concat_ws, count, window


def __query(df: GroupedData) -> DataFrame:
    return (
        # aggregate by failure, models and serial_numbers
        df.agg(
            count("failure").alias("failures"),
            collect_set(
                concat_ws(
                    ",",
                    col("model"),
                    col("serial_number"),
                )
            ).alias("failed_hdds"),
        )
        # order by failures
        .orderBy(col("failures").desc())
        # limit to ranking
        .limit(10)
    )


def __format(df: DataFrame, prefix: str, spark: SparkSession):
    # get all the item from the batch
    rows = df.collect()
    # create output rows with the initial date of the batch
    output_rows = [rows[0]["date"]]
    # iterate through all the rows (ranking positions)
    for row in rows:
        # create list of failed hard disks
        hdds = ",".join(row["failed_hdds"])
        # output string of vault (with failures and failed hdds)
        ranking = f"{row["vault_id"]},{row["failures"]}({hdds})"
        # add ranking to all rows
        output_rows.append(ranking)

    # create dataframe containing a single item by joining the ranking in a single line
    output_df = spark.createDataFrame([(",".join(output_rows),)])
    # write as text to output file
    output_df.write.mode("append").text(f"/app/output/{prefix}")


def save_query_2(wnds: List[Tuple[DataFrame, str]], spark: SparkSession):
    for wnd, prefix in wnds:
        (
            # process stream
            wnd.writeStream
            # in complete mode
            .outputMode("complete")
            # format and write output batch
            .foreachBatch(lambda df, _: __format(df, prefix, spark))
            .start()
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
