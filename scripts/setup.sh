#!/bin/bash

# $1: build, optional

additional_args="-d"

if [[ "$1" == "build" ]]; then
  additional_args="--build -d"
fi

echo "Cleaning up"
docker compose stop
docker compose rm -f

echo "Starting containers"
docker compose up $additional_args

echo "Creating Kafka topics"
# Created by NiFi
# docker exec broker sh -c \
#   "/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic original"
docker exec broker sh -c \
  "/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic filtered"
docker exec broker sh -c \
  "/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic invalid"
