package com.IgniteMemoryTest.memoryTest;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;

import javax.cache.Cache;
import java.security.SecureRandom;
import java.util.List;
import java.util.Random;

public class IgniteClusterWorker extends Worker {
    private final Ignite ignite; // Client node
    private final IgniteCache<String, String> cache;

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

    private static CacheConfiguration<String, String> getCacheConfiguration() {
        String cacheName = generateRandomString(10);
        CacheConfiguration<String, String> cache_cfg = new CacheConfiguration<>(cacheName);
        cache_cfg.setCacheMode(CacheMode.PARTITIONED);
        cache_cfg.setAtomicityMode(CacheAtomicityMode.ATOMIC);
        cache_cfg.setBackups(1);
        return cache_cfg;
    }

    public static IgniteConfiguration getIgniteConfiguration(String nodeName) {
        IgniteConfiguration cfg = new IgniteConfiguration();
        cfg.setIgniteInstanceName(nodeName);

        TcpDiscoverySpi discoverySpi = new TcpDiscoverySpi();
        TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
        ipFinder.setAddresses(List.of("127.0.0.1:47500..47599"));
        discoverySpi.setIpFinder(ipFinder);
        cfg.setDiscoverySpi(discoverySpi);

        return cfg;
    }

    public IgniteClusterWorker(int numReadOps, int numWriteOps, int dataSize) {
        super(numReadOps, numWriteOps, dataSize);
        String name = generateRandomString(10);
        this.ignite = Ignition.start(getIgniteConfiguration("ClientNode" + name));
        this.cache = this.ignite.getOrCreateCache(getCacheConfiguration());
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

    @Override
    public double performWrite() {
        Runnable writeOp = () -> {
            for (int i = 0; i < 1; i++) {
                this.cache.put(getKey(), "x".repeat(dataSize));
            }
        };
        return measureLatency(writeOp);
    }

    @Override
    public double performRead() {
        Runnable readOp = () -> {
            for (int i = 0; i < 1; i++) {
                this.cache.get(getKey());
            }
        };
        return measureLatency(readOp);
    }

    public void stopIgnite() {
        this.ignite.close();
    }
}
