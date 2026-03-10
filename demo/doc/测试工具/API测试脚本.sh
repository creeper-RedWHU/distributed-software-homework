#!/bin/bash

# Shiro 登录系统 API 测试脚本

BASE_URL="http://localhost:8080"
COOKIE_FILE="/tmp/shiro_cookie.txt"

echo "======================================"
echo "  Shiro 登录系统 API 测试"
echo "======================================"
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 测试函数
test_api() {
    local description=$1
    local method=$2
    local endpoint=$3
    local data=$4
    local use_cookie=$5

    echo -e "${YELLOW}测试: $description${NC}"
    echo "请求: $method $endpoint"

    if [ "$use_cookie" = "true" ]; then
        if [ -n "$data" ]; then
            response=$(curl -s -w "\nHTTP_CODE:%{http_code}" -X $method "$BASE_URL$endpoint" \
                -H "Content-Type: application/json" \
                -b "$COOKIE_FILE" \
                -d "$data")
        else
            response=$(curl -s -w "\nHTTP_CODE:%{http_code}" -X $method "$BASE_URL$endpoint" \
                -H "Content-Type: application/json" \
                -b "$COOKIE_FILE")
        fi
    else
        if [ -n "$data" ]; then
            response=$(curl -s -w "\nHTTP_CODE:%{http_code}" -X $method "$BASE_URL$endpoint" \
                -H "Content-Type: application/json" \
                -c "$COOKIE_FILE" \
                -d "$data")
        else
            response=$(curl -s -w "\nHTTP_CODE:%{http_code}" -X $method "$BASE_URL$endpoint" \
                -H "Content-Type: application/json")
        fi
    fi

    http_code=$(echo "$response" | grep "HTTP_CODE" | cut -d':' -f2)
    body=$(echo "$response" | sed '/HTTP_CODE/d')

    if [ "$http_code" = "200" ]; then
        echo -e "${GREEN}✓ 成功 (HTTP $http_code)${NC}"
    else
        echo -e "${RED}✗ 失败 (HTTP $http_code)${NC}"
    fi

    echo "响应: $body" | jq '.' 2>/dev/null || echo "响应: $body"
    echo ""
}

# 1. 测试商家注册
echo "======================================"
echo "1. 用户注册测试"
echo "======================================"
test_api "商家注册" "POST" "/api/auth/register/merchant" \
    '{"username":"test_merchant","password":"123456","phone":"13900001111","email":"merchant@test.com"}' \
    "false"

test_api "买家注册" "POST" "/api/auth/register/buyer" \
    '{"username":"test_buyer","password":"123456","phone":"13900002222","email":"buyer@test.com"}' \
    "false"

# 2. 测试登录
echo "======================================"
echo "2. 用户登录测试"
echo "======================================"
test_api "商家登录" "POST" "/api/auth/login" \
    '{"username":"merchant1","password":"123456"}' \
    "false"

# 3. 测试获取当前用户信息
echo "======================================"
echo "3. 获取用户信息测试（需要登录）"
echo "======================================"
test_api "获取当前用户信息" "GET" "/api/auth/info" \
    "" \
    "true"

# 4. 测试权限
echo "======================================"
echo "4. 权限测试"
echo "======================================"
test_api "测试商家权限" "GET" "/api/auth/test/merchant" \
    "" \
    "true"

test_api "测试买家权限（应该失败）" "GET" "/api/auth/test/buyer" \
    "" \
    "true"

test_api "测试管理员权限（应该失败）" "GET" "/api/auth/test/admin" \
    "" \
    "true"

test_api "测试创建商品权限（商家有此权限）" "GET" "/api/auth/test/create-product" \
    "" \
    "true"

test_api "测试查看订单权限" "GET" "/api/auth/test/view-order" \
    "" \
    "true"

# 5. 测试登出
echo "======================================"
echo "5. 登出测试"
echo "======================================"
test_api "用户登出" "POST" "/api/auth/logout" \
    "" \
    "true"

# 6. 测试买家登录和权限
echo "======================================"
echo "6. 买家登录和权限测试"
echo "======================================"
test_api "买家登录" "POST" "/api/auth/login" \
    '{"username":"buyer1","password":"123456"}' \
    "false"

test_api "测试买家权限" "GET" "/api/auth/test/buyer" \
    "" \
    "true"

test_api "测试商家权限（应该失败）" "GET" "/api/auth/test/merchant" \
    "" \
    "true"

test_api "测试创建商品权限（买家无此权限）" "GET" "/api/auth/test/create-product" \
    "" \
    "true"

# 7. 测试管理员登录和权限
echo "======================================"
echo "7. 管理员登录和权限测试"
echo "======================================"
test_api "管理员登录" "POST" "/api/auth/login" \
    '{"username":"admin1","password":"123456"}' \
    "false"

test_api "测试管理员权限" "GET" "/api/auth/test/admin" \
    "" \
    "true"

test_api "测试所有权限（管理员拥有全部权限）" "GET" "/api/auth/test/create-product" \
    "" \
    "true"

# 清理
rm -f "$COOKIE_FILE"

echo "======================================"
echo "  测试完成"
echo "======================================"
