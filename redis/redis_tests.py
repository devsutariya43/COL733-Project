import time
import json
import string
import random
import subprocess
import multiprocessing
import numpy as np
from rds import RedisWorker
from constant import *

class LatencyThroughputTester:
    def __init__(self, config_path):
        with open(config_path, 'r') as file:
            self.config = json.load(file)
        self.redis_hosts = None

    def redis(self):
        command = ["docker", "run", "--name", "redis", "-d", "-p", "6379:6379", "redis:latest", "--appendonly", "yes"]
        subprocess.run(command, check=True)
        time.sleep(1)
    
    def kill_redis(self):
        subprocess.run(["docker", "stop", "redis"])
        subprocess.run(["docker", "rm", "redis"])
    
    def setup_workers(self, num_workers, num_read_oprs, num_write_oprs):
        workers = []
        data_size = self.config.get("data_size", 100)
        for _ in range(num_workers):
            workers.append(
                RedisWorker(
                    self.config["host"],
                    self.config["port"],
                    num_read_oprs,
                    num_write_oprs,
                    data_size
                )
            )
        
        return workers
    
    def create_cluster(self):
        subprocess.run(['chmod', '777', CREATE_CLUSTER], check=True)
        subprocess.run([CREATE_CLUSTER, str(3), str(3)], check=True, text=True)
        
        with open(REDIS_HOSTS, 'r') as file:
            self.redis_hosts = json.load(file)
    
    def del_cluster(self):
        subprocess.run(['chmod', '777', DEL_CLUSTER], check=True)
        subprocess.run([DEL_CLUSTER, str(3), str(3)], check=True, text=True)
        
        self.redis_hosts = None
        
    def setup_cluster_workers(self, num_workers, num_read_oprs, num_write_oprs):
        startup_nodes = []
        for host in self.redis_hosts['hosts']:
            split = host.split(":")
            ip, port = str(split[0]), int(split[1])
            startup_nodes.append({"host":ip, "port":port})
        
        workers = []
        for _ in range(num_workers):
            workers.append(
                RedisClusterWorker(
                    startup_nodes,
                    num_read_oprs,
                    num_write_oprs,
                    self.config["data_size"]
                )
            )
        
        return workers
    
    def run_worker_process(self, worker, latencies, tail_latencies, opr_type="mixed"):
        latency, tail_latency = worker.run(opr_type)
        latencies.append(latency)
        tail_latencies.append(tail_latency)
    
    def run_workload_tests(self, redis=True):
        if redis:
            print('Running Workload Test')
        else:
            print('Running Workload Test for Redis Cluster')
        
        results = [["READ WORKLOAD (%)", "WRITE WORKLOAD (%)", "LATENCY (ms)", "TAIL LATENCY(90th) (ms)", "TAIL LATENCY(95th) (ms)", "TAIL LATENCY(99th) (ms)", "TAIL LATENCY(99.9th) (ms)", "THROUGHPUT (oprs/sec)"]]
        for num_reads, num_writes in zip(self.config["num_read_oprs"], self.config["num_write_oprs"]):
            if redis:
                self.redis()
                workers = self.setup_workers(2, num_reads, num_writes)
            else:
                self.create_cluster()
                workers = self.setup_cluster_workers(2, num_reads, num_writes)
            
            with multiprocessing.Manager() as manager:
                latencies = manager.list()  # Shared list for latencies
                tail_latencies = manager.list()  # Shared list for tail latencies
                processes = []

                for worker in workers:
                    p = multiprocessing.Process(target=self.run_worker_process, args=(worker, latencies, tail_latencies))
                    processes.append(p)
                    p.start()

                for p in processes:
                    p.join()

                # Calculate the average latency across all workers
                if latencies:
                    avg_latency = sum(latencies) / len(latencies)
                    avg_tail_latency = np.mean(np.array(tail_latencies), axis=0)
                    print(redis, [1000*e for e in avg_tail_latency])
                    read_workload, write_workload = num_reads/(num_reads+num_writes), num_writes/(num_reads+num_writes)
                    print(f"Average latency for {read_workload*100}% read-workload and {write_workload*100}% write-workload: {avg_latency*1000:.4f} ms")
                    print(f"Average tail latency (90th) for {read_workload*100}% read-workload and {write_workload*100}% write-workload: {avg_tail_latency[-4]*1000:.4f} ms")
                    print(f"Average tail latency (95th) for {read_workload*100}% read-workload and {write_workload*100}% write-workload: {avg_tail_latency[-3]*1000:.4f} ms")
                    print(f"Average tail latency (99th) for {read_workload*100}% read-workload and {write_workload*100}% write-workload: {avg_tail_latency[-2]*1000:.4f} ms")
                    print(f"Average tail latency (99.9th) for {read_workload*100}% read-workload and {write_workload*100}% write-workload: {avg_tail_latency[-1]*1000:.4f} ms")
                    print(f"Average throughput for {read_workload*100}% read-workload and {write_workload*100}%  write-workload: {1/(avg_latency):.2f} operations/sec")
                    results.append([read_workload*100, write_workload*100, avg_latency*1000, avg_tail_latency[-4]*1000, avg_tail_latency[-3]*1000, avg_tail_latency[-2]*1000, avg_tail_latency[-1]*1000,  1/(avg_latency)])
                else:
                    print("No latencies recorded.")
            
            if redis:
                self.kill_redis()
            else:
                self.del_cluster()
        
        # Results
        print("SUMMARY")
        col_widths = [max(len(str(item)) for item in column) for column in zip(*results)]
        for row in results:
            print(" | ".join(f"{str(item):<{col_widths[i]}}" for i, item in enumerate(row)))

        print("Workload Test Completed")

    def run_concurrency_tests(self, redis=True):
        if redis:
            print("Running Concurrency Test")
        else:
            print("Running Concurrency Test for Redis Cluster")
        
        results = [["NUM WORKERS", "OPR TYPE", "LATENCY (ms)", "TAIL LATENCY(90th) (ms)", "TAIL LATENCY(95th) (ms)", "TAIL LATENCY(99th) (ms)", "TAIL LATENCY(99.9th) (ms)", "THROUGHPUT (oprs/sec)"]]
        for num_workers in self.config["num_workers"]:
            if redis:
                self.redis()
                workers = self.setup_workers(num_workers, 5000, 5000)
            else:
                self.create_cluster()
                workers = self.setup_cluster_workers(num_workers, 5000, 5000)
            
            for test_type in ["write", "read", "mixed"]:
                with multiprocessing.Manager() as manager:
                    latencies = manager.list()  # Shared list for latencies
                    tail_latencies = manager.list()  # Shared list for tail latencies
                    processes = []

                    for worker in workers:
                        p = multiprocessing.Process(target=self.run_worker_process, args=(worker, latencies, tail_latencies, test_type))
                        processes.append(p)
                        p.start()

                    for p in processes:
                        p.join()

                    # Calculate the average latency across all workers
                    if latencies:
                        avg_latency = sum(latencies) / len(latencies)
                        avg_tail_latency = np.mean(np.array(tail_latencies), axis=0)
                        print(f"Average latency for {test_type} operations: {avg_latency*1000:.4f} ms")
                        print(f"Average tail latency (90th) for {test_type} operations: {avg_tail_latency[-4]*1000:.4f} ms")
                        print(f"Average tail latency (95th) for {test_type} operations: {avg_tail_latency[-3]*1000:.4f} ms")
                        print(f"Average tail latency (99th) for {test_type} operations: {avg_tail_latency[-2]*1000:.4f} ms")
                        print(f"Average tail latency (99.9th) for {test_type} operations: {avg_tail_latency[-1]*1000:.4f} ms")
                        print(f"Average throughput for {test_type} operations: {1/(avg_latency):.6f} operations/sec")
                        results.append([num_workers, test_type.upper(), avg_latency*1000, avg_tail_latency[-4]*1000, avg_tail_latency[-3]*1000, avg_tail_latency[-2]*1000, avg_tail_latency[-1]*1000,  1/(avg_latency)])
                    else:
                        print("No latencies recorded.")
            
            if redis:
                self.kill_redis()
            else:
                self.del_cluster()
                        
        # Results
        print("SUMMARY")
        col_widths = [max(len(str(item)) for item in column) for column in zip(*results)]
        for row in results:
            print(" | ".join(f"{str(item):<{col_widths[i]}}" for i, item in enumerate(row)))
        
        print("Concurrency Test Completed")
    
    def run(self):
        print("Running Latency and Throughput Tests ...")
        print('-'*100)
        self.run_workload_tests(redis=True)
        print('-'*100)
        self.run_workload_tests(redis=False)
        print('-'*100)
        self.run_concurrency_tests(redis=True)
        print('-'*100)
        self.run_concurrency_tests(redis=False)
        print('-'*100)
        print("\n\n")
