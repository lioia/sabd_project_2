from datetime import datetime
import logging
import time
from typing import Tuple

from kafka import KafkaProducer
import pandas as pd
import numpy as np


# helper function; convert string date into datetime object
def __parse_date(date: str) -> datetime:
    return datetime.strptime(date, "%Y-%m-%dT%H:%M:%S.%f")


# helper function; write tuple as a csv row
def __tuple_to_string(t: Tuple):
    return ",".join(map(str, t))


def calculate_scaling_factor(df: pd.DataFrame) -> float:
    # last time - first time in seconds
    total_seconds = (
        __parse_date(df["date"].iloc[-1]) - __parse_date(df["date"].iloc[0])
    ).total_seconds()
    replay_seconds = 20 * 60  # 20 minutes
    return (total_seconds + 24 * 60 * 60) / replay_seconds


def dataset_replay(df: pd.DataFrame, scaling_factor: float, producer: KafkaProducer):
    logging.warn(f"Started at {datetime.now()}")

    t = df.iloc[0]
    last_date = t["date"]
    same_day_delay = (24 * 60 * 60) / len(df[df["date"] == last_date])
    # send initial tuple
    producer.send("original", __tuple_to_string(t).encode())
    start = time.time()

    # iterate from second tuple to last
    for t in df.iloc[1:].itertuples():
        # checkpoint
        if (t[0] % 300_000) == 0:
            logging.warn(f"Checkpoint {t[0]} after {time.time() - start} seconds")
            producer.flush()
        # skip headers
        if t[1] == "date":
            continue
        # calculate scaled delay (with random delay in a day)
        delay = (
            (__parse_date(t[1]) - __parse_date(last_date)).total_seconds()
            + same_day_delay
        ) / scaling_factor
        # out-of-order
        if delay < 0:
            logging.warn(f"{t[0]} has negative delay {delay * scaling_factor}")
            delay = 0
        # wait for delay
        time.sleep(delay)
        # send to original topic
        producer.send("original", __tuple_to_string(t[1:]).encode())
        if t[1] != last_date:
            last_date = t[1]
            same_day_delay = (24 * 60 * 60) / len(df[df["date"] == last_date])
            logging.warn(f"New day at {t[0]} after {time.time() - start} seconds")

    # Send new tuple with date after the end of dataset to trigger the 23 day window
    t = "2023-04-26T00:00:00.000000,UNKNOWN,UNKNOWN,0,0,,,,,,,,,,,,,,,,,,,,,0.0,,,,,,,,,,,,,"
    producer.send("original", t.encode())

    # force flush
    producer.flush()
    logging.warn(f"Finished after {time.time() - start} seconds")
