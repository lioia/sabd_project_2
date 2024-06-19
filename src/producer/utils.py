from datetime import datetime


def parse_date(date: str) -> datetime:
    return datetime.strptime(date, "%Y-m-%dT%H:%M:%S.%f")


def scaled_delay(date_1: str, date_2: str, scale_factor: float) -> float:
    return (parse_date(date_1) - parse_date(date_2)).total_seconds() / scale_factor
