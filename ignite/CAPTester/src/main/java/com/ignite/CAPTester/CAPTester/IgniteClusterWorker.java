package com.ignite.CAPTester.CAPTester;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteCluster;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.lang.IgniteRunnable;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.jetbrains.annotations.NotNull;

import java.security.SecureRandom;
import java.util.List;
import java.util.Random;

public class IgniteClusterWorker extends Worker {
    private final Ignite ignite;
    private final IgniteCache<String, String> cache;
//    private final int numReadOps;
//    private final int numWriteOps;
//    private final int dataSize;
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

    private static @NotNull CacheConfiguration<String, String> getStringCacheConfiguration(int numBackups) {
        String cacheName = generateRandomString(10);
        CacheConfiguration<String, String> cache_cfg = new CacheConfiguration<>(cacheName);
        cache_cfg.setCacheMode(CacheMode.REPLICATED);
        cache_cfg.setAtomicityMode(CacheAtomicityMode.ATOMIC);
        cache_cfg.setBackups(numBackups);
        return cache_cfg;
    }

    public static IgniteConfiguration getIgniteConfiguration(String nodeName) {
        IgniteConfiguration cfg = new IgniteConfiguration();
        cfg.setIgniteInstanceName(nodeName);
        cfg.setClientMode(true);

        TcpDiscoverySpi discoverySpi = new TcpDiscoverySpi();
        TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
        ipFinder.setAddresses(List.of("127.0.0.1:47500..47599"));
        discoverySpi.setIpFinder(ipFinder);
        cfg.setDiscoverySpi(discoverySpi);

        return cfg;
    }

    public IgniteClusterWorker(int numReadOps, int numWriteOps, int dataSize, int numBackups) {
        super(numReadOps, numWriteOps, dataSize);
        String name = generateRandomString(10);
        this.ignite = Ignition.start(getIgniteConfiguration("ClientNode" + name));
        this.cache = this.ignite.getOrCreateCache(getStringCacheConfiguration(numBackups));
    }

    private String getKey() {
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        return String.valueOf(alphabet.charAt(new Random().nextInt(alphabet.length())));
    }

    public void write(String key, String val) {
        this.cache.put(key, val);
    }

    public String read(String key) {
        return this.cache.get(key);
    }

    public void stopIgnite() {
        this.ignite.close();
    }

    @Override
    public double performRead() {
        double start = System.currentTimeMillis();
        this.cache.get(getKey());
        double end = System.currentTimeMillis();
//        System.out.println("Time taken to read: " + (end - start) + " ms");
        return end - start;
    }

    @Override
    public double performWrite() {
        double start = System.currentTimeMillis();
        this.cache.put(getKey(), "x".repeat(dataSize));
        double end = System.currentTimeMillis();
//        System.out.println("Time taken to write: " + (end - start) + " ms");
        return end - start;
    }

    public void simulateFailure(int downtime) {
        IgniteCluster cluster = ignite.cluster();
        ClusterNode nodeToFail = cluster.forRemotes().node(); // Select a remote node to stop
        if (nodeToFail != null) {
            ignite.compute(cluster.forNode(nodeToFail)).run((IgniteRunnable) () -> {
                try {
                    System.out.println("Simulating node failure...");
                    ignite.close();
                    Thread.sleep(downtime * 1000L); // Downtime in milliseconds
                    Ignition.start(); // Restart Ignite node
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("Node failure simulation interrupted.");
                }
            });
        } else {
            System.out.println("No remote nodes found for failure simulation.");
        }
    }
}
