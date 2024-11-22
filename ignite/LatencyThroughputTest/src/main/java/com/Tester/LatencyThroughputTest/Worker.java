package com.Tester.LatencyThroughputTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
        double startTime = System.nanoTime();
        operation.run();
        double endTime = System.nanoTime();
        return (endTime - startTime) / 1000;
    }

    public static double calculatePercentile(List<Double> data, double p) {
        if (data == null || data.isEmpty() || p < 0 || p > 100) {
            throw new IllegalArgumentException("Invalid data or percentile");
        }

        // Sort the data
        Collections.sort(data);

        // Find the rank (0-based index)
        int rank = (int) ((p) * (data.size() - 1));

        return data.get(rank);
//        // Interpolate between the two nearest ranks if rank is not an integer
//        int lowerIndex = (int) Math.floor(rank);
//        int upperIndex = (int) Math.ceil(rank);
//
//        if (lowerIndex == upperIndex) {
//            return data.get(lowerIndex); // Exact match
//        } else {
//            // Linear interpolation
//            double lowerValue = data.get(lowerIndex);
//            double upperValue = data.get(upperIndex);
//            return lowerValue + (rank - lowerIndex) * (upperValue - lowerValue);
//        }
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

//        System.out.print("[");
//        for (int i = 0; i < latencies.size(); i++) {
//            System.out.print(latencies.get(i));
//            if (i < latencies.size() - 1) {
//                System.out.print(", ");
//            }
//        }
//        System.out.println("]");

        // Calculate average and 90th percentile latencies
        Double avgLatency = (double) latencies.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double totaltime = latencies.stream().mapToDouble(Double::doubleValue).sum();
//        double tailLatency = latencies.get((int) (latencies.size() * 0.9) - 1);
        double percentile_90 = calculatePercentile(latencies, 0.9);
        double percentile_50 = calculatePercentile(latencies, 0.5);
        double percentile_75 = calculatePercentile(latencies, 0.75);
        double percentile_80 = calculatePercentile(latencies, 0.80);
        double percentile_85 = calculatePercentile(latencies, 0.85);
        double percentile_95 = calculatePercentile(latencies, 0.95);
        double percentile_99 = calculatePercentile(latencies, 0.99);
        double percentile_995 = calculatePercentile(latencies, 0.995);
        System.out.println(avgLatency);
        System.out.println("percentile" + percentile_90);
        double throughput = latencies.size() / totaltime;
        System.out.println(throughput);
        return new double[] {avgLatency, percentile_50, percentile_75, percentile_80, percentile_85, percentile_90, percentile_95, percentile_99, percentile_995, throughput};
    }
}
