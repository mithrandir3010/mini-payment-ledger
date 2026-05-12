#!/bin/bash

KAFKA_CONTAINER="ledger-kafka"
BOOTSTRAP="localhost:9092"

create_topic() {
  local topic=$1
  local partitions=$2
  local retention_ms=$3

  docker exec $KAFKA_CONTAINER kafka-topics \
    --bootstrap-server $BOOTSTRAP \
    --create \
    --if-not-exists \
    --topic "$topic" \
    --partitions "$partitions" \
    --replication-factor 1 \
    --config retention.ms="$retention_ms"

  echo "Topic ready: $topic (partitions=$partitions, retention=${retention_ms}ms)"
}

echo "Creating Kafka topics..."

# 7 gün = 604800000 ms
create_topic "payment.events"        3  604800000

# 30 gün = 2592000000 ms
create_topic "payment.dlq"           1  2592000000

# 3 gün = 259200000 ms
create_topic "payment.notifications" 2  259200000

echo "All topics created."
