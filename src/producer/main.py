import os

from nifi import run_nifi

import urllib3
import pandas as pd
from kafka import KafkaProducer

from dataset_replay import calculate_scaling_factor
from server import run_server


def main():
    nifi_username = os.environ.get("NIFI_USERNAME")
    nifi_password = os.environ.get("NIFI_PASSWORD")
    if nifi_username is None or nifi_password is None:
        raise KeyError("Environment variables for NiFi not set")
    run_nifi(nifi_username, nifi_password, "/app/nifi_template.xml")

    producer = KafkaProducer(bootstrap_servers="broker:19092")
    df = pd.read_csv("/app/dataset.csv", dtype=object, keep_default_na=False)
    scaling_factor = calculate_scaling_factor(df)

    # NOTE: dataset replay is activated after a GET request to /
    run_server(df, scaling_factor, producer)


if __name__ == "__main__":
    urllib3.disable_warnings()
    main()
