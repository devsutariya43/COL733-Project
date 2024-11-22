package com.ignite.CAPTester.CAPTester;

import java.nio.DoubleBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public abstract class Worker {
    public int numReadOps;
    public int numWriteOps;
    public int dataSize;

    public Worker(int numReadOps, int numWriteOps, int dataSize) {
        this.numReadOps = numReadOps;
        this.numWriteOps = numWriteOps;
        this.dataSize = dataSize;
    }

    // Abstract methods for read and write operations
    public abstract double performRead();
    public abstract double performWrite();

    // Measures the latency for a single operation
    public double measureLatency(Runnable operation) {
        double startTime = System.currentTimeMillis();
        operation.run();
        double endTime = System.currentTimeMillis();
//        System.out.println(endTime - startTime);
        return (endTime - startTime);
    }

    // Runs the specified operations type and returns average and 90th percentile latencies
    public double[] run(String operationType) {
        List<String> operations = new ArrayList<>();

        // Determine operation types based on configuration
        switch (operationType) {
            case "read" -> {
                for (int i = 0; i < numReadOps; i++) {
                    operations.add("read");
                }
            }
            case "write" -> {
                for (int i = 0; i < numWriteOps; i++) {
                    operations.add("write");
                }
            }
            case "mixed" -> {
                for (int i = 0; i < numReadOps; i++) {
                    operations.add("read");
                }
                for (int i = 0; i < numWriteOps; i++) {
                    operations.add("write");
                }
            }
        }

        // Track latencies of each operation
        List<Double> latencies = new ArrayList<>();
        for (String op : operations) {
            if (op.equals("read")) {
                latencies.add(performRead());
            } else {
                latencies.add(performWrite());
            }
        }

        // Calculate average and 90th percentile latencies
        Double avgLatency = (double) latencies.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double tailTimes = latencies.stream().mapToDouble(Double::doubleValue).sum();
        latencies.sort(Double::compareTo);
        double tailLatency = latencies.get((int) (latencies.size() * 0.9) - 1); // 90th percentile
        System.out.println(avgLatency);
        System.out.println(tailLatency);
        double throughput = latencies.size() / tailTimes;
        return new double[] {avgLatency, tailLatency, throughput};
    }
}

