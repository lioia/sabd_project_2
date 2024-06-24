#!/bin/bash

echo "Cleaning up"
docker compose stop
docker compose rm -f

echo "Starting containers"
docker compose up -d

echo "Creating Kafka topics"
docker exec broker sh -c \
  "/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic original"
docker exec broker sh -c \
  "/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic filtered"
docker exec broker sh -c \
  "/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic invalid"
