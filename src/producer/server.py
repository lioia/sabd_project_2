from http.server import BaseHTTPRequestHandler, HTTPServer
import threading
from typing import Any

from kafka import KafkaProducer
import pandas as pd

from replay import dataset_replay


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
        if self.path == "/replay":  # start dataset replay
            self.send_response(200)
            self.end_headers()
            self.wfile.write(b"Starting Data Replay")
            threading.Thread(
                target=dataset_replay,
                args=(self.df, self.scaling_factor, self.producer),
            ).start()
        elif self.path == "/health":  # health-check; used by Docker Compose
            self.send_response(200)
            self.end_headers()
        else:  # not found
            self.send_response(404)
            self.end_headers()

    # Remove Logging
    def log_message(self, format: str, *args: Any) -> None:
        return


def run_server(df: pd.DataFrame, scaling_factor: float, producer: KafkaProducer):
    # Temp Class for passing arguments to handler
    class Handler(DataReplayHandler):
        def __init__(self, *args, **kwargs):
            super().__init__(df, scaling_factor, producer, *args, **kwargs)

    # Create server
    server_address = ("0.0.0.0", 8888)
    httpd = HTTPServer(server_address, Handler)
    print(f"Starting HTTP server on {server_address[0]}:{server_address[1]}")
    # Start server
    httpd.serve_forever()
