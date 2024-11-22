import time
import random
import numpy as np

class Worker:
    def __init__(self, num_read_oprs, num_write_oprs, data_size=100):
        self.num_read_oprs = num_read_oprs
        self.num_write_oprs = num_write_oprs
        self.data_size = data_size

    def measure_latency(self, operation_func, num_operations):
        start_time = time.time()
        operation_func()
        end_time = time.time()
        return (end_time - start_time) / num_operations  # Average latency

    def read(self):
        raise NotImplementedError("Subclass must implement read operation.")

    def write(self):
        raise NotImplementedError("Subclass must implement write operation.")
    
    def run(self, operation_type="mixed"):
        operations = []
        if operation_type == "read":
            operations = ['read'] * self.num_read_oprs
        elif operation_type == "write":
            operations = ['write'] * self.num_write_oprs
        elif operation_type == "mixed":
            operations = ['read'] * self.num_read_oprs + ['write'] * self.num_write_oprs
            random.shuffle(operations)

        latencies = []
        for op in operations:
            if op == 'read':
                latencies.append(self.perform_read())
            else:
                latencies.append(self.perform_write())
        
        latencies = np.array(latencies)
        avg_latency = np.mean(latencies)
        tail_latency = [np.percentile(latencies, p) for p in [50, 75, 80, 85, 90, 95, 99, 99.9]]
        
        return avg_latency, tail_latency
