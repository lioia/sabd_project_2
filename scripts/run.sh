#!/bin/bash

# $1: flink or spark
# $2: query number (1, 2 or all for both)
# $3: workers (default: 1)

w=1

if [[ "$1" != "flink" && "$1" != "spark" ]]; then
  echo "$1 invalid; expected: flink or spark"
  exit 1
fi

if [[ "$2" != "1" && "$2" != "1" && "$2" != "all" ]]; then
  echo "$2 invalid; expected: 1, 2 or all"
  exit 1
fi

if [[ -n "$3" ]]; then
  w="$3"
fi

# Start Dataset Replay
docker exec jobmanager sh -c "curl http://producer:8888/replay"

if [[ "$1" == "flink" ]]; then
  # TODO: scale taskmanager

  # Run Query 1
  if [[ "$2" == "all" || "$2" == "1" ]]; then
    docker exec jobmanager sh -c \
      "/opt/flink/bin/flink run --python /app/main.py 1"
  fi

  # Run Query 2
  if [[ "$2" == "all" || "$2" == "2" ]]; then
    docker exec jobmanager sh -c \
      "/opt/flink/bin/flink run --python /app/main.py 2"
  fi
fi

if [[ "$1" == "spark" ]]; then
  # Run Query 1
  if [[ -z "$2" || "$2" == "1" ]]; then
    docker exec spark-master sh -c \
      "/opt/spark/bin/spark-submit \
        --master spark://spark-master:7077 \
        --py-files /app/main.py,/app/query_1.py,/app/query_2.py \
        --conf \"spark.cores.max=$w\" \
        --conf \"spark.executor.cores=1\" \
        /app/main.py 1"
  fi

  # Run Query 2
  if [[ -z "$2" || "$2" == "2" ]]; then
    docker exec spark-master sh -c \
      "/opt/spark/bin/spark-submit \
        --master spark://spark-master:7077 \
        --py-files /app/main.py,/app/query_1.py,/app/query_2.py \
        --conf \"spark.cores.max=$w\" \
        --conf \"spark.executor.cores=1\" \
        /app/main.py 1"
  fi
fi
