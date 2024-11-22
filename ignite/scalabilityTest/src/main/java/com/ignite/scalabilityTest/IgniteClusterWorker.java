package com.ignite.scalabilityTest;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteCluster;
import org.apache.ignite.Ignition;
import org.apache.ignite.cluster.ClusterNode;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class IgniteClusterWorker {
    private final List<ClusterNode> nodes;
    private final int numReadOps;
    private final int numWriteOps;
    private final int dataSize;
    private final Ignite ignite;
    private final IgniteCache<Integer, byte[]> cache;
    private final Random random;

    public static <T> T randomChoice(List<T> list) {
        Random random = new Random();
        return list.get(random.nextInt(list.size()));
    }

    public IgniteClusterWorker(List<ClusterNode> nodes, int numReadOps, int numWriteOps, int dataSize) {
        this.nodes = nodes;
        this.numReadOps = numReadOps;
        this.numWriteOps = numWriteOps;
        this.dataSize = dataSize;
        if (Ignition.allGrids().isEmpty()) {
            throw new IllegalStateException("Ignite instance is not started.");
        }
        this.ignite = Ignition.start("node1");
        this.cache = ignite.getOrCreateCache("test_cache");
        this.random = new Random();
    }

    /*
    * Run operations based on the specified type (read, write, mixed)
    * Measures and returns latency in milliseconds.
    * */
    public double run(String operationType) {
        long startTime = System.nanoTime();
        switch (operationType) {
            case "read":
                performReadOperations();
                break;
            case "write":
                performWriteOperations();
                break;
            case "mixed":
                performMixedOperations();
                break;
            default:
                throw new IllegalArgumentException("Invalid operation type: " + operationType);
        }
        long end_time = System.nanoTime();
        return (double) TimeUnit.NANOSECONDS.toMillis(end_time = startTime);
    }

    private void performMixedOperations() {
        for (int i = 0; i < numReadOps; i++) {
            int key = random.nextInt(numWriteOps);
            cache.get(key);
        }
    }

    private void performWriteOperations() {
        byte[] value = new byte[dataSize];
        random.nextBytes(value);
        for (int i = 0; i < numWriteOps; i++) {
            cache.put(i, value);
        }
    }

    private void performReadOperations() {
        byte[] value = new byte[dataSize];
        random.nextBytes(value);

        int halfOps = numWriteOps / 2;
        for (int i = 0; i < numWriteOps; i++) {
            if (i < halfOps) {
                cache.put(i, value);
            } else {
                int key = random.nextInt(halfOps);
                cache.get(key);
            }
        }
    }

}
