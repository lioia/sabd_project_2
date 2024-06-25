from http.server import BaseHTTPRequestHandler, HTTPServer
import threading
from typing import Any

from kafka import KafkaProducer
import pandas as pd

from dataset_replay import dataset_replay


class DataReplayHandler(BaseHTTPRequestHandler):
    def __init__(
        self,
        df: pd.DataFrame,
        scaling_factor: float,
        producer: KafkaProducer,
        *args,
        **kwargs,
    ):
        self.df = df
        self.scaling_factor = scaling_factor
        self.producer = producer
        super().__init__(*args, **kwargs)

    def do_GET(self):
        if self.path == "/replay":
            self.send_response(200)
            self.end_headers()
            self.wfile.write(b"Starting Data Replay")
            threading.Thread(
                target=dataset_replay,
                args=(self.df, self.scaling_factor, self.producer),
            ).start()
        elif self.path == "/health":
            self.send_response(200)
            self.end_headers()
        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, format: str, *args: Any) -> None:
        return


def run_server(df: pd.DataFrame, scaling_factor: float, producer: KafkaProducer):
    class Handler(DataReplayHandler):
        def __init__(self, *args, **kwargs):
            super().__init__(df, scaling_factor, producer, *args, **kwargs)

    server_address = ("0.0.0.0", 8888)
    httpd = HTTPServer(server_address, Handler)
    print(f"Starting HTTP server on {server_address[0]}:{server_address[1]}")
    httpd.serve_forever()
