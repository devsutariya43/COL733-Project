import time
import json
import string
import random
import subprocess
import multiprocessing
import numpy as np
from rds import RedisWorker, RedisClusterWorker, RedisSentinelWorker
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
        try:
            self.run_workload_tests(redis=True)
        except Exception as e:
            print(f"An Exception Occured During Test\n Details: {e}")
        print('-'*100)
        try:
            self.run_workload_tests(redis=False)
        except Exception as e:
            print(f"An Exception Occured During Test\n Details: {e}")
        print('-'*100)
        try:
            self.run_concurrency_tests(redis=True)
        except Exception as e:
            print(f"An Exception Occured During Test\n Details: {e}")
        print('-'*100)
        try:
            self.run_concurrency_tests(redis=False)
        except Exception as e:
            print(f"An Exception Occured During Test\n Details: {e}")
        print('-'*100)
        print("\n\n")


class ScalabilityTester:
    def __init__(self, config_path):
        with open(config_path, 'r') as file:
            self.config = json.load(file)
        self.redis_hosts = None
    
    def create_cluster(self, num_masters, num_replicas):
        subprocess.run(['chmod', '777', CREATE_CLUSTER], check=True)
        subprocess.run([CREATE_CLUSTER, str(num_masters), str(num_replicas)], check=True, text=True)
        
        with open(REDIS_HOSTS, 'r') as file:
            self.redis_hosts = json.load(file)
    
    def del_cluster(self, num_master, num_replicas):
        subprocess.run(['chmod', '777', DEL_CLUSTER], check=True)
        subprocess.run([DEL_CLUSTER, str(num_master), str(num_replicas)], check=True, text=True)
        
        self.redis_hosts = None
        
    def setup_workers(self, num_workers):
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
                    self.config["num_read_oprs"],
                    self.config["num_write_oprs"],
                    self.config["data_size"]
                )
            )
        
        return workers
    
    def run_worker_process(self, worker, latencies, tail_latencies, operation_type="mixed"):
        latency, tail_latency = worker.run(operation_type)
        latencies.append(latency)
        tail_latencies.append(tail_latency)
        
    def run_replica_scalabity_test(self):
        print('Running Variable REPLICAS Cluster Test')
        results = [["NUM REPLICAS", "OPR TYPE", "LATENCY (ms)", "TAIL LATENCY (ms)", "THROUGHPUT (oprs/sec)"]]
        for num_replicas in self.config["num_replicas"]:
            self.create_cluster(3, num_replicas)
            
            workers = self.setup_workers(self.config["num_workers"])
            for test_type in ["write", "read", "mixed"]:
                # print(f"Running {test_type.upper()} operations test for REDIS workers:")

                with multiprocessing.Manager() as manager:
                    latencies = manager.list()  # Shared list for latencies
                    tail_latencies = manager.list()  # Shared list for latencies
                    processes = []

                    for worker in workers:
                        p = multiprocessing.Process(target=self.run_worker_process, args=(worker, latencies,tail_latencies, test_type))
                        processes.append(p)
                        p.start()

                    for p in processes:
                        p.join()

                    # Calculate the average latency across all workers
                    if latencies:
                        avg_latency = sum(latencies) / len(latencies)
                        avg_tail_latency = np.mean(np.array(tail_latencies), axis=0)[-4]
                        print(f"Average latency for {test_type} operations with cluster size {num_replicas}: {avg_latency*1000:.4f} ms")
                        print(f"Average tail latency for {test_type} operations with cluster size {num_replicas}: {avg_tail_latency*1000:.4f} ms")
                        print(f"Average throughput for {test_type} operations with cluster size {num_replicas}: {1/(avg_latency):.4f} operations/sec")
                        results.append([num_replicas, test_type, avg_latency*1000, avg_tail_latency*1000, 1/(avg_latency)])
                    else:
                        print("No latencies recorded.")
            
            self.del_cluster(3, num_replicas)
        
        # Results
        print("SUMMARY")
        col_widths = [max(len(str(item)) for item in column) for column in zip(*results)]
        for row in results:
            print(" | ".join(f"{str(item):<{col_widths[i]}}" for i, item in enumerate(row)))
        
        print('Variable Node Cluster Test Completed')
    
    def run_master_scalabity_test(self):
        print('Running Variable MASTERS Cluster Test')
        results = [["NUM MASTERS", "OPR TYPE", "LATENCY (ms)", "TAIL LATENCY (ms)", "THROUGHPUT (oprs/sec)"]]
        for num_masters in self.config["num_masters"]:
            self.create_cluster(num_masters, 0)
            
            workers = self.setup_workers(self.config["num_workers"])
            for test_type in ["write", "read", "mixed"]:
                # print(f"Running {test_type.upper()} operations test for REDIS workers:")

                with multiprocessing.Manager() as manager:
                    latencies = manager.list()  # Shared list for latencies
                    tail_latencies = manager.list()  # Shared list for latencies
                    processes = []

                    for worker in workers:
                        p = multiprocessing.Process(target=self.run_worker_process, args=(worker, latencies,tail_latencies, test_type))
                        processes.append(p)
                        p.start()

                    for p in processes:
                        p.join()

                    # Calculate the average latency across all workers
                    if latencies:
                        avg_latency = sum(latencies) / len(latencies)
                        avg_tail_latency = np.mean(np.array(tail_latencies), axis=0)[-4]
                        print(f"Average latency for {test_type} operations with cluster size {num_masters}: {avg_latency*1000:.4f} ms")
                        print(f"Average tail latency for {test_type} operations with cluster size {num_masters}: {avg_tail_latency*1000:.4f} ms")
                        print(f"Average throughput for {test_type} operations with cluster size {num_masters}: {1/(avg_latency):.4f} operations/sec")
                        results.append([num_masters, test_type, avg_latency*1000, avg_tail_latency*1000, 1/(avg_latency)])
                    else:
                        print("No latencies recorded.")
            
            self.del_cluster(num_masters, 0)
        
        # Results
        print("SUMMARY")
        col_widths = [max(len(str(item)) for item in column) for column in zip(*results)]
        for row in results:
            print(" | ".join(f"{str(item):<{col_widths[i]}}" for i, item in enumerate(row)))
        
        print('Variable Node Cluster Test Completed')
        
    def run(self):
        print("Running Scalability Tests ...")
        print('-'*100)
        try:
            self.run_master_scalabity_test()
        except Exception as e:
            print(f"An Exception Occured During Test\n Details: {e}")
        print('-'*100)
        try:
            self.run_replica_scalabity_test()
        except Exception as e:
            print(f"An Exception Occured During Test\n Details: {e}")
        print('-'*100)
        print("\n\n")


