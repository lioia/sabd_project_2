from datetime import datetime

from pyflink.common import WatermarkStrategy, Types
from pyflink.datastream import StreamExecutionEnvironment, RuntimeExecutionMode
from pyflink.datastream.connectors.kafka import KafkaOffsetsInitializer, KafkaSource
from pyflink.datastream.formats.json import JsonRowDeserializationSchema

import requests


def timestamp_assigner(event) -> float:
    return datetime.strptime(event["date"], "%Y-%m-%dT%H:%M:%S.%f").timestamp()


def main():
    # Start dataset replay
    response = requests.get("http://producer:8888/", verify=False)
    response.raise_for_status()

    # Create execution Flink environment
    env = StreamExecutionEnvironment.get_execution_environment()
    env.set_runtime_mode(RuntimeExecutionMode.STREAMING)
    env.set_parallelism(1)

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

    source = (
        KafkaSource.builder()
        .set_bootstrap_servers("broker:19092")
        .set_topics("filtered")
        .set_group_id("flink")
        .set_starting_offsets(KafkaOffsetsInitializer.earliest())
        .set_value_only_deserializer(json_format)
        .build()
    )

    ds = env.from_source(
        source=source,
        watermark_strategy=WatermarkStrategy.for_monotonous_timestamps().with_timestamp_assigner(
            lambda event, _: timestamp_assigner(event)  # type: ignore
        ),
        source_name="kafka_source",
    )
    ds.print()


if __name__ == "__main__":
    main()
