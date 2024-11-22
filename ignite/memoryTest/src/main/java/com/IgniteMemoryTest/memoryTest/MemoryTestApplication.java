package com.IgniteMemoryTest.memoryTest;

import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.*;

public class MemoryTestApplication {

	private static final Logger logger = Logger.getLogger(MemoryTestApplication.class.getName());

	static {
		// Configure the logger to write to a file
		try {
			FileHandler fileHandler = new FileHandler("memory_test_log.log", true);
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
		runMemoryTest(3);
		runMemoryTest(1);
		System.exit(0);
	}

	public static List<Ignite> igniteCluster(int numNodes) {
		List<Ignite> nodes = new ArrayList<>();

		for (int i = 0; i < numNodes; i++) {
			addNewNode("ignite-node-" + i, nodes);
		}
		return nodes;
	}

	private static void addNewNode(String nodeName, List<Ignite> nodes) {
		IgniteConfiguration cfg = getIgniteConfiguration(nodeName);
		Ignite ignite = Ignition.start(cfg);
		nodes.add(ignite);
	}

	private static IgniteConfiguration getIgniteConfiguration(String ndoeName) {
		IgniteConfiguration cfg = new IgniteConfiguration();
		cfg.setIgniteInstanceName(ndoeName);

		TcpDiscoverySpi dsc = new TcpDiscoverySpi();
		TcpDiscoverySpi discoverySpi = new TcpDiscoverySpi();
		TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
		ipFinder.setAddresses(List.of("127.0.0.1:47500..47509"));
		discoverySpi.setIpFinder(ipFinder);
		cfg.setDiscoverySpi(discoverySpi);

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

			try {
				int waitTime = ThreadLocalRandom.current().nextInt(2000, 5000);
				System.out.println("Waiting for " + waitTime + " milliseconds before proceeding");
				Thread.sleep(waitTime);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				System.out.println("Wait interrupted after stopping node: " + nodeName);
			}
		} else {
			System.out.println("Node not found: " + nodeName);
		}
	}

	public static void stopCluster(List<Ignite> nodes) {
		System.out.println("Stopping entire cluster...");
		for (Ignite node: nodes) {
			System.out.println("Stopping node: " + node.name());
			node.close();
		}
		nodes.clear();
		System.out.println("Cluster stopped.");

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

	public static List<IgniteClusterWorker> setup_workers(int num_workers, int dataSize) {
		List<IgniteClusterWorker> workers = new ArrayList<>();
		for (int i = 0; i < num_workers; i++) {
			workers.add(new IgniteClusterWorker(5000, 5000, dataSize*1024));
		}
		return workers;
	}

	public static void runWorkerProcess(IgniteClusterWorker worker, List<Double> latencies, List<Double> Latencies50, List<Double> Latencies75, List<Double> Latencies80, List<Double> Latencies85, List<Double> Latencies90, List<Double> Latencies95, List<Double> Latencies99, List<Double> Latencies995,List<Double> throughputs, String oprType) {
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

	public static void runMemoryTest(int numNodes) {
		System.out.println("Running Memory test");

		List<List<Object>> results = new ArrayList<>();
		results.add(List.of("DATA SIZE", "LATENCY (ms)", "Latency50", "Latency75", "Latency80", "Latency85", "Latency90", "Latency95", "Latency99", "Latency995", "THROUGHPUT (oprs/ms)"));

		for (int i : List.of(1, 2, 4, 8, 16, 32, 128, 256, 512, 1024)) {
			try {
				List<Ignite> cluster = igniteCluster(numNodes);

				List<IgniteClusterWorker> workers = setup_workers(4, i);

				ExecutorService executorService = Executors.newFixedThreadPool(workers.size());
				List<Future<?>> futures = new ArrayList<>();

				// Shared lists for recording latencies
				List<Double> latencies = new ArrayList<>();
//				List<Double> tailLatencies = new ArrayList<>();
				List<Double> throughputs = new ArrayList<>();
				List<Double> Latencies50 = new ArrayList<>();
				List<Double> Latencies75 = new ArrayList<>();
				List<Double> Latencies80 = new ArrayList<>();
				List<Double> Latencies85 = new ArrayList<>();
				List<Double> Latencies90 = new ArrayList<>();
				List<Double> Latencies95 = new ArrayList<>();
				List<Double> Latencies99 = new ArrayList<>();
				List<Double> Latencies995 = new ArrayList<>();

				for (IgniteClusterWorker worker : workers) {
					futures.add(executorService.submit(() -> runWorkerProcess(worker, latencies, Latencies50, Latencies75, Latencies80, Latencies85, Latencies90, Latencies95, Latencies99, Latencies995, throughputs, "mixed")));
				}

				for (Future<?> future : futures) {
					future.get();
				}

				if (!latencies.isEmpty()) {
					double avgLatency = latencies.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
					double latency50 = Latencies50.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
					double latency75 = Latencies75.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
					double latency80 = Latencies80.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
					double latency85 = Latencies85.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
					double latency90 = Latencies90.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
					double latency95 = Latencies95.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
					double latency99 = Latencies99.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
					double latency995 = Latencies995.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

					double avgOperations = 10000;
					double throughput = throughputs.stream().mapToDouble(Double::doubleValue).average().orElse(0.0) * 1000;

					// Log results to the file
					logger.info(String.format("Average Latency with datasize %d KB: %.8f ms", i, avgLatency));
					logger.info(String.format("Average Throughput with datasize %d KB: %.8f operations/ms", i, throughput));

					results.add(List.of(i, avgLatency, latency50, latency75, latency80, latency85, latency90, latency95, latency99, latency995, throughput));
				}

				for (IgniteClusterWorker worker: workers) {
					worker.stopIgnite();
				}

				stopCluster(cluster);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		// Log the result table to the file
		logger.info("Results of Memory Test:");
		for (List<Object> row : results) {
			logger.info(row.toString());
		}
	}
}
