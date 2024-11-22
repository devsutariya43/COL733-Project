#!/bin/bash

# Array of test directories
test_dirs=("LatencyThroughputTest" "scalabilityTest" "CAPTester" "memoryTest")

# Loop through each test directory and run the test
for test_dir in "${test_dirs[@]}"; do
  echo "Running test in $test_dir..."

  # Navigate to the test directory
  if cd "$test_dir"; then
    # Check if run.sh exists and is executable
    if [[ -x "run.sh" ]]; then
      # Run the test script
      ./run.sh
      echo "Test in $test_dir completed."
    else
      echo "Error: run.sh not found or not executable in $test_dir"
    fi
    # Go back to the original directory
    cd - > /dev/null
  else
    echo "Error: Directory $test_dir not found."
  fi
done

echo "All tests completed."
