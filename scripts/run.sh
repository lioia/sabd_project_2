#!/bin/bash

docker exec jobmanager sh -c \
  "/opt/flink/bin/flink run --python /app/main.py"
