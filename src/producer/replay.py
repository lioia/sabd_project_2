from datetime import datetime, timedelta
import logging
import time
import csv
from typing import Dict, List

from kafka import KafkaProducer


def dataset_replay(producer: KafkaProducer):
    logging.warning(f"Started at {datetime.now()}")

    format = "%Y-%m-%dT%H:%M:%S.%f"
    speed = 1440
    dates: Dict[str, List[str]] = dict()

    start = time.time()
    # load csv
    with open("/app/dataset.csv", "r") as csvfile:
        reader = csv.reader(csvfile)
        for t in reader:
            # skip headers
            if t[0] == "date":
                continue
            dates.setdefault(t[0], []).append(",".join(t))
    logging.warning(f"Completed reading the dataset after {time.time()-start:.2f}s")
    logging.warning(f"Starting replay at {datetime.now()}")

    # sort by date
    sorted_dates = sorted(dates.items())
    last_date = datetime.strptime(sorted_dates[0][0], format)

    start = time.time()
    for date, tuples in sorted_dates:
        parsed_date = datetime.strptime(date, format)
        # delay between two days
        if parsed_date != last_date:
            delay = (parsed_date - last_date) / speed
            time.sleep(delay.total_seconds())
        # evenly spread the events in the same day
        same_day_delay = timedelta(days=1) / len(tuples)
        for i, t in enumerate(tuples):
            # set new date in the tuple
            new_date = (parsed_date + same_day_delay * i).strftime(format)
            t = new_date + t[26:]
            # send tuple to kafka
            producer.send("original", t.encode())
            # sleep
            time.sleep(same_day_delay.total_seconds() / speed)
            last_date = datetime.strptime(t[:26], format)
        # flush after each day
        producer.flush()
        logging.warning(f"\tCompleted date {date} after {time.time() - start}")

    # Send final tuple (trigger 23 days window)
    producer.send(
        "original",
        "2023-04-26T00:00:00.000000,UNKNOWN,UNKNOWN,0,0,,,,,,,,,,,,,,,,,,,,,0.0,,,,,,,,,,,,,".encode(),
    )
    producer.flush()

    logging.warning(f"Finished after {time.time() - start:.2f} seconds")
