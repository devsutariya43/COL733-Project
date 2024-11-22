import time
from redis.sentinel import Sentinel
import redis
import multiprocessing
import subprocess
import numpy as np
import random
from constant import *

class SentinelTest:
    def __init__(self, sentinel_hosts):
        self.sentinel = Sentinel(sentinel_hosts, socket_timeout=0.1)
        
    def create_sentinel(self, nodes):
        subprocess.run([CREATE_SENTINEL, str(nodes-1)], check=True)
        time.sleep(2)
    
    def del_sentinel(self):
        subprocess.run([DEL_SENTINEL], check=True)
    
    # Function to handle setting values with failover support
    def perform_writes(self, num_writes, latencies):
        for i in range(num_writes):
            while True:
                try:
                    # Try to connect to the current master
                    master = self.sentinel.master_for(SERVICE_NAME, socket_timeout=0.1)
                    # Attempt to set a value
                    start = time.time()
                    master.set(f"key_{i}", f"value_{i}")
                    end = time.time()
                    latencies.append((end-start))
                    # print(f"Value '{value}' set for key '{key}' on master.")
                    break
                except Exception as e:
                    print(f"Error setting value: {e}. Retrying in 1 second...")
                    # Wait briefly to allow Sentinel to promote a new master
                    time.sleep(1)

    # Function to get value from any replica
    def perform_reads(self, num_reads, latencies):
        for i in range(num_reads):
            while True:
                try:
                    slaves = self.sentinel.discover_slaves(SERVICE_NAME)
                    # slave = self.sentinel.slave_for(SERVICE_NAME, socket_timeout=0.1)
                    host, port = random.choice(slaves)
                    slave = redis.StrictRedis(host=host, port=port)
                    start = time.time()
                    value = slave.get(f"key_{i}")
                    end = time.time()
                    latencies.append((end-start))
                    # if value:
                    #     print(f"Value retrieved from replica: {value.decode('utf-8')}")
                    # else:
                    #     print("Key not found.")
                    break
                except Exception as e:
                    print(f"Error reading from replica: {e}")
                    time.sleep(1)

    def fail_master(self):
        subprocess.run(["docker", "stop", "redis-master"], check=True)
        time.sleep(2)

    def fail_replicas(self, node):
        subprocess.run(["docker", "stop", f"redis-replica{node}"], check=True)
        time.sleep(2)
    
    def run_scalability_test(self):
        print('Running Scalabality Test for Redis Sentinel')
        
        for num_readers, num_writers in zip([16, 32], [16, 0]):
            print(f'NUM READERS : {num_readers} | NUM WRITERS : {num_writers}')
            
            results = [["NUM NODES", "LATENCY (ms)", "TAIL LATENCY (ms)", "THROUGHPUT (oprs/sec)"]] 
            num_nodes = [3, 6, 9, 12, 15, 18, 21, 24, 27]
            for nodes in num_nodes:
                self.create_sentinel(nodes)
                
                with multiprocessing.Manager() as manager:
                    latencies = manager.list()  # Shared list for latencies
                    
                    readers = []
                    writers = []
                    for _ in range(num_readers):
                        readers.append(multiprocessing.Process(target=self.perform_reads, args=(500,latencies,)))
                    for _ in range(num_writers):
                        writers.append(multiprocessing.Process(target=self.perform_writes, args=(500,latencies,)))

                    for reader in readers:
                        reader.start()
                    for writer in writers:
                        writer.start()
                    
                    for reader in readers:
                        reader.join()
                    for writer in writers:
                        writer.join()
                    
                    avg_latency = np.mean(latencies)*1000
                    tail_latency = np.percentile(latencies, 90)*1000
                    print(f"Avg Latencies {avg_latency:.4f} ms")
                    print(f"Tail Latencies {tail_latency:.4f} ms")
                    results.append([nodes, avg_latency, tail_latency, 2000/np.sum(latencies)])
            
                self.del_sentinel()
            
            # Results
            print("SUMMARY")
            col_widths = [max(len(str(item)) for item in column) for column in zip(*results)]
            for row in results:
                print(" | ".join(f"{str(item):<{col_widths[i]}}" for i, item in enumerate(row)))
        
        print('Scalabality Test for Redis Sentinel Completed')
    
    def run_availability_test(self):
        print('Running Multiple Failure Test')
        
        for num_readers, num_writers in zip([16, 32], [16, 0]):
            print(f'NUM READERS : {num_readers} | NUM WRITERS : {num_writers}')
            results = [["NUM NODES", "LATENCY (ms)", "TAIL LATENCY (ms)", "THROUGHPUT (oprs/sec)"]] 
            num_nodes = [3, 6, 9, 12, 15, 18, 21, 24, 27]
            for nodes in num_nodes:
                self.create_sentinel(nodes)
                
                with multiprocessing.Manager() as manager:
                    latencies = manager.list()  # Shared list for latencies
                    
                    readers = []
                    writers = []
                    for _ in range(num_readers):
                        readers.append(multiprocessing.Process(target=self.perform_reads, args=(500,latencies,)))
                    for _ in range(num_writers):
                        writers.append(multiprocessing.Process(target=self.perform_writes, args=(500,latencies,)))

                    for reader in readers:
                        reader.start()
                    for writer in writers:
                        writer.start()
                    
                    failures = (nodes-1)//2
                    failures_threads = []
                    failures_threads.append(multiprocessing.Process(target=self.fail_master))
                    for i in range(failures-1):
                        failures_threads.append(multiprocessing.Process(target=self.fail_replicas, args=(i+1    ,)))
                    for failure in failures_threads:
                        failure.start()
                    
                    for reader in readers:
                        reader.join()
                    for writer in writers:
                        writer.join()
                    for failure in failures_threads:
                        failure.join()
                    
                    avg_latency = np.mean(latencies)*1000
                    tail_latency = np.percentile(latencies, 90)*1000
                    print(f"Avg Latencies {avg_latency:.4f} ms")
                    print(f"Tail Latencies {tail_latency:.4f} ms")
                    results.append([nodes, avg_latency, tail_latency, 2000/np.sum(latencies)])
            
                self.del_sentinel()
            
            # Results
            print("SUMMARY")
            col_widths = [max(len(str(item)) for item in column) for column in zip(*results)]
            for row in results:
                print(" | ".join(f"{str(item):<{col_widths[i]}}" for i, item in enumerate(row)))
            
        print('Multiple Failure Test Completed')
    
    def run(self):
        print('Running Sentinel Tests ...')
        print('-'*100)
        try:
            self.run_scalability_test()
        except Exception as e:
            print(f"An Exception Occured During Test\n Details: {e}")
        print('-'*100)
        try:
            self.run_availability_test()
        except Exception as e:
            print(f"An Exception Occured During Test\n Details: {e}")
        print('-'*100)
        print('\n\n')

if __name__ == "__main__":
    tester = SentinelTest(SENTINEL_HOSTS)
    tester.run()
