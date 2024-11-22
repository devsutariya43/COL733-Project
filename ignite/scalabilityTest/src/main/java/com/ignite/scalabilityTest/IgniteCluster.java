package com.ignite.scalabilityTest;

import ch.qos.logback.core.encoder.JsonEscapeUtil;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.cluster.ClusterState;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;

import java.security.SecureRandom;
import java.sql.Time;
import java.util.ArrayList;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.cluster.ClusterState;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.jetbrains.annotations.NotNull;

import java.sql.Time;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;


public class IgniteCluster {
    private final int NODE_COUNT;
    private List<Ignite> igniteCluster;
    private final IgniteCache<Integer, String> cache;
    private final Random random = new Random();
    private Ignite clientNode;

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generateRandomString(int length) {
        StringBuilder sb = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            int index = RANDOM.nextInt(CHARACTERS.length());
            sb.append(CHARACTERS.charAt(index));
        }

        return sb.toString();
    }

    private static @NotNull CacheConfiguration<Integer, String> getIntegerStringCacheConfiguration(int backups) {
        String cacheName = generateRandomString(10);
        CacheConfiguration<Integer, String> cache_cfg = new CacheConfiguration<>(cacheName);
        cache_cfg.setCacheMode(CacheMode.PARTITIONED);
        cache_cfg.setAtomicityMode(CacheAtomicityMode.ATOMIC);
        cache_cfg.setBackups(backups);
        return cache_cfg;
    }

    /**
     * Constructor that initializes the Ignite cluster with the specified number of nodes.
     *
     * @param nodeCount The number of nodes to start in Ignite Cluster.
     * */
    public IgniteCluster(int nodeCount, int backups) {
        this.NODE_COUNT = nodeCount;
        this.igniteCluster = startCluster();
//        this.cache = igniteCluster.get(0).getOrCreateCache("test_cache");
        this.cache = getCache(backups);
    }

    /*
    * Starts an Ignite Cluster with the specified number of nodes.
    *
    * @return List of Ignite instances representing the cluster nodes.
    * */
    private List<Ignite> startCluster() {
        endCluster();
        List<Ignite> nodes = new ArrayList<>();

        for (int i = 0; i < NODE_COUNT; i++) {
            IgniteConfiguration cfg = getIgniteConfiguration("node-"+i);
            Ignite ignite = Ignition.start(cfg);
            ignite.cluster().state(ClusterState.ACTIVE);
            nodes.add(ignite);
        }

        return nodes;
    }

    /*
    * Shuts down all nodes in the Ignite Cluster
    **/
    public void endCluster() {
        if (igniteCluster != null && !igniteCluster.isEmpty()) {
            for (Ignite ignite: igniteCluster) {
                ignite.close();
            }
            igniteCluster.clear();
            clientNode.close();
            System.out.println("Cluster has been shut down");
        } else {
            System.out.println("Cluster is already stopped or was not initialized.");
        }
    }

    /*
    * Creates and retrieves an Ignite Cache instance.
    * @return IgniteCache instance to interact with the Cache.
    * */
    public IgniteCache<Integer, String> getCache(int backups) {
//        if (igniteCluster.isEmpty()) {
//            throw new IllegalStateException("Cluster is not initialized.");
//        }
//        Ignite ignite = igniteCluster.get(0);
//        return ignite.getOrCreateCache("my_cache");
        CacheConfiguration<Integer, String> cache_cfg = getIntegerStringCacheConfiguration(backups);
        IgniteConfiguration cfg = getIgniteConfiguration("ClientNode");
        clientNode = Ignition.start(cfg);
        return clientNode.getOrCreateCache(cache_cfg);
    }

    /*
    * Returns the configuration for a specific node in the Ignite Cluster
    *
    * @param i Index of the node in the cluster.
    * @return IgniteConfiguration for the specified node
    * */
    private IgniteConfiguration getIgniteConfiguration(String nodeName) {
        IgniteConfiguration cfg = new IgniteConfiguration();
        cfg.setIgniteInstanceName(nodeName);

        // Configure data storage
        DataStorageConfiguration dsc = new DataStorageConfiguration();
        cfg.setDataStorageConfiguration(dsc);

        // Configure discovery for node discovery within cluster
        TcpDiscoverySpi discoverySpi = new TcpDiscoverySpi();
        TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
        ipFinder.setAddresses(List.of("127.0.0.1:47500..47599"));
        discoverySpi.setIpFinder(ipFinder);
        cfg.setDiscoverySpi(discoverySpi);

//        // Configure cache settings:
//        CacheConfiguration<Integer, String> cache_cfg = new CacheConfiguration<>("my_cache");
//        cache_cfg.setCacheMode(CacheMode.PARTITIONED);
//        cache_cfg.setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL);
//        cfg.setCacheConfiguration(cache_cfg);

        return cfg;
    }

    // Perform read operations and return latency in milliseconds
    public double performReadOperation(int numReadOps) {
        long startTime = System.nanoTime();
        for (int i = 0; i < numReadOps; i++) {
            int key = random.nextInt(numReadOps);
            cache.get(key);
        }
        long endTime = System.nanoTime();
        return (double) TimeUnit.NANOSECONDS.toMillis(endTime - startTime) / numReadOps;
    }

    // Perform write operations and return latency in milliseconds
    public double performWriteOperations(int numWriteOps) {
        long startTime = System.nanoTime();
        for (int i = 0; i < numWriteOps; i++) {
            String value = "Data-" + i;
            cache.put(i, value);
        }
        long endTime = System.nanoTime();
        return (double) TimeUnit.NANOSECONDS.toMillis(endTime - startTime) / numWriteOps;
    }

    // Perform mixed operations and returns latency in milliseconds
    public double performMixedOperation(int numReadOps, int numWriteOps) {
        long startTime = System.nanoTime();
        int halfOps = numWriteOps / 2;

        for (int i = 0; i < numWriteOps; i++) {
            if (i < halfOps) {
                String value = "Data-" + i;
                cache.put(i, value);
            } else {
                int key = random.nextInt(halfOps);
                cache.get(key);
            }
        }
        long endTime = System.nanoTime();
        return (double) TimeUnit.NANOSECONDS.toMillis(endTime - startTime) / (numReadOps);
    }

    public static void main(String[] args) {
        int numberOfNodes = 3;
        int numBackupNodes = 0;
        IgniteCluster clusterInit = new IgniteCluster(numberOfNodes, 0);

        // Define number of operations for each type
        int numReadOps = 1000;
        int numWriteOps = 1000;

        System.out.println("Write Operation latency: " + clusterInit.performWriteOperations(numWriteOps) + " ms");
        System.out.println("Read Operation latency: " + clusterInit.performReadOperation(numReadOps) + " ms");
        System.out.println("Mixed Operation latency: " + clusterInit.performMixedOperation(numWriteOps, numReadOps) + " ms");

        // Shutdown
        clusterInit.endCluster();
    }
}
