#!/bin/bash

echo "======================================"
echo "  检查项目是否能编译"
echo "======================================"
echo ""

cd /Users/mac/Desktop/project/distributed-software-homework/demo

# 检查是否有 Maven
if ! command -v mvn &> /dev/null; then
    echo "❌ Maven 未安装，尝试使用 ./mvnw"
    if [ -f "./mvnw" ]; then
        MVN_CMD="./mvnw"
    else
        echo "❌ 无法找到 Maven 或 mvnw"
        exit 1
    fi
else
    MVN_CMD="mvn"
fi

echo "✓ 使用命令: $MVN_CMD"
echo ""

echo "开始编译..."
echo "======================================"

$MVN_CMD clean compile -DskipTests

if [ $? -eq 0 ]; then
    echo ""
    echo "======================================"
    echo "✅ 编译成功！"
    echo "======================================"
    exit 0
else
    echo ""
    echo "======================================"
    echo "❌ 编译失败，请检查错误信息"
    echo "======================================"
    exit 1
fi
