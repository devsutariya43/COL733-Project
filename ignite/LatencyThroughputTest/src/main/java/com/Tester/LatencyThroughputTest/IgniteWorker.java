package com.Tester.LatencyThroughputTest;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cluster.ClusterState;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.jetbrains.annotations.NotNull;

//import javax.cache.Cache;
import java.security.SecureRandom;
import java.util.List;
import java.util.Random;

public class IgniteWorker extends Worker{
//    private final Ignite ignite;
    private final Random random = new Random();
    private final IgniteCache<String, String> cache;


    public IgniteWorker(IgniteCache<String, String> cache, int numReadOps, int numWriteOps, int dataSize) {
        super(numReadOps, numWriteOps, dataSize);
        this.cache = cache;

        // Configure and start Ignite
//        String name = generateRandomString(10);
//        this.ignite = Ignition.start(getIgniteConfiguration("ClientNode" + name));
//        this.ignite.cluster().state(ClusterState.ACTIVE);
//        this.cache = this.ignite.getOrCreateCache(getCacheConfiguration("Cache" + name));
    }

    // Helper method to generate random key
    private String getKey() {
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        return String.valueOf(alphabet.charAt(new Random().nextInt(alphabet.length())));
    }

    public void write(String key, String val) {
        this.cache.put(key,val);
    }

    public String read(String key) {
        return this.cache.get(key);
    }

    public void clearCache() {
        this.cache.clear();
    }

//    public void stopIgnite() {
//        this.ignite.close();
//    }

    // Perform a read operation
    @Override
    public double performRead() {
        Runnable readOp = () -> {
            for (int i = 0; i < 1; i++) {
                this.cache.get(getKey());
            }
        };
        return measureLatency(readOp);
    }

    // Perform a write operation
    @Override
    public double performWrite() {
        Runnable writeOp = () -> {
            for (int i = 0; i < 1; i++) {
                this.cache.put(getKey(), "x".repeat(dataSize));
            }
        };
        return measureLatency(writeOp);

    }
}
