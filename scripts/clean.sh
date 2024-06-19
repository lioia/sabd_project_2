#!/bin/bash

echo "Stopping Containers"
docker compose stop

echo "Removing Containers"
docker compose rm -f
