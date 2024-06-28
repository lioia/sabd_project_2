import math
from typing import List, Tuple

from pyflink.common import Time
from pyflink.datastream import DataStream
from pyflink.datastream.window import GlobalWindows, TumblingEventTimeWindows


def __to_tuple(x) -> Tuple[int, Tuple[int, float, float, int]]:
    return (x["vault_id"], (1, x["s194_temperature_celsius"], 1, x["date"][:10]))


def __reduce(
    agg: Tuple[int, Tuple[int, float, float, str]],
    value: Tuple[int, Tuple[int, float, float, str]],
) -> Tuple[int, Tuple[int, float, float, str]]:
    vault_id, (count, mean, m2, date) = agg
    _, new_value, _, new_date = value[1]

    count += 1
    delta = new_value - mean
    mean += delta / count
    delta2 = new_value - mean
    m2 += delta * delta2
    date = min(date, new_date)

    # (vault_id = agg[0]) should be equal to value[0]
    return (vault_id, (count, mean, m2, date))


def __to_csv_string(x: Tuple) -> str:
    stddev = math.sqrt(x[1][2] / x[1][0])
    return f"{x[1][3]},{x[0]},{x[1][0]},{x[1][1]:.3f},{stddev:.3f}"


def query_1(ds: DataStream) -> List[Tuple[DataStream, str]]:
    ds = (
        # filter vaults between 1000 and 1020
        ds.filter(lambda x: x["vault_id"] >= 1000 and x["vault_id"] <= 1020)
        # map into (vault_id, (count, mean, stddev, date))
        .map(__to_tuple)
        # key by vault_id
        .key_by(lambda x: x[0])
    )

    window_1_day = (
        # create 1 day window
        ds.window(TumblingEventTimeWindows.of(Time.days(1)))
        # create reduction
        .reduce(__reduce)
        # map into csv string: vault_id, count, mean, stddev
        .map(__to_csv_string)
    )
    window_3_days = (
        # create 1 day window
        ds.window(TumblingEventTimeWindows.of(Time.days(3)))
        # create reduction
        .reduce(__reduce)
        # map into csv string: vault_id, count, mean, stddev
        .map(__to_csv_string)
    )
    window_all = (
        # create 1 day window
        ds.window(GlobalWindows.create())
        # create reduction
        .reduce(__reduce)
        # map into csv string: vault_id, count, mean, stddev
        .map(__to_csv_string)
    )

    return [
        (window_1_day, "query_1_day_1"),
        (window_3_days, "query_1_days_3"),
        (window_all, "query_1_days_all"),
    ]
