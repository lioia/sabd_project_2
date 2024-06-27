import math
from typing import Tuple, List

from pyflink.common import Time
from pyflink.datastream import DataStream
from pyflink.datastream.formats.csv import CsvSchema
from pyflink.datastream.window import TumblingEventTimeWindows
from pyflink.table.types import DataTypes
from pyflink.datastream import FilterFunction, ReduceFunction


class FilterVaults(FilterFunction):
    def filter(self, value):
        return value["vault_id"] >= 1000 and value["vault_id"] <= 1020


# TODO: check if stddev is computed correctly
class ReduceVaults(ReduceFunction):
    # value with (count, mean, m2)
    def reduce(self, value1, value2):  # type: ignore
        count1, mean1, m2_1, date1 = value1[1]
        count2, mean2, m2_2, date2 = value2[1]

        count = count1 + count2
        delta = mean2 - mean1
        mean = mean1 + delta * (count2 / count)
        m2 = m2_1 + m2_2 + delta**2 * count1 * count2 / count
        date = min(date1, date2)

        # value1[0] should be equal to value2[0]
        return (value1[0], (count, mean, m2, date))


def query_1(ds: DataStream) -> Tuple[List[DataStream], CsvSchema]:
    ds = (
        # filter vaults between 1000 and 1020
        ds.filter(FilterVaults())
        # map into (vault_id, (count, mean, variance, date))
        .map(
            lambda x: (
                x["vault_id"],
                (1, x["s194_temperature_celsius"], 1, x["date"][:10]),
            )
        )
        # key by vault_id
        .key_by(lambda x: x[0])
    )

    window_1_day = (
        # create 1 day window
        ds.window(TumblingEventTimeWindows.of(Time.days(1)))
        # create reduction
        .reduce(ReduceVaults())
        # map into (vault_id, count, mean, stddev)
        .map(lambda x: (x[1][3], x[0], x[1][0], x[1][1], math.sqrt(x[1][2])))
    )
    # window_3_days = ds.window(TumblingEventTimeWindows.of(Time.days(3)))
    # window_all = ds.window(GlobalWindows.create())

    # Output Schema
    schema = (
        CsvSchema.builder()
        .add_string_column("timestamp")
        .add_number_column("vault_id", number_type=DataTypes.INT())
        .add_number_column("count", number_type=DataTypes.INT())
        .add_number_column("mean_s194", number_type=DataTypes.FLOAT())
        .add_number_column("stddev_s194", number_type=DataTypes.FLOAT())
        .set_column_separator(",")
        .build()
    )

    return [window_1_day], schema
