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
