#!/bin/bash

# Step 1: Install Conda if not already installed
echo "Checking for Conda installation..."
if ! command -v conda &> /dev/null; then
    echo "Conda not found. Downloading Miniconda..."
    wget https://repo.anaconda.com/miniconda/Miniconda3-latest-Linux-x86_64.sh -O miniconda.sh
    bash miniconda.sh -b -p $HOME/miniconda
    export PATH="$HOME/miniconda/bin:$PATH"
    echo 'export PATH="$HOME/miniconda/bin:$PATH"' >> ~/.bashrc
    source ~/.bashrc
else
    echo "Conda is already installed."
fi

# Step 2: Create a Conda environment with Python 3.12
ENV_NAME="redis_env"
echo "Creating a Conda environment named $ENV_NAME with Python 3.12..."
conda create -n $ENV_NAME python=3.12 -y

# Step 3: Activate the Conda environment
echo "Activating the Conda environment..."
source activate $ENV_NAME

# Step 4: Install Python modules
echo "Installing required Python modules..."
pip install redis redis-py-cluster numpy

# Step 5: Install Docker (if not already installed)
echo "Checking for Docker installation..."
if ! command -v docker &> /dev/null; then
    echo "Docker not found. Installing Docker..."
    sudo apt-get update
    sudo apt-get install -y docker.io
else
    echo "Docker is already installed."
fi

echo "Setup completed!"
