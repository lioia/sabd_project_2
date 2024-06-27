#!/bin/bash

# $1: query number (1, 2 or empty for both)

# Start Dataset Replay
docker exec jobmanager sh -c \
  "curl http://producer:8888/replay"

# Run Query 1
if [[ -z "$1" || "$1" == "1" ]]; then
  docker exec jobmanager sh -c \
    "/opt/flink/bin/flink run --python /app/main.py 1"
fi

# Run Query 2
if [[ -z "$1" || "$1" == "2" ]]; then
  docker exec jobmanager sh -c \
    "/opt/flink/bin/flink run --python /app/main.py 2"
fi
