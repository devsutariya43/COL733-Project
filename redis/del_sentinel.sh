#!/bin/bash

echo "Stopping and removing Redis and Sentinel containers..."

# Stop and remove Redis master container
docker stop redis-master 2>/dev/null
docker rm redis-master 2>/dev/null

# Stop and remove all Redis replicas
for container in $(docker ps -a --filter "name=redis-replica" --format "{{.Names}}"); do
    docker stop "$container" 2>/dev/null
    docker rm "$container" 2>/dev/null
done

# Stop and remove all Sentinel containers
for container in $(docker ps -a --filter "name=redis-sentinel" --format "{{.Names}}"); do
    docker stop "$container" 2>/dev/null
    docker rm "$container" 2>/dev/null
done

# Remove the Docker network
echo "Removing Docker network..."
docker network rm redis-cluster-net 2>/dev/null

# Remove all Sentinel configuration files
echo "Removing Sentinel configuration files..."
rm -f sentinel*.conf

echo "Cleanup complete."
