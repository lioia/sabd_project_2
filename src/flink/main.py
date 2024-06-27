from datetime import datetime
import argparse
from typing import List

from pyflink.common import Types
from pyflink.common.watermark_strategy import TimestampAssigner, WatermarkStrategy
from pyflink.datastream import (
    DataStream,
    RuntimeExecutionMode,
    StreamExecutionEnvironment,
)
from pyflink.datastream.connectors.kafka import (
    KafkaOffsetsInitializer,
    KafkaSource,
)
from pyflink.datastream.formats.csv import CsvSchema
from pyflink.datastream.formats.json import JsonRowDeserializationSchema

from query_2 import query_2
from query_1 import query_1


class CustomTimestampAssigner(TimestampAssigner):
    def extract_timestamp(self, value: dict, record_timestamp: int) -> int:  # type: ignore
        return int(
            datetime.strptime(value["date"], "%Y-%m-%dT%H:%M:%S.%f").timestamp() * 1000
        )


def main():
    # create argument parser for query selection
    parser = argparse.ArgumentParser(description="SABD Project 2")
    # add parser for query selection
    subparsers = parser.add_subparsers(title="Queries", dest="query")
    subparsers.add_parser("1", help="Execute Query 1")
    subparsers.add_parser("2", help="Execute Query 2")
    subparsers.add_parser("3", help="Execute Query 3")
    # parse args
    args = parser.parse_args()

    # Create execution Flink environment
    env = StreamExecutionEnvironment.get_execution_environment()
    env.set_runtime_mode(RuntimeExecutionMode.STREAMING)
    env.set_parallelism(1)
    env.add_python_file("file:///app/query_1.py")
    env.add_python_file("file:///app/query_2.py")

    # Create input type
    row_type_info = Types.ROW_NAMED(
        [
            "date",
            "serial_number",
            "model",
            "failure",
            "vault_id",
            "s194_temperature_celsius",
        ],
        [
            Types.STRING(),
            Types.STRING(),
            Types.STRING(),
            Types.INT(),
            Types.INT(),
            Types.FLOAT(),
        ],
    )
    json_format = (
        JsonRowDeserializationSchema.builder().type_info(row_type_info).build()
    )

    # Create Kafka Source
    source = (
        KafkaSource.builder()
        .set_bootstrap_servers("broker:19092")
        .set_topics("filtered")
        .set_group_id("flink")
        .set_starting_offsets(KafkaOffsetsInitializer.earliest())
        .set_value_only_deserializer(json_format)
        .build()
    )

    ds = (
        # Create DataStream from Kafka Source
        env.from_source(
            source=source,
            # no watermarks as it will be defined from the timestamp
            watermark_strategy=WatermarkStrategy.no_watermarks(),
            source_name="kafka_source",
        )
        # Setting date as timestamp
        .assign_timestamps_and_watermarks(
            WatermarkStrategy.for_monotonous_timestamps().with_timestamp_assigner(
                CustomTimestampAssigner()
            )
        )
    )

    windows: List[DataStream] = []
    schema = CsvSchema.builder().build()
    if args.query == "1":
        windows, schema = query_1(ds)
    elif args.query == "2":
        windows, schema = query_2(ds)

    # TODO: replace print with save to file (commented code)
    for window in windows:
        window.print()
    # sink = FileSink.for_bulk_format(
    #     "/app/output", CsvBulkWriters.for_schema(schema)
    # ).build()
    # ds.sink_to(sink)

    env.execute()


if __name__ == "__main__":
    main()
