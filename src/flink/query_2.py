from typing import List, Tuple
from pyflink.datastream import DataStream
from pyflink.datastream.formats.csv import CsvSchema


def query_2(ds: DataStream) -> Tuple[List[DataStream], CsvSchema]:
    # TODO: implement
    schema = CsvSchema.builder().build()
    return [], schema
