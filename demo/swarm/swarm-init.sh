#!/bin/bash

# =============================================
# Docker Swarm 集群初始化脚本
# =============================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查 Docker
check_docker() {
    if ! command -v docker &> /dev/null; then
        print_error "Docker 未安装"
        exit 1
    fi
    print_success "Docker 已安装"
}

# 初始化 Swarm（仅在管理节点执行）
init_swarm() {
    if docker info 2>/dev/null | grep -q "Swarm: active"; then
        print_warning "Swarm 已初始化"
        return
    fi

    print_info "初始化 Swarm 集群..."

    # 获取本机 IP
    LOCAL_IP=$(hostname -I | awk '{print $1}')
    print_info "使用 IP 地址: $LOCAL_IP"

    # 初始化 Swarm
    docker swarm init --advertise-addr $LOCAL_IP

    print_success "Swarm 集群初始化成功"

    echo ""
    print_info "获取工作节点加入命令:"
    docker swarm join-token worker

    echo ""
    print_info "获取管理节点加入命令:"
    docker swarm join-token manager
}

# 标记节点
label_nodes() {
    print_info "标记节点..."

    # 获取当前节点 ID
    NODE_ID=$(docker node ls --format "{{.ID}}" -f "role=manager" | head -n 1)

    # 标记节点类型
    docker node update --label-add type=database $NODE_ID
    docker node update --label-add type=cache $NODE_ID
    docker node update --label-add type=mq $NODE_ID

    print_success "节点标记完成"
}

# 创建网络
create_network() {
    print_info "创建 overlay 网络..."

    if docker network ls | grep -q seckill-network; then
        print_warning "网络 seckill-network 已存在"
    else
        docker network create \
            --driver overlay \
            --attachable \
            --opt encrypted=true \
            seckill-network
        print_success "网络创建成功"
    fi
}

# 创建 Secrets
create_secrets() {
    print_info "创建 Secrets..."

    # MySQL root 密码
    if docker secret ls | grep -q mysql_root_password; then
        print_warning "Secret mysql_root_password 已存在"
    else
        echo "root" | docker secret create mysql_root_password -
        print_success "创建 mysql_root_password"
    fi

    # MySQL 用户密码
    if docker secret ls | grep -q mysql_user_password; then
        print_warning "Secret mysql_user_password 已存在"
    else
        echo "seckill123" | docker secret create mysql_user_password -
        print_success "创建 mysql_user_password"
    fi
}

# 显示状态
show_status() {
    echo ""
    print_success "=========================================="
    print_success "Swarm 集群初始化成功！"
    print_success "=========================================="
    echo ""

    print_info "节点列表:"
    docker node ls

    echo ""
    print_info "网络列表:"
    docker network ls | grep seckill

    echo ""
    print_info "Secrets 列表:"
    docker secret ls

    echo ""
    print_info "下一步操作:"
    echo "  1. 在其他节点执行 join 命令加入集群"
    echo "  2. 执行 ./swarm-deploy.sh 部署应用栈"
    echo "  3. 查看服务: docker service ls"
    echo ""
}

# 主函数
main() {
    check_docker
    init_swarm
    label_nodes
    create_network
    create_secrets
    show_status
}

main
