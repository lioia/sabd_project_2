import os
import time

from producer.nifi import run_nifi

import urllib3
import pandas as pd
from kafka import KafkaProducer

from producer.utils import parse_date, scaled_delay


def main():
    nifi_username = os.environ.get("NIFI_USERNAME")
    nifi_password = os.environ.get("NIFI_PASSWORD")
    if nifi_username is None or nifi_password is None:
        raise KeyError("Environment variables for NiFi not set")
    run_nifi(nifi_username, nifi_password, "/app/nifi_template.xml")

    producer = KafkaProducer(bootstrap_servers="broker:9092")

    df = pd.read_csv("/app/dataset.csv")

    original_duration = (
        parse_date(df["date"].iloc[-1]) - parse_date(df["date"].iloc[0])
    ).total_seconds()
    desired_duration = 5 * 60  # 5 minutes
    scaling_factor = desired_duration / original_duration

    last_tuple = df.iloc[0]
    producer.send("original", last_tuple)

    for t in df.iloc[1:].itertuples():
        delay = scaled_delay(last_tuple["date"], t["date"], scaling_factor)
        time.sleep(delay)
        producer.send("original", t)
        last_tuple = t


if __name__ == "__main__":
    urllib3.disable_warnings()
    main()
