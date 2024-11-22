import random

def crc16(data: str):
    """Calculate CRC16 hash for a given string."""
    crc = 0xFFFF
    for byte in data.encode('utf-8'):
        crc ^= byte
        for _ in range(8):
            if crc & 1:
                crc = (crc >> 1) ^ 0xA001
            else:
                crc >>= 1
    return crc & 0xFFFF

def calculate_hash_slot(key: str):
    """Calculate the Redis hash slot for a key."""
    hash_value = crc16(key)
    return hash_value % 16384  # Redis hash slot range is 0-16383

def generate_random_key():
    """Generate a random key that will have uniform distribution over all Redis hash slots."""
    while True:
        # Generate a random key of alphanumeric characters
        key = ''.join(random.choices('abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789', k=random.randint(5, 15)))
        hash_slot = calculate_hash_slot(key)
        if 0 <= hash_slot < 16384:
            return key, hash_slot
