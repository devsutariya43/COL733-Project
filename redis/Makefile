# Define variables
SHELL := /bin/bash
CONDA_ENV := redis_env

# # Declare .PHONY targets to ensure they always run
# .PHONY: setup redis_tests redis_sentinel_tests clean

# Commands
setup:
	@echo "Ensuring setup script is executable..."
	chmod +x redis_setup.sh
	@echo "Running setup.sh..."
	./redis_setup.sh

redis_tests:
	@echo "Ensuring redis_tests.py is executable..."
	@echo "Activating Conda environment and running redis_tests.py..."
	source activate $(CONDA_ENV) && python redis_tests.py

redis_sentinel_tests:
	@echo "Ensuring redis_sentinel_tests.py is executable..."
	@echo "Activating Conda environment and running redis_sentinel_tests.py..."
	source activate $(CONDA_ENV) && python redis_sentinel_tests.py

clean:
	@echo "Ensuring cleanup scripts are executable..."
	chmod +x del_cluster.sh del_sentinel.sh
	@echo "Running cleanup scripts..."
	@if [ -f redis_hosts.json ]; then rm redis_hosts.json; fi
	./del_cluster.sh
	./del_sentinel.sh
	@if docker ps -a --format '{{.Names}}' | grep -q '^redis$$'; then docker stop redis && docker rm redis; fi
	@if conda env list | grep -q "^$(CONDA_ENV) "; then conda remove -n $(CONDA_ENV) --all -y; fi
