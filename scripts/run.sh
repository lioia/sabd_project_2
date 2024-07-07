#!/bin/bash

# $1: flink or spark, required
# $2: query: 1 or 2

# Argument check
if [[ -z "$1" && "$1" != "flink" && "$1" != "spark" ]]; then
  echo "Profile argument required; got $1, expected flink or spark"
  exit 1
fi

if [[ "$2" != "1" && "$2" != "2" ]]; then
  echo "Query argument required; got $1, expected 1 or 2"
  exit 1
fi


# Syntax: start_flink <query>
start_flink() {
  # TODO: check if it is necessary
  docker exec taskmanager sh -c \
    "chown -R flink:flink /opt/flink/output"
  docker exec jobmanager sh -c \
    "/opt/flink/bin/flink run /opt/flink/sabd.jar $1"
}

# Syntax: start_spark <query>
start_spark() {
  docker exec spark-master sh -c \
    "/opt/spark/bin/spark-submit \
      --master spark://spark-master:7077 \
      --conf spark.cores.max=3 \
      --conf spark.executor.cores=1 \
      --conf spark.ui.prometheus.enabled=true \
      --conf spark.sql.streaming.metricsEnabled=true \
      --conf spark.metrics.conf=/opt/spark/conf/metrics.properties \
      --conf spark.metrics.namespace=sabd \
      --class SABDSpark \
      --conf spark.driver.extraJavaOptions=\"-Divy.cache.dir=/tmp -Divy.home=/tmp\" \
      --packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.1,org.mongodb.spark:mongo-spark-connector_2.12:10.3.0 \
      /opt/spark/work-dir/sabd.jar $1"
}

# Syntax setup_kafka
setup_kafka() {
  echo "Creating Kafka topics"
  # NOTE: this should not be necessary as NiFi already creates this topic
  # docker exec broker sh -c \
  #   "/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic original"
  docker exec broker sh -c \
    "/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic filtered"
  docker exec broker sh -c \
    "/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic invalid"
}

# Syntax: main <framework> <query>
main() {
  echo "Cleaning up"
  docker compose --profile flink --profile spark stop
  docker compose --profile flink --profile spark rm -f

  echo "Starting containers"
  docker compose --profile $1 up --build -d

  setup_kafka

  echo "Starting Dataset Replay"
  docker exec producer sh -c "curl http://localhost:8888/replay"

  if [[ "$1" == "flink" ]]; then
    # start query
    start_flink $2
  fi

  if [[ "$1" == "spark" ]]; then
    echo "Starting Spark"

    docker exec spark-master sh -c \
      "/opt/spark/sbin/start-master.sh"
    docker exec spark-worker-1 sh -c \
      "/opt/spark/sbin/start-worker.sh spark://spark-master:7077"
    docker exec spark-worker-2 sh -c \
      "/opt/spark/sbin/start-worker.sh spark://spark-master:7077"

    # start query
    start_spark $2
  fi
}

main $1 $2
