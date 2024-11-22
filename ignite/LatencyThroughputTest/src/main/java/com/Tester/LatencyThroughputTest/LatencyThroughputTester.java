package com.Tester.LatencyThroughputTest;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cluster.ClusterState;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.*;

public class LatencyThroughputTester {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(LatencyThroughputTester.class);
    private final String configPath;
//    private Ignite ignite;
    private List<Ignite> igniteCluster;
    private Ignite clientNode;

    private static final Logger logger = Logger.getLogger(LatencyThroughputTester.class.getName());

    static {
        // Configure the logger to write to a file
        try {
            FileHandler fileHandler = new FileHandler("latency_throughput_log.log");
            fileHandler.setFormatter(new SimpleFormatter() {
                @Override
                public synchronized String format(LogRecord record) {
                    return String.format("%1$tF %1$tT %2$s %n", record.getMillis(), record.getMessage());
                }
            });
            logger.addHandler(fileHandler);
            logger.setLevel(Level.INFO);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public LatencyThroughputTester(String configPath) {
        this.configPath = configPath;
//        logger.info("Initialized LatencyThroughputTester with config path: " + configPath);
    }

    public static List<Ignite> igniteCluster(int numNodes) {
        List<Ignite> nodes = new ArrayList<>();

        for (int i = 0; i < numNodes; i++) {
            addNewNode("ignite-node-" + i, nodes);
        }
        nodes.get(0).cluster().state(ClusterState.ACTIVE);
        return nodes;
    }

    private static void addNewNode(String nodeName, List<Ignite> nodes) {
        IgniteConfiguration cfg = getIgniteConfiguration(nodeName);
        Ignite ignite = Ignition.start(cfg);
        nodes.add(ignite);
    }

    private static IgniteConfiguration getIgniteConfiguration(String NodeName) {
        IgniteConfiguration cfg = new IgniteConfiguration();
        cfg.setIgniteInstanceName(NodeName);

//		DataStorageConfiguration dsc = new DataStorageConfiguration();
//		dsc.getDefaultDataRegionConfiguration().setPersistenceEnabled(true);
//		cfg.setDataStorageConfiguration(dsc);


        TcpDiscoverySpi discoverySpi = new TcpDiscoverySpi();
        TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
        ipFinder.setAddresses(List.of("127.0.0.1:47500..47509"));
        discoverySpi.setIpFinder(ipFinder);
        cfg.setDiscoverySpi(discoverySpi);

//		CacheConfiguration<Integer, String> cache_cfg = new CacheConfiguration<>("replicatedCache");
//		cache_cfg.setCacheMode(CacheMode.PARTITIONED);
//		cache_cfg.setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL);
//
//		cache_cfg.setBackups(2);
//		cfg.setCacheConfiguration(cache_cfg);

        return cfg;
    }

    public static void stopNode(String nodeName, List<Ignite> nodes) {
        Ignite nodeToStop = nodes.stream()
                .filter(node -> node.name().equals(nodeName))
                .findFirst()
                .orElse(null);

        if (nodeToStop != null) {
            nodeToStop.close();
            nodes.remove(nodeToStop);
            System.out.println("Stopped node: " + nodeName);

            // Add a delay between 2 to 5 seconds
            try {
                int waitTime = ThreadLocalRandom.current().nextInt(2000, 5000);
                System.out.println("Waiting for " + waitTime + " milliseconds before proceeding...");
                Thread.sleep(waitTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();  // Restore interrupted status
                System.out.println("Wait interrupted after stopping node: " + nodeName);
            }
        } else {
            System.out.println("Node not found: " + nodeName);
        }
    }

    public static void stopCluster(List<Ignite> nodes) {
        System.out.println("Stopping entire cluster...");

        // Stop each Ignite node in the cluster
        for (Ignite node : nodes) {
            System.out.println("Stopping node: " + node.name());
            node.close();
        }

        nodes.clear();
        System.out.println("Cluster stopped.");

        // Delay to allow for full shutdown
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    public void setupIgnite(int numNodes) {
//        logger.info("Setting up Ignite node...");
        for (int i = 0; i < numNodes; i++) {
            System.out.println("Setting up Cluster node: " + i);
            IgniteConfiguration cfg = getIgniteConfiguration(i);
            Ignite ignite = Ignition.start(cfg);
            ignite.cluster().state(ClusterState.ACTIVE);
            this.igniteCluster.add(ignite);
        }

//        logger.info("Ignite node started successfully.");
    }

    private static @NotNull IgniteConfiguration getIgniteConfiguration(int i) {
        IgniteConfiguration cfg = new IgniteConfiguration();
        cfg.setIgniteInstanceName("Node" + i);

        DataStorageConfiguration dsc = new DataStorageConfiguration();
        dsc.getDefaultDataRegionConfiguration().setPersistenceEnabled(true);
        cfg.setDataStorageConfiguration(dsc);

        TcpDiscoverySpi discoverySpi = new TcpDiscoverySpi();
        TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
        ipFinder.setAddresses(List.of("127.0.0.1:47500..47509"));
        discoverySpi.setIpFinder(ipFinder);
        cfg.setDiscoverySpi(discoverySpi);

//        CacheConfiguration<Integer, String> cache_cfg = new CacheConfiguration<>("my_cache");
//        cache_cfg.setCacheMode(CacheMode.PARTITIONED);
//        cache_cfg.setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL);
//        cache_cfg.setBackups(1);
//        cfg.setCacheConfiguration(cache_cfg);

        cfg.setClientMode(false);
        return cfg;
    }

    public void killIgnite() {
//        if (this.ignite != null) {
//            this.ignite.close();
////            logger.info("Ignite node stopped successfully.");
//        }

        for (Ignite ignite: this.igniteCluster) {
            ignite.close();
        }
    }

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

    private static @NotNull CacheConfiguration<String, String> getCacheConfiguration(String cacheName) {
        CacheConfiguration<String, String> cache_cfg = new CacheConfiguration<>(cacheName);
        cache_cfg.setCacheMode(CacheMode.PARTITIONED);
        cache_cfg.setAtomicityMode(CacheAtomicityMode.ATOMIC);
        cache_cfg.setBackups(1);
        return cache_cfg;
    }

    public static IgniteConfiguration getIgniteClientConfiguration(String nodeName) {
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

    public List<IgniteWorker> setupWorkers(int numWorkers, int numReadOps, int numWriteOps, int dataSize) {
//        logger.info("Setting up " + numWorkers + " workers with " + numReadOps + " read operations and " + numWriteOps + " write operations.");
//        this.clientNode = Ignition.start(getIgniteClientConfiguration("ClientNode"));
        Ignite node = this.igniteCluster.get(0);
        IgniteCache<String, String> cache = node.getOrCreateCache(getCacheConfiguration(generateRandomString(10)));
        List<IgniteWorker> workers = new ArrayList<>();
        for (int i = 0; i < numWorkers; i++) {
            IgniteWorker worker = new IgniteWorker(cache, numReadOps, numWriteOps, dataSize);
            workers.add(worker);
        }
        return workers;
    }

//    public List<IgniteWorker> setupWorkers(int numWorkers, int numReadOps, int numWriteOps, int dataSize) {
////        logger.info("Setting up " + numWorkers + " workers with " + numReadOps + " read operations and " + numWriteOps + " write operations.");
//        this.clientNode = Ignition.start(getIgniteClientConfiguration("ClientNode"));
//        IgniteCache<String, String> cache = clientNode.getOrCreateCache(getCacheConfiguration(generateRandomString(10)));
//        List<IgniteWorker> workers = new ArrayList<>();
//        for (int i = 0; i < numWorkers; i++) {
//            IgniteWorker worker = new IgniteWorker(cache, numReadOps, numWriteOps, dataSize);
//            workers.add(worker);
//        }
//        return workers;
//    }

    public void stopClient() {
        this.clientNode.close();
        this.clientNode = null;
    }

    public static void runWorkerProcess(IgniteWorker worker, List<Double> latencies, List<Double> Latencies50, List<Double> Latencies75, List<Double> Latencies80, List<Double> Latencies85, List<Double> Latencies90, List<Double> Latencies95, List<Double> Latencies99, List<Double> Latencies995,List<Double> throughputs, String oprType) {
        double[] latencys = worker.run(oprType);
        latencies.add(latencys[0]);
        Latencies50.add(latencys[1]);
        Latencies75.add(latencys[2]);
        Latencies80.add(latencys[3]);
        Latencies85.add(latencys[4]);
        Latencies90.add(latencys[5]);
        Latencies95.add(latencys[6]);
        Latencies99.add(latencys[7]);
        Latencies995.add(latencys[8]);
        throughputs.add(latencys[9]);
    }

    public void runWorkloadTests() {
        System.out.println("Running Workload Test for Ignite Cluster");
//        logger.info("Starting workload test for Ignite cluster.");


        List<String[]> results = new ArrayList<>();
        results.add(new String[]{"READ WORKLOAD (%)", "WRITE WORKLOAD (%)", "LATENCY (μs)", "Percentile50 (μs)", "Percentile75 (μs)", "Percentile80 (μs)","Percentile85 (μs)", "Percentile90 (μs)", "Percentile95 (μs)", "Percentile99 (μs)", "Percentile995 (μs)", "THROUGHPUT (ops/s)"});

        this.igniteCluster = igniteCluster(3);

        List<Integer> readOps = List.of(0, 1000, 2500, 5000, 7500, 10000, 20000, 25000, 30000, 40000, 50000, 60000, 70000, 75000, 80000, 90000, 92500, 95000, 97500, 99000, 100000);
        List<Integer> writeOps = List.of(100000, 99000, 97500, 95000, 92500, 90000, 80000, 75000, 70000, 60000, 50000, 40000, 30000, 25000, 20000, 10000, 7500, 5000, 2500, 1000, 0);

            for (int i = 0; i < readOps.size(); i++) {
                try {
                    int numReadOps = readOps.get(i);
                    int numWriteOps = writeOps.get(i);
//                    logger.info("Starting test with " + numReadOps + " read ops and " + numWriteOps + " write ops.");
                    System.out.println("Starting test with " + numReadOps + " read ops and " + numWriteOps + " write ops.");
                    List<IgniteWorker> workers = setupWorkers(2, numReadOps, numWriteOps, 100);
                    ExecutorService executor = Executors.newFixedThreadPool(workers.size());
                    List<Future<?>> futures = new ArrayList<>();
                    List<Double> latencies = new ArrayList<>();
//                    List<Double> tailLatencies = new ArrayList<>();
                    List<Double> throughputs = new ArrayList<>();
                    List<Double> Latencies50 = new ArrayList<>();
                    List<Double> Latencies75 = new ArrayList<>();
                    List<Double> Latencies80 = new ArrayList<>();
                    List<Double> Latencies85 = new ArrayList<>();
                    List<Double> Latencies90 = new ArrayList<>();
                    List<Double> Latencies95 = new ArrayList<>();
                    List<Double> Latencies99 = new ArrayList<>();
                    List<Double> Latencies995 = new ArrayList<>();

                    for (IgniteWorker worker : workers) {
                        futures.add(executor.submit(() -> runWorkerProcess(worker, latencies, Latencies50, Latencies75, Latencies80, Latencies85, Latencies90, Latencies95, Latencies99, Latencies995, throughputs, "mixed")));
                    }

                    for (Future<?> future : futures) {
                        future.get();
                    }

                    double avgLatencyMs = latencies.stream().mapToDouble(Double::doubleValue).sum() / latencies.size();
                    double latency50 = Latencies50.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                    double latency75 = Latencies75.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                    double latency80 = Latencies80.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                    double latency85 = Latencies85.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                    double latency90 = Latencies90.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                    double latency95 = Latencies95.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                    double latency99 = Latencies99.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                    double latency995 = Latencies995.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
//                    double throughput = (numReadOps + numWriteOps) / avgLatencyMs;
                    double throughput = throughputs.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

                    double readWorkloadPercentage = numReadOps / (double) (numReadOps + numWriteOps) * 100;
                    double writeWorkloadPercentage = numWriteOps / (double) (numReadOps + numWriteOps) * 100;

//                    logger.info(String.format("Average latency for %.0f%% read-workload and %.0f%% write-workload: %.4f ms", readWorkloadPercentage, writeWorkloadPercentage, avgLatencyMs));
//                    logger.info(String.format("Average tail latency for %.0f%% read-workload and %.0f%% write-workload: %.4f ms", readWorkloadPercentage, writeWorkloadPercentage, avgTailLatencyMs));
//                    logger.info(String.format("Average throughput for %.0f%% read-workload and %.0f%% write-workload: %.2f operations/ms", readWorkloadPercentage, writeWorkloadPercentage, throughput));

                    results.add(new String[]{String.valueOf(readWorkloadPercentage), String.valueOf(writeWorkloadPercentage), String.valueOf(avgLatencyMs), String.valueOf(latency50), String.valueOf(latency75), String.valueOf(latency80), String.valueOf(latency85), String.valueOf(latency90), String.valueOf(latency95), String.valueOf(latency99), String.valueOf(latency995), String.valueOf(throughput)});

                    for (IgniteWorker worker : workers) {
                        worker.clearCache();
                    }
//                    stopClient();

                } catch (Exception e) {
                    logger.severe("Error during workload test: " + e.getMessage());
                    e.printStackTrace();
                }
            }


        stopCluster(this.igniteCluster);

//        logger.info("Workload test completed.");
        System.out.println("SUMMARY");
        results.forEach(row -> System.out.println(String.join(" | ", row)));
        logResults(results);
    }

    public void runConcurrencyTests() {
        System.out.println("Running Concurrency Test for Ignite Cluster");
//        logger.info("Starting concurrency test for Ignite cluster.");

        List<String[]> results = new ArrayList<>();
        results.add(new String[]{"NUM WORKERS", "OPR TYPE", "LATENCY (μs)", "Percentile50 (μs)", "Percentile75 (μs)", "Percentile80 (μs)","Percentile85 (μs)", "Percentile90 (μs)", "Percentile95 (μs)", "Percentile99 (μs)", "Percentile995 (μs)", "THROUGHPUT (ops/s)"});

//        setupIgnite(3);
        this.igniteCluster = igniteCluster(3);
        for (int numWorkers : List.of(1, 2, 4, 6, 8, 12, 16, 24, 32, 48, 64)) {
            List<IgniteWorker> workers = setupWorkers(numWorkers, 500, 500, 100);

            for (String testType : List.of("write", "read", "mixed")) {
                try {
//                    logger.info("Testing with " + numWorkers + " workers for " + testType + " operations.");
                    System.out.println("Testing with " + numWorkers + " workers for " + testType + " operations.");
                    ExecutorService executor = Executors.newFixedThreadPool(workers.size());
                    List<Future<?>> futures = new ArrayList<>();
                    List<Double> latencies = new ArrayList<>();
                    List<Double> tailLatencies = new ArrayList<>();
                    List<Double> throughputs = new ArrayList<>();
                    List<Double> Latencies50 = new ArrayList<>();
                    List<Double> Latencies75 = new ArrayList<>();
                    List<Double> Latencies80 = new ArrayList<>();
                    List<Double> Latencies85 = new ArrayList<>();
                    List<Double> Latencies90 = new ArrayList<>();
                    List<Double> Latencies95 = new ArrayList<>();
                    List<Double> Latencies99 = new ArrayList<>();
                    List<Double> Latencies995 = new ArrayList<>();

                    for (IgniteWorker worker : workers) {
                        futures.add(executor.submit(() -> runWorkerProcess(worker, latencies, Latencies50, Latencies75, Latencies80, Latencies85, Latencies90, Latencies95, Latencies99, Latencies995, throughputs, testType)));
                    }

                    for (Future<?> future : futures) {
                        future.get();
                    }

                    double avgLatencyMs = latencies.stream().mapToDouble(Double::doubleValue).sum() / latencies.size();
//                    double avgTailLatencyMs = tailLatencies.stream().mapToDouble(Double::doubleValue).sum() / tailLatencies.size();
                    double latency50 = Latencies50.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                    double latency75 = Latencies75.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                    double latency80 = Latencies80.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                    double latency85 = Latencies85.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                    double latency90 = Latencies90.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                    double latency95 = Latencies95.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                    double latency99 = Latencies99.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                    double latency995 = Latencies995.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                    int opsCount = testType.equals("mixed") ? 10000 : 5000;
//                    double throughput = opsCount / avgLatencyMs;
                    double throughput = throughputs.stream().mapToDouble(Double::doubleValue).average().orElse(0.0)*1000000;
//                    logger.info(String.format("Average Latency for %s: %.4f ms | Tail Latency: %.4f ms | Throughput: %.2f ops/ms", testType, avgLatencyMs, avgTailLatencyMs, throughput));

                    System.out.println(String.format("Average Latency for %s: %.4f ms | Tail Latency: %.4f ms | Throughput: %.2f ops/s", testType, avgLatencyMs, latency90, throughput));

//                    results.add(new String[]{
//                            String.valueOf(numWorkers),
//                            testType.toUpperCase(),
//                            String.valueOf(avgLatencyMs),
//                            String.valueOf(avgTailLatencyMs),
//                            String.valueOf(throughput)
//                    });
                    results.add(new String[]{String.valueOf(numWorkers),
                            testType.toUpperCase(),
                            String.valueOf(avgLatencyMs),
                            String.valueOf(latency50),
                            String.valueOf(latency75),
                            String.valueOf(latency80),
                            String.valueOf(latency85),
                            String.valueOf(latency90),
                            String.valueOf(latency95),
                            String.valueOf(latency99),
                            String.valueOf(latency995),
                            String.valueOf(throughput)});


                } catch (Exception e) {
                    logger.severe("Error during concurrency test: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            workers.get(0).clearCache();
//            stopClient();
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
//            for (IgniteWorker worker: workers) {
//                worker.stopIgnite();
//            }
        }
//        killIgnite();

        stopCluster(this.igniteCluster);
//        logger.info("Concurrency test completed.");
        System.out.println("SUMMARY");
        results.forEach(row -> System.out.println(String.join(" | ", row)));
        logResults(results);
    }

    private void logResults(List<String[]> results) {
        for (String[] row : results) {
            logger.info(String.join(" | ", row));
        }
        logger.info("\n");
    }
}
