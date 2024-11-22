package com.Tester.LatencyThroughputTest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LatencyThroughputTestApplication {

	public static void main(String[] args) {
		LatencyThroughputTester tester = new LatencyThroughputTester("hello");
		tester.runWorkloadTests();
		tester.runConcurrencyTests();
//		System.out.println("Hello");
		System.exit(0);
	}
}
