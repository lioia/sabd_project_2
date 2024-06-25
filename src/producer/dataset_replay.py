from datetime import datetime
import logging
import time
from typing import Tuple
from concurrent.futures import ThreadPoolExecutor

from kafka import KafkaProducer
import pandas as pd


def parse_date(date: str) -> datetime:
    return datetime.strptime(date, "%Y-%m-%dT%H:%M:%S.%f")


def scaled_delay(date_1: str, date_2: str, scale_factor: float) -> float:
    return (parse_date(date_1) - parse_date(date_2)).total_seconds() / scale_factor


def calculate_scaling_factor(df: pd.DataFrame) -> float:
    original_duration = (
        parse_date(df["date"].iloc[-1]) - parse_date(df["date"].iloc[0])
    ).total_seconds()
    desired_duration = 5 * 60  # 5 minutes
    return original_duration / desired_duration


def tuple_to_string(t: Tuple) -> str:
    return ",".join(map(str, t))


def tuple_to_bytes(t: Tuple) -> bytes:
    return str.encode(tuple_to_string(t))


def send_to_kafka(producer: KafkaProducer, tuple: Tuple):
    try:
        future = producer.send("original", tuple_to_bytes(tuple))
        future.get(timeout=10)
    except Exception as e:
        logging.error(f"Error sending tuple {tuple}: {e}")


def dataset_replay(df: pd.DataFrame, scaling_factor: float, producer: KafkaProducer):
    last_tuple = df.iloc[0]
    send_to_kafka(producer, last_tuple)
    start = time.time()
    logging.warn(f"Dataset Replay started at {datetime.now()}")

    with ThreadPoolExecutor(max_workers=5) as executor:
        for i, t in df.iloc[1:].iterrows():
            if t["date"] == "date":
                continue
            delay = scaled_delay(last_tuple["date"], t["date"], scaling_factor)
            if delay < 0:
                logging.warn(f"Tuple {i} has negative delay {delay}")
                delay = 0
            time.sleep(max(delay, 0))
            executor.submit(send_to_kafka, producer, t)
            last_tuple = t
    producer.flush()
    logging.warn(f"Dataset Replay finished after {time.time() - start} seconds")
