from datetime import datetime
import logging
import time
from typing import Tuple

from kafka import KafkaProducer
import pandas as pd


def __parse_date(date: str) -> datetime:
    return datetime.strptime(date, "%Y-%m-%dT%H:%M:%S.%f")


# applies scaling factor between two dates
def __scaled_delay(date_1: str, date_2: str, scale_factor: float) -> float:
    return (__parse_date(date_1) - __parse_date(date_2)).total_seconds() / scale_factor


def calculate_scaling_factor(df: pd.DataFrame) -> float:
    # last time - first time in seconds
    original_duration = (
        __parse_date(df["date"].iloc[-1]) - __parse_date(df["date"].iloc[0])
    ).total_seconds()
    desired_duration = 20 * 60  # 20 minutes
    return original_duration / desired_duration


# helper function; write tuple as a csv row
def __tuple_to_string(t: Tuple):
    return ",".join(map(str, t))


def dataset_replay(df: pd.DataFrame, scaling_factor: float, producer: KafkaProducer):
    last_tuple = df.iloc[0]
    # send initial tuple
    producer.send("original", __tuple_to_string(last_tuple).encode())
    start = time.time()
    logging.warn(f"Started at {datetime.now()}")

    # iterate from second tuple to last (not included)
    for i, t in df.iloc[1:].iterrows():
        # checkpoint
        if (i % 750000) == 0:
            logging.warn(f"Checkpoint {i} after {time.time() - start} seconds")
        # skip headers
        if t["date"] == "date":
            continue
        # calculate scaled delay
        delay = __scaled_delay(last_tuple["date"], t["date"], scaling_factor)
        # out-of-order
        if delay < 0:
            logging.warn(f"{i} has negative delay {delay * scaling_factor}")
            delay = 0
        # wait for delay
        time.sleep(delay)
        # send to original topic (add new value to indicate whether it's the end or not)
        producer.send("original", __tuple_to_string(t).encode())
        last_tuple = t
    # force flush
    producer.flush()
    logging.warn(f"Finished after {time.time() - start} seconds")