class CAPTester:
    def __init__(self, config_path):
        with open(config_path, 'r') as file:
            self.config = json.load(file)
        self.startup_nodes = []
    
    def create_cluster(self, num_masters, num_replicas):
        subprocess.run(['chmod', '777', CREATE_CLUSTER], check=True)
        subprocess.run([CREATE_CLUSTER, str(num_masters), str(num_replicas)], check=True, text=True)
        time.sleep(5)
        
        with open(REDIS_HOSTS, 'r') as file:
            redis_hosts = json.load(file)
        
        for host in redis_hosts['hosts']:
            split = host.split(":")
            ip, port = str(split[0]), int(split[1])
            self.startup_nodes.append({"host":ip, "port":port})
    
    def del_cluster(self, num_masters, num_replicas):
        subprocess.run(['chmod', '777', DEL_CLUSTER], check=True)
        subprocess.run([DEL_CLUSTER, str(num_masters), str(num_replicas)], check=True, text=True)
        
        self.startup_nodes = []
    
    def setup_workers(self, num_workers):
        workers = []
        for _ in range(num_workers):
            workers.append(
                RedisClusterWorker(
                    self.startup_nodes,
                    self.config["num_read_oprs"],
                    self.config["num_write_oprs"],
                    self.config["data_size"]
                )
            )
        
        return workers
    
    def run_worker_process(self, worker, latencies, tail_latencies, operation_type="mixed"):
        latency, tail_latency = worker.run(operation_type)
        latencies.append(latency)
        tail_latencies.append(tail_latency)
    
    def stop_node(self, container_id):
        subprocess.run(["docker", "stop", container_id])
    
    def start_node(self, container_id):
        subprocess.run(["docker", "start", container_id])
    
    def get_containerid(self, master_node):
        cmd = "docker inspect --format '{{.Id}} {{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' $(docker ps -q)"
        result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
        containers = {r.split(" ")[1]:r.split(" ")[0] for r in result.stdout.split("\n")[:-1]}
        
        return containers.get(self.startup_nodes[master_node]["host"], None)
    
    def simulate_failure(self, num_failures, downtime):
        containers = [self.get_containerid(master_node) for master_node in range(num_failures)]
        for container_id in containers:
            if container_id:
                self.stop_node(container_id)
                
        time.sleep(downtime)
        
        for container_id in containers:
            if container_id:
                self.start_node(container_id)
    
    def run_availibility_test(self):
        print('Running Availibility Test')
        
        results = [["NUM MASTERS", "NUM REPLICAS", "NUM FAILURES", "LATENCY (ms)", "TAIL LATENCY (ms)", "THROUGHPUT (oprs/sec)"]]
        for num_masters, num_replicas in zip(self.config["num_masters"], self.config["num_replicas"]):
            self.create_cluster(num_masters, num_replicas)
            
            workers = self.setup_workers(self.config["num_workers"])
            # print(f"Running {test_type.upper()} operations test for REDIS workers:")

            with multiprocessing.Manager() as manager:
                latencies = manager.list()  # Shared list for latencies
                tail_latencies = manager.list()  # Shared tail list for latencies
                processes = []

                for worker in workers:
                    p = multiprocessing.Process(target=self.run_worker_process, args=(worker, latencies, tail_latencies))
                    processes.append(p)
                    p.start()

                num_failures = random.randint(1, int((num_masters-1)/2))
                failure_process = multiprocessing.Process(target=self.simulate_failure, args=(num_failures, self.config["downtime"]))
                failure_process.start()
    
                for p in processes:
                    p.join()

                failure_process.join()
                
                # Calculate the average latency across all workers
                if latencies:
                    avg_latency = sum(latencies) / len(latencies)
                    avg_tail_latency = np.mean(np.array(tail_latencies), axis=0)[-4]
                    print(f"Average latency with {num_masters} MASTERS, {num_replicas} REPLICAS, {num_failures} FAILURES: {avg_latency*1000:.4f} ms")
                    print(f"Average tail latency with {num_masters} MASTERS, {num_replicas} REPLICAS, {num_failures} FAILURES: {avg_tail_latency*1000:.4f} ms")
                    print(f"Average throughput with {num_masters} MASTERS, {num_replicas} REPLICAS, {num_failures} FAILURES: {1/(avg_latency):.4f} operations/sec")
                    results.append([num_masters, num_replicas, num_failures, avg_latency*1000, avg_tail_latency*1000, 1/(avg_latency)])
                else:
                    print("No latencies recorded.")
            
            self.del_cluster(num_masters, num_replicas)
        
        # Results
        print("SUMMARY")
        col_widths = [max(len(str(item)) for item in column) for column in zip(*results)]
        for row in results:
            print(" | ".join(f"{str(item):<{col_widths[i]}}" for i, item in enumerate(row)))
        
        print("Availibility Test Completed")
    
    def run_consistency_test(self):
        print('Running Consistency Test')
        
        for num_masters, num_replicas in zip(self.config["num_masters"], self.config["num_replicas"]):
            results = [["NUM MASTERS", "NUM REPLICAS", "PRE FAILURE", "POST FAILURE", "POST RECOVERY"]]
            self.create_cluster(num_masters, num_masters)
            
            worker = self.setup_workers(1)[0]
            letters = list(string.ascii_uppercase)
            for letter in letters:
                worker.write(letter, ord(letter)-65)
            
            containers = []
            for i in range(int((num_masters-1)/2)):
                container_id = self.get_containerid(i)
                self.stop_node(container_id)
                containers.append(container_id)
            print(f"Simulated Failure of {int((num_masters-1)/2)} master. NOTE THAT MORE THAN THIS FAILURES LEADS TO TOTAL UNAVAILIBILITY")
            
            retrived = {}
            for letter in letters:
                try:
                    val = worker.read(letter)
                except:
                    val = -1
                retrived[ord(letter)-65] = int(val)
            
            for container_id in containers:
                self.start_node(container_id)
            time.sleep(5)
            
            for letter in letters:
                try:
                    val = worker.read(letter)
                except:
                    val = -1
                
                results.append([num_masters, num_replicas, f'{letter} : {ord(letter)-65}', f'{letter} : {retrived[ord(letter)-65]}', f'{letter} : {val}'])
            
            self.del_cluster(num_masters, num_replicas)
            
            # Results
            col_widths = [max(len(str(item)) for item in column) for column in zip(*results)]
            for row in results:
                print(" | ".join(f"{str(item):<{col_widths[i]}}" for i, item in enumerate(row)))
            
            correct = 0
            for i in range(len(retrived)):
                if retrived[i] == i:
                    correct += 1
            print(f'Accuracy: ', (correct*100)/len(retrived), "%")

        print('Consistency Test Completed')
    
    def run(self):
        print("Running Consistency and Availibity Tests ...")
        print('-'*100)
        try:
            self.run_availibility_test()
        except Exception as e:
            print(f"An Exception Occured During Test\n Details: {e}")
        print('-'*100)
        try:
            self.run_consistency_test()
        except Exception as e:
            print(f"An Exception Occured During Test\n Details: {e}")
        print('-'*100)
        print("\n\n")


