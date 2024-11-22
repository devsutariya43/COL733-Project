#!/bin/bash

# Stop and remove all containers with names starting with "redis-master-" or "redis-replica-"
for container in $(docker ps -a --format "{{.Names}}" | grep -E '^redis-master-[0-9]+$|^redis-replica-[0-9]+$')
do
  echo "Stopping container: $container"
  docker stop "$container"
  echo "Removing container: $container"
  docker rm "$container"
done

echo "Redis cluster deleted!"
