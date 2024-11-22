import redis
import rediscluster
from worker import Worker
import random
import string
import subprocess
import time
from redis.sentinel import Sentinel
from crc16 import generate_random_key
from constant import SERVICE_NAME

class RedisWorker(Worker):
    def __init__(self, host, port, num_read_oprs, num_write_oprs, data_size=100):
        super().__init__(num_read_oprs, num_write_oprs, data_size)
        self.host = host
        self.port = port
        self.client = redis.StrictRedis(host=host, port=port, decode_responses=True)

    def read(self, key):
        return self.client.get(key)
    
    def write(self, key, val):
        return self.client.set(key, val)
    
    def perform_read(self):
        def read_op():
            for i in range(1):
                self.client.get(f"test_key_{i}")
        return self.measure_latency(read_op, 1)

    def perform_write(self):
        def write_op():
            for i in range(1):
                self.client.set(f"test_key_{i}", "x" * self.data_size)
        return self.measure_latency(write_op, 1)

    def get_containerid(self):
        cmd = "docker inspect --format '{{.Id}} {{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' $(docker ps -q)"
        result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
        containers = {r.split(" ")[1]:r.split(" ")[0] for r in result.stdout.split("\n")[:-1]}
        
        return containers.get(self.host, None)
    
    def simulate_failure(self, downtime):
        container_id = self.get_containerid()
        if container_id:
            subprocess.run(["docker", "stop", container_id])
            time.sleep(downtime)
            subprocess.run(["docker", "start", container_id])
        
class RedisClusterWorker(Worker):
    def __init__(self, startup_nodes, num_read_oprs, num_write_oprs, data_size=100):
        super().__init__(num_read_oprs, num_write_oprs, data_size)
        self.startup_nodes = startup_nodes
        self.client = rediscluster.RedisCluster(startup_nodes=startup_nodes, decode_responses=True, read_from_replicas=True, socket_timeout=5, socket_connect_timeout=5)
    
    def get_key(self):
        # letters = list(string.ascii_letters)
        # key = random.choice(letters)
        key, _ = generate_random_key()
        return key
    
    def read(self, key):
        return self.client.get(key)
    
    def write(self, key, val):
        return self.client.set(key, val)
    
    def perform_read(self):
        key = self.get_key()
        def read_op():
            for _ in range(1):
                self.client.get(key)
        return self.measure_latency(read_op, 1)

    def perform_write(self):
        key = self.get_key()
        def write_op():
            for _ in range(1):
                self.client.set(key, "x" * self.data_size)
        return self.measure_latency(write_op, 1)
    
    def get_containerid(self, master_node):
        cmd = "docker inspect --format '{{.Id}} {{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' $(docker ps -q)"
        result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
        containers = {r.split(" ")[1]:r.split(" ")[0] for r in result.stdout.split("\n")[:-1]}
        
        return containers.get(self.startup_nodes[master_node]["host"], None)
    
    def simulate_failure(self, master_node, downtime):
        contaider_id = self.get_containerid(master_node)
        if contaider_id:
            subprocess.run(["docker", "stop", contaider_id])
            time.sleep(downtime)
            subprocess.run(["docker", "start", contaider_id])
    
class RedisSentinelWorker(Worker):
    def __init__(self, sentinel_hosts, num_read_oprs, num_write_oprs, data_size=100):
        super().__init__(num_read_oprs, num_write_oprs, data_size)
        self.sentinel_hosts = sentinel_hosts
        self.sentinel = Sentinel(sentinel_hosts, socket_timeout=0.1)
    
    def get_key(self):
        # letters = list(string.ascii_letters)
        # key = random.choice(letters)
        key, _ = generate_random_key()
        return key
    
    def read(self, key):
        slave = self.sentinel.slave_for(SERVICE_NAME, socket_timeout=0.1)
        return slave.get(key)
    
    def write(self, key, val):
        master = self.sentinel.master_for(SERVICE_NAME, socket_timeout=0.1)
        return master.set(key, val)
    
    def perform_read(self):
        key = self.get_key()
        def read_op():
            for _ in range(1):
                while True:
                    try:
                        slave = self.sentinel.slave_for(SERVICE_NAME, socket_timeout=0.1)
                        slave.get(key)
                        break
                    except Exception as e:
                        print(e)
                        continue
        return self.measure_latency(read_op, 1)

    def perform_write(self):
        key = self.get_key()
        def write_op():
            for _ in range(1):
                while True:
                    try:
                        master = self.sentinel.master_for(SERVICE_NAME, socket_timeout=0.1)
                        master.set(key, "x" * self.data_size)
                        break
                    except Exception as e:
                        print(e)
                        continue
        return self.measure_latency(write_op, 1)

