#!/bin/bash

# Check if the required number of replicas is provided
if [ -z "$1" ]; then
  echo "Usage: $0 <number_of_replicas>"
  exit 1
fi

# Step 1: Define number of replicas and create Docker Network
NUM_REPLICAS=$1
docker network rm redis-cluster-net
docker network create redis-cluster-net

# Step 2: Run Redis Master
echo "Starting Redis master instance..."
docker run -d --name redis-master --network redis-cluster-net -p 6379:6379 redis:latest

# Step 3: Start the specified number of Redis Replicas
for i in $(seq 1 "$NUM_REPLICAS"); do
  echo "Starting Redis replica instance $i..."
  docker run -d --name redis-replica$i --network redis-cluster-net -p $((6379 + i)):6379 redis:latest redis-server --replicaof redis-master 6379
done

# Step 4: Get the Redis Master's IP Address
REDIS_MASTER_IP=$(docker inspect -f '{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}' redis-master)
echo "Redis master IP address: $REDIS_MASTER_IP"

# Step 5: Create Sentinel Configuration Files with Redis Master IP
echo "Creating Sentinel configuration files..."
cat <<EOL > sentinel1.conf
port 26379
dir /tmp
sentinel monitor mymaster $REDIS_MASTER_IP 6379 2
sentinel down-after-milliseconds mymaster 5000
sentinel failover-timeout mymaster 10000
sentinel parallel-syncs mymaster 1
EOL

cat <<EOL > sentinel2.conf
port 26380
dir /tmp
sentinel monitor mymaster $REDIS_MASTER_IP 6379 2
sentinel down-after-milliseconds mymaster 5000
sentinel failover-timeout mymaster 10000
sentinel parallel-syncs mymaster 1
EOL

cat <<EOL > sentinel3.conf
port 26381
dir /tmp
sentinel monitor mymaster $REDIS_MASTER_IP 6379 2
sentinel down-after-milliseconds mymaster 5000
sentinel failover-timeout mymaster 10000
sentinel parallel-syncs mymaster 1
EOL

# Step 6: Run Sentinel Containers
echo "Starting Redis Sentinel instances..."
docker run -d --name redis-sentinel1 --network redis-cluster-net -p 26379:26379 -v "$(pwd)/sentinel1.conf:/etc/sentinel.conf" redis:latest redis-sentinel /etc/sentinel.conf
docker run -d --name redis-sentinel2 --network redis-cluster-net -p 26380:26379 -v "$(pwd)/sentinel2.conf:/etc/sentinel.conf" redis:latest redis-sentinel /etc/sentinel.conf
docker run -d --name redis-sentinel3 --network redis-cluster-net -p 26381:26379 -v "$(pwd)/sentinel3.conf:/etc/sentinel.conf" redis:latest redis-sentinel /etc/sentinel.conf

# sleep 10

echo "Redis with Sentinel setup is complete."

# Optional: Verify Sentinel Status
# echo "Checking Sentinel status..."
# docker exec -it redis-sentinel1 redis-cli -p 26379 SENTINEL masters
# docker exec -it redis-sentinel1 redis-cli -p 26379 SENTINEL slaves mymaster
