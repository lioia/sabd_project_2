#!/bin/bash

echo "Stopping Containers"
docker compose --profile flink --profile spark stop

echo "Removing Containers"
docker compose --profile flink --profile spark rm -f
