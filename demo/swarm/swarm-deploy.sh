#!/bin/bash

# =============================================
# Docker Swarm 应用部署脚本
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

STACK_NAME="seckill"
COMPOSE_FILE="$(dirname "$0")/docker-compose.swarm.yml"

# 检查 Swarm
check_swarm() {
    if ! docker info 2>/dev/null | grep -q "Swarm: active"; then
        print_error "Swarm 未初始化，请先执行 ./swarm-init.sh"
        exit 1
    fi
    print_success "Swarm 已激活"
}

# 部署栈
deploy_stack() {
    print_info "部署应用栈 $STACK_NAME..."

    docker stack deploy -c "$COMPOSE_FILE" "$STACK_NAME"

    print_success "应用栈部署完成"
}

# 等待服务就绪
wait_services() {
    print_info "等待服务就绪..."

    # 等待 60 秒
    for i in {1..60}; do
        RUNNING=$(docker service ls --filter "label=com.docker.stack.namespace=$STACK_NAME" --format "{{.Replicas}}" | grep -o '^[0-9]*' | paste -sd+ - | bc)
        DESIRED=$(docker service ls --filter "label=com.docker.stack.namespace=$STACK_NAME" --format "{{.Replicas}}" | grep -o '/[0-9]*' | tr -d '/' | paste -sd+ - | bc)

        if [ "$RUNNING" -eq "$DESIRED" ] 2>/dev/null; then
            print_success "所有服务已就绪"
            return
        fi

        echo -n "."
        sleep 2
    done

    print_warning "部分服务可能未就绪，请检查"
}

# 显示状态
show_status() {
    echo ""
    print_info "服务列表:"
    docker stack services "$STACK_NAME"

    echo ""
    print_info "服务任务:"
    docker stack ps "$STACK_NAME" --no-trunc

    echo ""
    print_success "=========================================="
    print_success "应用部署成功！"
    print_success "=========================================="
    echo ""

    echo -e "${GREEN}访问地址:${NC}"
    echo "  - 应用: http://localhost:8080"
    echo "  - Nginx: http://localhost"
    echo "  - 健康检查: http://localhost:8080/actuator/health"
    echo ""

    echo -e "${BLUE}常用命令:${NC}"
    echo "  查看服务: docker service ls"
    echo "  查看日志: docker service logs -f ${STACK_NAME}_app"
    echo "  扩容服务: docker service scale ${STACK_NAME}_app=5"
    echo "  更新服务: docker service update --image new-image:tag ${STACK_NAME}_app"
    echo "  删除栈: docker stack rm $STACK_NAME"
    echo ""
}

# 更新服务
update_service() {
    local service=$1
    local image=$2

    if [ -z "$service" ] || [ -z "$image" ]; then
        print_error "用法: $0 update <service> <image>"
        exit 1
    fi

    print_info "更新服务 ${STACK_NAME}_${service} 到镜像 $image..."

    docker service update \
        --image "$image" \
        --update-parallelism 1 \
        --update-delay 10s \
        "${STACK_NAME}_${service}"

    print_success "服务更新完成"
}

# 扩缩容
scale_service() {
    local service=$1
    local replicas=$2

    if [ -z "$service" ] || [ -z "$replicas" ]; then
        print_error "用法: $0 scale <service> <replicas>"
        exit 1
    fi

    print_info "扩缩容服务 ${STACK_NAME}_${service} 到 $replicas 个副本..."

    docker service scale "${STACK_NAME}_${service}=$replicas"

    print_success "扩缩容完成"
}

# 查看日志
view_logs() {
    local service=${1:-app}

    print_info "查看服务 ${STACK_NAME}_${service} 日志..."
    docker service logs -f "${STACK_NAME}_${service}"
}

# 删除栈
remove_stack() {
    print_warning "删除应用栈 $STACK_NAME..."
    read -p "确认删除？(yes/no) " confirm

    if [ "$confirm" = "yes" ]; then
        docker stack rm "$STACK_NAME"
        print_success "应用栈已删除"
    else
        print_info "取消删除"
    fi
}

# 显示帮助
show_help() {
    echo "Docker Swarm 部署脚本"
    echo ""
    echo "用法: $0 [命令] [参数]"
    echo ""
    echo "命令:"
    echo "  deploy              部署应用栈（默认）"
    echo "  update <svc> <img>  更新服务镜像"
    echo "  scale <svc> <num>   扩缩容服务"
    echo "  logs [service]      查看服务日志"
    echo "  status              查看服务状态"
    echo "  remove              删除应用栈"
    echo "  help                显示帮助信息"
    echo ""
    echo "示例:"
    echo "  $0 deploy"
    echo "  $0 update app your-registry.com/seckill-app:v1.1.0"
    echo "  $0 scale app 5"
    echo "  $0 logs app"
    echo ""
}

# 主函数
main() {
    case "${1:-deploy}" in
        deploy)
            check_swarm
            deploy_stack
            wait_services
            show_status
            ;;
        update)
            check_swarm
            update_service "$2" "$3"
            ;;
        scale)
            check_swarm
            scale_service "$2" "$3"
            ;;
        logs)
            check_swarm
            view_logs "$2"
            ;;
        status)
            check_swarm
            docker stack services "$STACK_NAME"
            docker stack ps "$STACK_NAME"
            ;;
        remove)
            check_swarm
            remove_stack
            ;;
        help)
            show_help
            ;;
        *)
            print_error "未知命令: $1"
            show_help
            exit 1
            ;;
    esac
}

main "$@"