class MemUsageTester:
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
    
    def setup_workers(self, num_workers, data_size):
        workers = []
        for _ in range(num_workers):
            workers.append(
                RedisWorker(
                    self.config["host"],
                    self.config["port"],
                    self.config["num_read_oprs"],
                    self.config["num_write_oprs"],
                    data_size*1024
                )
            )
        
        return workers

    def create_cluster(self, num_masters, num_replicas):
        subprocess.run(['chmod', '777', CREATE_CLUSTER], check=True)
        subprocess.run([CREATE_CLUSTER, str(num_masters), str(num_replicas)], check=True, text=True)
        
        with open(REDIS_HOSTS, 'r') as file:
            self.redis_hosts = json.load(file)
    
    def del_cluster(self, num_masters, num_replicas):
        subprocess.run(['chmod', '777', DEL_CLUSTER], check=True)
        subprocess.run([DEL_CLUSTER, str(num_masters), str(num_replicas)], check=True, text=True)
        
        self.redis_hosts = None
        
    def setup_cluster_workers(self, num_workers, data_size):
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
                    self.config["num_read_oprs"],
                    self.config["num_write_oprs"],
                    data_size*1024
                )
            )
        
        return workers
    
    def run_worker_process(self, worker, latencies, tail_latencies, opr_type="mixed"):
        latency, tail_latency = worker.run(opr_type)
        latencies.append(latency)
        tail_latencies.append(tail_latency)
    
    def run_memusage_test(self):
        print('Running Memory Usage Test for Redis')
        
        results = [["DATA SIZE", "LATENCY (ms)", "TAIL LATENCY (ms)", "THROUGHPUT (oprs/sec)"]]
        for data_size in self.config["data_size"]:
            self.redis()
            
            workers = self.setup_workers(self.config["num_workers"], data_size)
            
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
                    avg_tail_latency = np.mean(np.array(tail_latencies), axis=0)[-4]
                    print(f"Average latency with data size {data_size}KB: {avg_latency*1000:.4f} ms")
                    print(f"Average tail latency with data size {data_size}KB: {avg_tail_latency*1000:.4f} ms")
                    print(f"Average throughput with data size {data_size}KB: {1/(avg_latency):.6f} operations/sec")
                    results.append([data_size, avg_latency*1000, avg_tail_latency*1000, 1/(avg_latency)])
            
            self.kill_redis()
                
        # Results
        print("SUMMARY")
        col_widths = [max(len(str(item)) for item in column) for column in zip(*results)]
        for row in results:
            print(" | ".join(f"{str(item):<{col_widths[i]}}" for i, item in enumerate(row)))
        
        print('Memory Usage Test for Redis Completed')
    
    def run_memusage_cluster_test(self):
        print('Running Memory Usage Test on Redis Cluster')
        
        results = [["DATA SIZE", "LATENCY (ms)", "TAIL LATENCY (ms)", "THROUGHPUT (oprs/sec)"]]
        for data_size in self.config["data_size"]:
            self.create_cluster(3, 0)
            
            workers = self.setup_cluster_workers(self.config["num_workers"], data_size)
            
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
                    avg_tail_latency = np.mean(np.array(tail_latencies), axis=0)[-4]
                    print(f"Average latency with data size {data_size}KB: {avg_latency*1000:.4f} ms")
                    print(f"Average tail latency with data size {data_size}KB: {avg_tail_latency*1000:.4f} ms")
                    print(f"Average throughput with data size {data_size}KB: {1/(avg_latency):.6f} operations/sec")
                    results.append([data_size, avg_latency*1000, avg_tail_latency*1000, 1/(avg_latency)])
            
            self.del_cluster(3, 0)
        
        # Results
        print("SUMMARY")
        col_widths = [max(len(str(item)) for item in column) for column in zip(*results)]
        for row in results:
            print(" | ".join(f"{str(item):<{col_widths[i]}}" for i, item in enumerate(row)))
        
        print('Memory Usage Test for Redis Cluster Completed')
            
    def run(self):
        print("Running Memory Usage Tests ...")
        print("-"*100)
        try:
            self.run_memusage_test()
        except Exception as e:
            print(f"An Exception Occured During Test\n Details: {e}")
        print("-"*100)
        try:
            self.run_memusage_cluster_test()
        except Exception as e:
            print(f"An Exception Occured During Test\n Details: {e}")
        print("-"*100)
        print("\n\n")


