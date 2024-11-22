package com.ignite.CAPTester.CAPTester;

import org.apache.ignite.*;
import org.apache.ignite.cluster.ClusterState;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import java.util.stream.Collectors;

public class CapTesterApplication {
	private static final org.slf4j.Logger log = LoggerFactory.getLogger(CapTesterApplication.class);
	private static int NODE_COUNT = 3;

	private static final Logger logger = Logger.getLogger(CapTesterApplication.class.getName());

	static {
		// Configure the logger to write to a file
		try {
			FileHandler fileHandler = new FileHandler("CAP_test_log.log", true);
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

	public static void main(String[] args) {
		runAvailabilityTest();
		runConsistencyTest();
		System.exit(0);
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

	public static Ignite randomChoice(List<Ignite> list) {
		Random random = new Random();
		return list.get(random.nextInt(list.size()));
	}

	public static List<IgniteClusterWorker> setup_workers(int num_workers, List<Ignite> nodes, int numBackups) {
		List<IgniteClusterWorker> workers = new ArrayList<>();
		for (int i = 0; i < num_workers; i++) {
			workers.add(new IgniteClusterWorker(500, 500, 100, numBackups));
		}
		return workers;
	}

	public static void runWorkerProcess(IgniteClusterWorker worker, List<Double> latencies, List<Double> tailLatencies, List<Double> throughputs, String oprType) {
		double[] latencys = worker.run(oprType);
		latencies.add(latencys[0]);
		tailLatencies.add(latencys[1]);
		throughputs.add(latencys[2]);
	}

	public static void simulateFailure(int numFailures, List<Ignite> nodes) {
		List<String> nodesToStop = new ArrayList<>();

		// Shutdown the first numFailure nodes
		for (int i = 0; i < numFailures; i++) {
			nodesToStop.add("ignite-node-" + i);
		}

		try {
			for (String nodeName: nodesToStop) {
				stopNode(nodeName, nodes);
				addNewNode(nodeName, nodes);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void runAvailabilityTest() {
		System.out.println("Running Availability Test");
		logger.info("Starting Availability Test");

		List<List<Object>> results = new ArrayList<>();
		results.add(List.of("NUM SERVERS", "NUM BACKUPS", "NUM FAILURES", "LATENCY (ms)", "TAIL LATENCY (ms)", "THROUGHPUT (oprs/ms)"));

		for (int i : List.of(3, 6, 9)) {
			Random random = new Random();
			List<Integer> k;
	 		if (i == 3 || i == 9) {
				k = List.of(1, 2);
			} else {
				k = List.of(1, 2, 4);
			}
            for (int j: k) {
                int numFailures = random.nextInt(i - 1) + 1;
                try {
                    System.out.println("Testing with " + i + " servers.");
                    logger.info("Testing with " + i + " servers.");
                    List<Ignite> nodes = igniteCluster(i);

                    List<IgniteClusterWorker> workers = setup_workers(4, nodes, j);

                    ExecutorService executor = Executors.newFixedThreadPool(workers.size() + 1);
                    List<Future<?>> futures = new ArrayList<>();

                    List<Double> latencies = new ArrayList<>();
                    List<Double> tailLatencies = new ArrayList<>();
                    List<Double> throughputs = new ArrayList<>();

                    for (IgniteClusterWorker worker : workers) {
                        futures.add(executor.submit(() -> runWorkerProcess(worker, latencies, tailLatencies, throughputs, "mixed")));
                    }

                    futures.add(executor.submit(() -> simulateFailure(numFailures, nodes)));

                    for (Future<?> future : futures) {
                        future.get();
                    }

                    if (!latencies.isEmpty()) {
                        double avgLatency = latencies.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                        double avgTailLatency = tailLatencies.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                        double avgOperations = 10000;  // Example average operations per worker

                        double throughput = throughputs.stream().mapToDouble(Double::doubleValue).average().orElse(0.0)*1000;

                        String result = String.format(
                                "NUM SERVERS: %d, NUM BACKUPS: %d, NUM FAILURES: %d, LATENCY: %.8f ms, TAIL LATENCY: %.8f ms, THROUGHPUT: %.8f oprs/ms",
                                i, j, numFailures, avgLatency, avgTailLatency, throughput
                        );

                        System.out.println(result);
                        logger.info(result);


                        results.add(List.of(i, j, numFailures, avgLatency, avgTailLatency, throughput));
                    }

                    for (IgniteClusterWorker worker : workers) {
                        worker.stopIgnite();
                    }

                    stopCluster(nodes);
                } catch (Exception e) {
                    e.printStackTrace();
                    logger.warning("Error during availability test with " + i + " servers: " + e.getMessage());
                }
            }

        }

		logger.info("Availability Test Results:");
		results.forEach(row -> logger.info(row.stream().map(String::valueOf).collect(Collectors.joining(", "))));
	}

	public static void runConsistencyTest() {
		System.out.println("Running Consistency Test");
		logger.info("Starting Consistency Test");

		List<Integer> numNodes = List.of(3, 6, 9);

		for (int i = 0; i < numNodes.size(); i++) {
			int nodes = numNodes.get(i);
			List<Ignite> cluster = igniteCluster(nodes);
			System.out.printf("Testing with %d SERVERS%n", nodes);
			logger.info("Testing with " + nodes + " SERVERS");
			int numBackups = 0;

			IgniteClusterWorker worker = new IgniteClusterWorker(500, 500, 100, numBackups);
			List<Character> letters = new ArrayList<>();
			for (char letter = 'A'; letter <= 'Z'; letter++) {
				letters.add(letter);
				worker.write(String.valueOf(letter), String.valueOf(letter - 'A'));
			}

			List<String> stoppedNodes = new ArrayList<>();
			for (int j = 0; j < (nodes-1)/2; j++) {
				String nodeName = "ignite-node-" + i;
				stopNode(nodeName, cluster);
				stoppedNodes.add(nodeName);
			}
			System.out.println("Simulated failure of " + stoppedNodes.size() + " server nodes.");
			logger.info("Simulated failure of " + stoppedNodes.size() + " server nodes.");

			List<String> results = new ArrayList<>();
			int correct = 0;
			for (char letter: letters) {
				int expectedValue = letter - 65;
				int retrievedValue = Integer.parseInt(worker.read(Character.toString(letter)));
				if (retrievedValue == expectedValue) correct++;
				results.add(String.format("%c : Expected %d, Retrieved %d", letter, expectedValue, retrievedValue));
			}

			double accuracy = (double) correct / letters.size() * 100;
			String accuracyResult = String.format("Consistency Accuracy: %.2f%%%n", accuracy);
			System.out.println(accuracyResult);
			logger.info("Consistency Test Results:");
			results.forEach(logger::info);
			logger.info(accuracyResult);

//			addNewNode(stoppedNodes.get(0), cluster);
//
//			correct = 0;
//			for (char letter: letters) {
//				int expectedValue = letter - 65;
//				int retrievedValue = Integer.parseInt(worker.read(Character.toString(letter)));
//				if (retrievedValue == expectedValue) correct++;
//				results.add(String.format("%c : Expected %d, Retrieved %d", letter, expectedValue, retrievedValue));
//			}


			worker.stopIgnite();
			stopCluster(cluster);
			for (String each: results) {
				System.out.println(each);
			}

		}

		logger.info("Consistency Test Completed");
	}


}
