package com.ignite.scalabilityTest;

import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.*;

public class ScalabilityTestApplication {
	private final List<Integer> nodes;
	private final int numWorkers;
	private final int numReadOps;
	private final int numWriteOps;
	private final int dataSize;
	private final List<Integer> backupList;

	private static final Logger logger = Logger.getLogger(ScalabilityTestApplication.class.getName());

	static {
		// Configure the logger to write to a file
		try {
			FileHandler fileHandler = new FileHandler("scalability_test_log.log", true);
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

	public ScalabilityTestApplication(List<Integer> nodes, List<Integer> backupList, int numWorkers, int numReadOps, int numWriteOps, int dataSize) {
		this.nodes = nodes;
		this.numWorkers = numWorkers;
		this.numReadOps = numReadOps;
		this.numWriteOps = numWriteOps;
		this.dataSize = dataSize;
		this.backupList = backupList;
	}

	public void runScalabilityTest1() {
		logger.info("Running Variable Node Cluster Test");
		logger.info(String.format("%-10s | %-10s | %-15s | %-20s%n", "NUM NODES", "OPR TYPE", "LATENCY (ms)", "THROUGHPUT (ops/ms)"));
		System.out.println("Running Variable Node Cluster Test");
		System.out.printf("%-10s | %-10s | %-15s | %-20s%n", "NUM NODES", "OPR TYPE", "LATENCY (ms)", "THROUGHPUT (ops/ms)");

		for (int numNodes : nodes) {
			IgniteCluster igniteCluster = new IgniteCluster(numNodes, 0);

			for (String testType: List.of("write", "read", "mixed")) {
				List<Double> latencies = new CopyOnWriteArrayList<>();
				ExecutorService executorService = Executors.newFixedThreadPool(numWorkers);

				try {
					List<Future<?>> futures = new ArrayList<>();
					for (int i = 0; i < numWorkers; i++) {
						Future<?> future = executorService.submit(() -> {
							double latency;
							switch (testType) {
								case "read" -> latency = igniteCluster.performReadOperation(numReadOps);
								case "write" -> latency = igniteCluster.performWriteOperations(numWriteOps);
								case "mixed" -> latency = igniteCluster.performMixedOperation(numReadOps, numWriteOps);
								default -> throw new IllegalArgumentException("Unknown operation type: " + testType);
							}
							latencies.add(latency);
						});
						futures.add(future);
					}
					for (Future<?> future: futures) future.get();
				} catch (InterruptedException | ExecutionException e) {
					e.printStackTrace();
				} finally {
					executorService.shutdown();
				}

				// Calculate and print latency and throughput
				if (!latencies.isEmpty()) {
					double avgLatency = latencies.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
					System.out.println(latencies.size());
					System.out.println(latencies);
					double totalTime = latencies.stream().mapToDouble(Double::doubleValue).sum();
					double throughput = 1000 / avgLatency; // Throughput in ops/ms
					logger.info(String.format("%-10d | %-10s | %-15.4f | %-20.4f", numNodes, testType, avgLatency, throughput));
					System.out.printf("%-10d | %-10s | %-15.4f | %-20.4f%n", numNodes, testType, avgLatency, throughput);
				} else {
//					logger.warning("No latencies recorded for " + testType + " operations with " + numNodes + " nodes.");
					System.out.println("No latencies recorded for " + testType + " operations with " + numNodes + " nodes.");
				}
			}
			igniteCluster.endCluster();
			try {
				int waitTimeInMillis = 5000; // 5 seconds
				Thread.sleep(waitTimeInMillis);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		logger.info("Variable Node Cluster Test Completed");
		System.out.println("Variable Node Cluster Test Completed");
	}

	public void runScalabilityTest2() {
		logger.info("Running Variable BackupsTest");
		logger.info(String.format("%-10s | %-10s | %-15s | %-20s%n", "NUM NODES", "OPR TYPE", "LATENCY (ms)", "THROUGHPUT (ops/ms)"));
		System.out.println("Running Variable Node Test");
		System.out.printf("%-10s | %-10s | %-15s | %-20s%n", "NUM NODES", "OPR TYPE", "LATENCY (ms)", "THROUGHPUT (ops/ms)");

		int numNodes = 6;
		for (int backups : backupList) {
			IgniteCluster igniteCluster = new IgniteCluster(numNodes, backups);

			for (String testType: List.of("write", "read", "mixed")) {
				List<Double> latencies = new CopyOnWriteArrayList<>();
				ExecutorService executorService = Executors.newFixedThreadPool(numWorkers);

				try {
					List<Future<?>> futures = new ArrayList<>();
					for (int i = 0; i < numWorkers; i++) {
						Future<?> future = executorService.submit(() -> {
							double latency;
							switch (testType) {
								case "read" -> latency = igniteCluster.performReadOperation(numReadOps);
								case "write" -> latency = igniteCluster.performWriteOperations(numWriteOps);
								case "mixed" -> latency = igniteCluster.performMixedOperation(numReadOps, numWriteOps);
								default -> throw new IllegalArgumentException("Unknown operation type: " + testType);
							}
							latencies.add(latency);
						});
						futures.add(future);
					}
					for (Future<?> future: futures) future.get();
				} catch (InterruptedException | ExecutionException e) {
					e.printStackTrace();
				} finally {
					executorService.shutdown();
				}

				// Calculate and print latency and throughput
				if (!latencies.isEmpty()) {
					double avgLatency = latencies.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
					System.out.println(latencies.size());
					System.out.println(latencies);
					double totalTime = latencies.stream().mapToDouble(Double::doubleValue).sum();
					double throughput = 1000 / avgLatency; // Throughput in ops/ms
					logger.info(String.format("%-10d | %-10s | %-15.4f | %-20.4f", numNodes, testType, avgLatency, throughput));
					System.out.printf("%-10d | %-10s | %-15.4f | %-20.4f%n", numNodes, testType, avgLatency, throughput);
				} else {
//					logger.warning("No latencies recorded for " + testType + " operations with " + numNodes + " nodes.");
					System.out.println("No latencies recorded for " + testType + " operations with " + numNodes + " nodes.");
				}
			}
			igniteCluster.endCluster();
			try {
				int waitTimeInMillis = 5000; // 5 seconds
				Thread.sleep(waitTimeInMillis);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		logger.info("Variable Node Cluster Test Completed");
		System.out.println("Variable Node Cluster Test Completed");
	}

	public void scalabilityTest() {
		logger.info("Running variable Node cluster test");

	}
	public static void main(String[] args) {
		List<Integer> nodes = List.of(3, 6, 9, 12, 15, 18, 24, 27);
		List<Integer> backupNodes = List.of(1, 2, 3, 4, 5);
		int numWorkers = 4;
		int numReadOps = 500;
		int numWriteOps = 500;
		int dataSize = 100;

		ScalabilityTestApplication tester = new ScalabilityTestApplication(nodes, backupNodes, numWorkers, numReadOps, numWriteOps, dataSize);
		// Test with variable nodes and 0 backups
//		tester.runScalabilityTest1();
		tester.runScalabilityTest2();

		return;
	}
}
//
//@SpringBootApplication
//public class ScalabilityTestApplication {
//	private final List<Integer> nodes;
//	private final int numWorkers;
//	private final int numReadOps;
//	private final int numWriteOps;
//	private final int dataSize;
//
//	public ScalabilityTestApplication(List<Integer> nodes, int numWorkers, int numReadOps, int numWriteOps, int dataSize) {
//		this.nodes = nodes;
//		this.numWorkers = numWorkers;
//		this.numReadOps = numReadOps;
//		this.numWriteOps = numWriteOps;
//		this.dataSize = dataSize;
//	}
//
//	private List<Ignite> igniteCluster = new ArrayList<>();
//
//	public void createCluster(int numNodes) {
//		for (int i = 0; i < numNodes; i++) {
//			IgniteConfiguration cfg = new IgniteConfiguration();
//			cfg.setIgniteInstanceName("node-" + i);
//			igniteCluster.add(Ignition.start(cfg));
//		}
//	}
//
//	public void deleteCluster() {
//		for (Ignite ignite: igniteCluster) {
//			ignite.close();
//		}
//		igniteCluster.clear();
//	}
//
//	private List<IgniteClusterWorker> setupWorkers() {
//		List<IgniteClusterWorker> workers = new ArrayList<>();
//		List<ClusterNode> nodes = new ArrayList<>(igniteCluster.get(0).cluster().nodes());
//
//		for (int i = 0; i < numWorkers; i++) {
//			workers.add(new IgniteClusterWorker(nodes, numReadOps, numWriteOps, dataSize));
//		}
//
//		return workers;
//	}
//
//	public void runWorkerTask(IgniteClusterWorker worker, List<Double> latencies, String operationType) {
//		double latency = worker.run(operationType);
//		latencies.add(latency);
//	}
//
//	public void runScalabiltiyTest() {
//		System.out.println("Running Variable Node Cluster Test");
//		System.out.printf("%-10s | %-10s | %-15s | %-20s%n", "NUM NODES", "OPR TYPE", "LATENCY (ms)", "THROUGHPUT (ops/ms)");
//
//		for (int numNodes: nodes) {
//			createCluster(numNodes);
//			List<IgniteClusterWorker> workers = setupWorkers();
//
//			for (String testType : List.of("write", "read", "mixed")) {
//				List<Double> latencies = new CopyOnWriteArrayList<>();
//				ExecutorService executorService = Executors.newFixedThreadPool(numWorkers);
//
//				try {
//					List<Future<?>> futures = new ArrayList<>();
//					for (IgniteClusterWorker worker: workers) {
//						Future<?> future = executorService.submit(() -> runWorkerTask(worker, latencies, testType));
//						futures.add(future);
//					}
//					for (Future<?> future: futures) future.get();
//				} catch (InterruptedException | ExecutionException e) {
//					e.printStackTrace();
//				} finally {
//					executorService.shutdown();
//				}
//
//				// Calculate and print latency and throughput
//				if (!latencies.isEmpty()) {
//					double avgLatency = latencies.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
//					int totalOps = switch (testType) {
//						case "read" + "write" -> 5000;
//						case "mixed" -> 10000;
//						default -> 0;
//					};
//					double throughput = totalOps / 1000 * avgLatency;
//					System.out.printf("%-10d | %-10s | %-15.4f | %-20.4f%n", numNodes, testType, avgLatency, throughput);
//				} else {
//					System.out.println("No latencies recorded for " + testType + " operations with " + numNodes + " nodes.");
//				}
//			}
//			deleteCluster();
//		}
//		System.out.println("Variable Node Cluster Test Completed");
//	}
//
//
//	public static void main(String[] args) {
//		List<Integer> nodes = List.of(1, 3, 5);
//		int numWorkers = 10;
//		int numReadOps = 1000;
//		int numWriteOps = 1000;
//		int dataSize = 1024; // Size in KB
//
//		ScalabilityTestApplication tester = new ScalabilityTestApplication(nodes, numWorkers, numReadOps, numWriteOps, dataSize);
//		tester.runScalabiltiyTest();
//	}
//
//}