class RedisSentinelTests:
    def __init__(self, config_path):
        with open(config_path, 'r') as file:
            self.config = json.load(file)
        
    def create_sentinel(self, nodes):
        subprocess.run(['chmod', '777', CREATE_SENTINEL], check=True)
        subprocess.run([CREATE_SENTINEL, str(nodes-1)], check=True, text=True)
        
    def del_sentinel(self):
        subprocess.run(['chmod', '777', DEL_SENTINEL], check=True)
        subprocess.run([DEL_SENTINEL], check=True, text=True)
    
    def setup_sentinel_worker(self, num_workers, num_read_oprs, num_write_oprs):
        workers = []
        for _ in range(num_workers):
            workers.append(
                RedisSentinelWorker(
                    SENTINEL_HOSTS,
                    num_read_oprs,
                    num_write_oprs,
                    self.config["data_size"]
                )
            )
        
        return workers
    
    def run_worker_process(self, worker, latencies, tail_latencies, operation_type="mixed"):
        latency, tail_latency = worker.run(operation_type)
        latencies.append(latency)
        tail_latencies.append(tail_latency)
        
    def run_workload_tests(self):
        print('Running Workload Test for Redis Sentinel')
        
        results = [["READ WORKLOAD (%)", "WRITE WORKLOAD (%)", "LATENCY (ms)", "TAIL LATENCY (ms)", "THROUGHPUT (oprs/sec)"]]
        for num_reads, num_writes in zip(self.config["num_read_oprs"], self.config["num_write_oprs"]):
            self.create_sentinel(3)
            workers = self.setup_sentinel_worker(2, num_reads, num_writes)
            
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
                    avg_tail_latency = np.mean(np.array(tail_latencies), axis=0)[-4]
                    read_workload, write_workload = num_reads/(num_reads+num_writes), num_writes/(num_reads+num_writes)
                    print(f"Average latency for {read_workload*100}% read-workload and {write_workload*100}% write-workload: {avg_latency*1000:.4f} ms")
                    print(f"Average tail latency for {read_workload*100}% read-workload and {write_workload*100}% write-workload: {avg_tail_latency*1000:.4f} ms")
                    print(f"Average throughput for {read_workload*100}% read-workload and {write_workload*100}%  write-workload: {1/(avg_latency):.2f} operations/sec")
                    results.append([read_workload*100, write_workload*100, avg_latency*1000, avg_tail_latency*1000, 1/(avg_latency)])
                else:
                    print("No latencies recorded.")
            
            self.del_sentinel()
        
        # Results
        print("SUMMARY")
        col_widths = [max(len(str(item)) for item in column) for column in zip(*results)]
        for row in results:
            print(" | ".join(f"{str(item):<{col_widths[i]}}" for i, item in enumerate(row)))

        print("Workload Test Completed")

    def run_concurrency_tests(self):
        print("Running Concurrency Test for Redis Sentinel")
        
        results = [["NUM WORKERS", "OPR TYPE", "LATENCY (ms)", "TAIL LATENCY (ms)", "THROUGHPUT (oprs/sec)"]]
        for num_workers in self.config["num_workers"]:
            self.create_sentinel(3)
            workers = self.setup_sentinel_worker(num_workers, 5000, 5000)
            
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
                        avg_tail_latency = np.mean(np.array(tail_latencies), axis=0)[-4]
                        print(f"Average latency for {test_type} operations: {avg_latency*1000:.4f} ms")
                        print(f"Average tail latency for {test_type} operations: {avg_tail_latency*1000:.4f} ms")
                        print(f"Average throughput for {test_type} operations: {1/(avg_latency):.6f} operations/sec")
                        results.append([num_workers, test_type.upper(), avg_latency*1000, avg_tail_latency*1000, 1/(avg_latency)])
                    else:
                        print("No latencies recorded.")
            
            self.del_sentinel()
                        
        # Results
        print("SUMMARY")
        col_widths = [max(len(str(item)) for item in column) for column in zip(*results)]
        for row in results:
            print(" | ".join(f"{str(item):<{col_widths[i]}}" for i, item in enumerate(row)))
        
        print("Concurrency Test Completed")
    
    def run_scalabity_test(self):
        print('Running Variable Node Cluster Test')
        results = [["NUM NODES", "OPR TYPE", "LATENCY (ms)", "TAIL LATENCY (ms)", "THROUGHPUT (oprs/sec)"]]
        for num_nodes in self.config["nodes"]:
            self.create_sentinel(num_nodes)
            
            workers = self.setup_sentinel_worker(1, 500, 500)
            for test_type in ["write", "read", "mixed"]:
                # print(f"Running {test_type.upper()} operations test for REDIS workers:")

                with multiprocessing.Manager() as manager:
                    latencies = manager.list()  # Shared list for latencies
                    tail_latencies = manager.list()  # Shared list for latencies
                    processes = []

                    for worker in workers:
                        p = multiprocessing.Process(target=self.run_worker_process, args=(worker, latencies,tail_latencies, test_type))
                        processes.append(p)
                        p.start()

                    for p in processes:
                        p.join()

                    # Calculate the average latency across all workers
                    if latencies:
                        avg_latency = sum(latencies) / len(latencies)
                        avg_tail_latency = np.mean(np.array(tail_latencies), axis=0)[-4]
                        print(f"Average latency for {test_type} operations with cluster size {num_nodes}: {avg_latency*1000:.4f} ms")
                        print(f"Average tail latency for {test_type} operations with cluster size {num_nodes}: {avg_tail_latency*1000:.4f} ms")
                        print(f"Average throughput for {test_type} operations with cluster size {num_nodes}: {1/(avg_latency):.4f} operations/sec")
                        results.append([num_nodes, test_type, avg_latency*1000, avg_tail_latency*1000, 1/(avg_latency)])
                    else:
                        print("No latencies recorded.")
            
            self.del_sentinel()
        
        # Results
        print("SUMMARY")
        col_widths = [max(len(str(item)) for item in column) for column in zip(*results)]
        for row in results:
            print(" | ".join(f"{str(item):<{col_widths[i]}}" for i, item in enumerate(row)))
        
        print('Variable Node Cluster Test Completed')
    
    def run(self):
        print("Running Latency and Throughput Tests ...")
        print('-'*100)
        try:
            self.run_workload_tests()
        except Exception as e:
            print(f"An Exception Occured During Test\n Details: {e}")
        print('-'*100)
        try:
            self.run_concurrency_tests()
        except Exception as e:
            print(f"An Exception Occured During Test\n Details: {e}")
        print('-'*100)
        try:
            self.run_scalabity_test()
        except Exception as e:
            print(f"An Exception Occured During Test\n Details: {e}")
        print('-'*100)
        print("\n\n")

if __name__ == "__main__":
    # Run latency & throughput tests
    tester1 = LatencyThroughputTester("redis_configs/config1.json")
    tester1.run()
    
    # Run scalability tests
    tester2 = ScalabilityTester("redis_configs/config2.json")
    tester2.run()
    
    # Run cap tests
    tester3 = CAPTester("redis_configs/config3.json")
    tester3.run()
    
    # Run memory usage and persistance tests
    tester4 = MemUsageTester("redis_configs/config4.json")
    tester4.run()
    
    # Run redis sentinel tests
    # tester5 = RedisSentinelTests("redis_configs/config5.json")
    # tester5.run()
    
