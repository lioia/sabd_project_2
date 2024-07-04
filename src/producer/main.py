import os

from nifi import run_nifi

import urllib3
import pandas as pd
from kafka import KafkaProducer

from replay import calculate_scaling_factor
from server import run_server


def main():
    nifi_username = os.environ.get("NIFI_USERNAME")
    nifi_password = os.environ.get("NIFI_PASSWORD")
    if nifi_username is None or nifi_password is None:
        raise KeyError("Environment variables for NiFi not set")
    # Start NiFi Flow
    run_nifi(nifi_username, nifi_password, "/app/nifi_template.xml")

    # Create Kafka Producer
    producer = KafkaProducer(bootstrap_servers="broker:19092", linger_ms=5)
    # Load Dataset (without editing anything)
    df = pd.read_csv("/app/dataset.csv", dtype=object, keep_default_na=False)

    # Calculate scaling factor
    scaling_factor = calculate_scaling_factor(df)

    # Run server
    run_server(df, scaling_factor, producer)

    # Cleanup
    producer.close()


if __name__ == "__main__":
    # Disable NiFi warnings (insecure connection)
    urllib3.disable_warnings()
    main()
