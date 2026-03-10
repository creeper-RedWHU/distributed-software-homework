#!/bin/bash

# 使用 Python 生成 SHA-256 加密密码
python3 << 'PYTHON_SCRIPT'
import hashlib

def sha256_hash(password, salt, iterations=1024):
    """模拟 Shiro SHA-256 加密"""
    result = password.encode('utf-8')
    salt_bytes = salt.encode('utf-8')

    for i in range(iterations):
        result = hashlib.sha256(result + salt_bytes).digest()

    return result.hex()

# 测试密码
password = "123456"

# 固定盐值
salt1 = "salt1merchant1234567890abcdefghij"
salt2 = "salt2buyer1234567890abcdefghijklm"
salt3 = "salt3admin1234567890abcdefghijklm"

print("=== 生成加密密码 ===")
print(f"原始密码: {password}\n")

encrypted1 = sha256_hash(password, salt1)
print(f"商家 (merchant1):")
print(f"  盐值: {salt1}")
print(f"  加密密码: {encrypted1}")
print()

encrypted2 = sha256_hash(password, salt2)
print(f"买家 (buyer1):")
print(f"  盐值: {salt2}")
print(f"  加密密码: {encrypted2}")
print()

encrypted3 = sha256_hash(password, salt3)
print(f"管理员 (admin1):")
print(f"  盐值: {salt3}")
print(f"  加密密码: {encrypted3}")
print()

print("=== SQL 插入语句 ===")
print("INSERT IGNORE INTO t_user_login (id, user_id, username, password, user_type, salt, status) VALUES")
print(f"(1, 1, 'merchant1', '{encrypted1}', 'MERCHANT', '{salt1}', 1),")
print(f"(2, 2, 'buyer1', '{encrypted2}', 'BUYER', '{salt2}', 1),")
print(f"(3, 3, 'admin1', '{encrypted3}', 'ADMIN', '{salt3}', 1);")

PYTHON_SCRIPT
