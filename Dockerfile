# Use a base image with Java 17
FROM openjdk:17-slim

# Install maven 
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

# Set working directory inside the container
WORKDIR /app

# Copy the entire project to the container
COPY ignite /app

# Grant execution permission for the run_tests.sh script
RUN chmod +x /app/run_tests.sh

# Set the default command to execute the script
CMD ["./run_tests.sh"]
