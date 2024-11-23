# Setup to Run Tests

## Redis Tests

The tests will use 6379 onward ports for Redis and 26379 onward ports for Sentinel. Please stop any other Docker container running on these ports, as doing so will fail to create the required containers.

### Method1
1. Download `redis` folder.
2. cd to `redis`
3. Run `make setup` to set the requirements
4. Run `make redis_tests` to run Redis tests
5. Run `make redis_sentinel_tests` to run Redis Sentinel tests

### Method2
If the above method doesn't work or throws any error use the following method. Ensure you have python3.12 (or at least >=python3.10) installed. Run it on Baadal as it has docker installed. (otherwise have to install docker as well)

1. Run `pip3.12 install -r requirements.txt` to install requirements. (use appropriate pip)
2. Run `python3.12 redis_tests.py` to run Redis tests
3. Run `python3.12 redis_sentinel_tests.py` to run Redis Sentinel tests

## Ignite Tests

For Ignite Tests, we have used Java-17 and maven to run the tests.

### Method
1. Download `ignite` folder.
2. Download the `Dockerfile`. Keep the Dockerfile outside the ignite folder.
3. Run `docker build -t ignite-tests .`
4. Run `docker run --rm ignite-tests`
