from typing import Iterable, List, Tuple
from pyflink.common import Time
from pyflink.datastream import DataStream
from pyflink.datastream.data_stream import ProcessAllWindowFunction
from pyflink.datastream.window import (
    GlobalWindows,
    TumblingEventTimeWindows,
)


Query2Tuple = Tuple[int, Tuple[int, List[Tuple[str, str]], str]]


def __to_tuple(x) -> Query2Tuple:
    return x["vault_id"], (1, [x["model"], x["serial_number"]], x["date"][:10])


def __reduce(agg: Query2Tuple, value: Query2Tuple) -> Query2Tuple:
    vault_id, (failures, list_of_hdd, date) = agg
    _, (failures_2, list_of_hdd_2, date_2) = value

    failures += failures_2
    hdds = list_of_hdd + list_of_hdd_2
    date = min(date, date_2)
    return (vault_id, (failures, hdds, date))


class Top10Vaults(ProcessAllWindowFunction):
    def process(
        self,
        context: "ProcessAllWindowFunction.Context",
        elements: Iterable[Query2Tuple],
    ) -> Iterable[str]:
        sorted_vaults = sorted(elements, key=lambda x: x[1][0], reverse=True)[10:]

        # ts
        result = f"{sorted_vaults[0][1][2]}"
        result = ""
        for vault in sorted_vaults:
            # ts,vault_id,failures,
            result += f",{vault[0]},{vault[1][0]}"
            for model, serial in set(vault[1][1]):
                result += f",{model},{serial}"
        yield result


def query_2(ds: DataStream) -> List[Tuple[DataStream, str]]:
    ds = (
        # filter vaults with a failure
        ds.filter(lambda x: x["failure"] > 0)
        # map into (vault_id, (failures, set(model, serial_number), date))
        .map(__to_tuple)
        # key by vault_id
        .key_by(lambda x: x[0])
    )

    window_1_day = (
        # create 1 day window
        ds.window(TumblingEventTimeWindows.of(Time.days(1)))
        # create reduction
        .reduce(__reduce)
        # create 1 day window for all the reduced elements
        .window_all(TumblingEventTimeWindows.of(Time.days(1)))
        # return the top 10 vaults (with correct csv formatting)
        .process(Top10Vaults())
    )
    window_3_days = (
        # create 1 day window
        ds.window(TumblingEventTimeWindows.of(Time.days(3)))
        # create reduction
        .reduce(__reduce)
        # create 3 days window for all the reduced elements
        .window_all(TumblingEventTimeWindows.of(Time.days(3)))
        # return the top 10 vaults (with correct csv formatting)
        .process(Top10Vaults())
    )
    window_all = (
        # create global window
        ds.window(GlobalWindows.create())
        # create reduction
        .reduce(__reduce)
        # create global window for all the reduced elements
        .window_all(GlobalWindows.create())
        # return the top 10 vaults (with correct csv formatting)
        .process(Top10Vaults())
    )

    return [
        (window_1_day, "query_2_day_1"),
        (window_3_days, "query_2_days_3"),
        (window_all, "query_2_days_all"),
    ]
