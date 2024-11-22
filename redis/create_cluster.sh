#!/bin/bash

# Check if correct number of arguments are provided
if [[ $# -ne 2 ]]; then
    echo "Usage: $0 <number_of_masters> <number_of_replicas>"
    exit 1
fi

# Assign input arguments to variables
num_masters=$1
num_replicas=$2

# Validate the input
if [[ $num_masters -lt 3 ]]; then
    echo "Error: The number of masters should be 3 or greater."
    exit 1
fi

if [[ $((num_replicas % num_masters)) -ne 0 ]]; then
    echo "Error: The number of replicas should be 3 or greater and a multiple of the number of masters."
    exit 1
fi

# Create Docker network
docker network rm redis-cluster-net
docker network create redis-cluster-net

# Start master nodes
echo "Starting $num_masters master nodes..."
for i in $(seq 1 $num_masters); do
    port=$((6379 + i))
    docker run -d --name redis-master-$i --network redis-cluster-net redis redis-server --port $port --cluster-enabled yes --cluster-config-file nodes.conf --cluster-node-timeout 5000 --appendonly yes
    ip=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' redis-master-$i)
    eval IP_MASTER_$i=$ip
    host_list+=("$ip:$port")
done

# Start replica nodes
echo "Starting $num_replicas replica nodes..."
for j in $(seq 1 $num_replicas); do
    port=$((6379 + num_masters + j))
    docker run -d --name redis-replica-$j --network redis-cluster-net redis redis-server --port $port --cluster-enabled yes --cluster-config-file nodes.conf --cluster-node-timeout 5000 --appendonly yes
    ip=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' redis-replica-$j)
    eval IP_REPLICA_$j=$ip
    host_list+=("$ip:$port")
done

# Wait for containers to initialize
sleep 5

# Print IPs for verification
echo "Master and Replica IPs:"
for host in "${host_list[@]}"; do
    echo "$host"
done

# Create a JSON file to store host IPs and ports
json_file="redis_hosts.json"
echo "{" > $json_file
echo "  \"hosts\": [" >> $json_file

# Populate the JSON file
for host in "${host_list[@]}"; do
    echo "    \"$host\"," >> $json_file
done

# Remove the last comma and close the JSON array and object
sed -i '$ s/,$//' $json_file  # Remove the last comma
echo "  ]" >> $json_file
echo "}" >> $json_file

# Create the cluster
echo "Creating the cluster..."
cluster_create_cmd="docker exec -it redis-master-1 redis-cli --cluster create"
for i in $(seq 1 $num_masters); do
    cluster_create_cmd+=" \$IP_MASTER_$i:$((6379 + i))"
done
for j in $(seq 1 $num_replicas); do
    cluster_create_cmd+=" \$IP_REPLICA_$j:$((6379 + num_masters + j))"
done

# Add the replica distribution flag
cluster_create_cmd+=" --cluster-replicas $((num_replicas / num_masters)) --cluster-yes"

# Run the cluster creation command
eval $cluster_create_cmd

sleep 5

# Verify the cluster setup
docker exec -it redis-master-1 redis-cli -p $((6379 + 1)) cluster info
docker exec -it redis-master-1 redis-cli -p $((6379 + 1)) cluster nodes

echo "Redis cluster setup complete!"
echo "Host IPs and Ports saved to $json_file"
