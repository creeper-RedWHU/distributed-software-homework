#!/bin/bash

# =============================================
# 秒杀系统 Docker 一键启动脚本
# =============================================

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 打印带颜色的消息
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

# 检查 Docker 是否安装
check_docker() {
    print_info "检查 Docker 环境..."
    if ! command -v docker &> /dev/null; then
        print_error "Docker 未安装，请先安装 Docker"
        exit 1
    fi

    if ! command -v docker-compose &> /dev/null; then
        print_error "Docker Compose 未安装，请先安装 Docker Compose"
        exit 1
    fi

    print_success "Docker 环境检查通过"
}

# 检查端口占用
check_ports() {
    print_info "检查端口占用..."
    ports=(8080 3306 6379 9092 2181)
    occupied_ports=()

    for port in "${ports[@]}"; do
        if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1 ; then
            occupied_ports+=($port)
        fi
    done

    if [ ${#occupied_ports[@]} -gt 0 ]; then
        print_warning "以下端口已被占用: ${occupied_ports[*]}"
        read -p "是否继续？(y/n) " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    else
        print_success "端口检查通过"
    fi
}

# 停止并清理旧容器
cleanup() {
    print_info "清理旧容器..."
    docker-compose down -v 2>/dev/null || true
    print_success "清理完成"
}

# 构建镜像
build_images() {
    print_info "构建应用镜像..."
    docker-compose build --no-cache
    print_success "镜像构建完成"
}

# 启动服务
start_services() {
    print_info "启动所有服务..."
    docker-compose up -d
    print_success "服务启动完成"
}

# 等待服务就绪
wait_for_services() {
    print_info "等待服务就绪..."

    # 等待 MySQL
    print_info "等待 MySQL 就绪..."
    for i in {1..30}; do
        if docker-compose exec -T mysql mysqladmin ping -h localhost -uroot -proot --silent &> /dev/null; then
            print_success "MySQL 已就绪"
            break
        fi
        if [ $i -eq 30 ]; then
            print_error "MySQL 启动超时"
            exit 1
        fi
        sleep 2
    done

    # 等待 Redis
    print_info "等待 Redis 就绪..."
    for i in {1..15}; do
        if docker-compose exec -T redis redis-cli ping &> /dev/null; then
            print_success "Redis 已就绪"
            break
        fi
        if [ $i -eq 15 ]; then
            print_error "Redis 启动超时"
            exit 1
        fi
        sleep 2
    done

    # 等待 Kafka
    print_info "等待 Kafka 就绪..."
    sleep 20
    print_success "Kafka 已就绪"

    # 等待应用
    print_info "等待应用就绪..."
    for i in {1..60}; do
        if curl -s http://localhost:8080/actuator/health &> /dev/null; then
            print_success "应用已就绪"
            break
        fi
        if [ $i -eq 60 ]; then
            print_error "应用启动超时，请查看日志: docker-compose logs app"
            exit 1
        fi
        sleep 3
    done
}

# 显示服务状态
show_status() {
    echo ""
    print_info "服务状态:"
    docker-compose ps

    echo ""
    print_success "=========================================="
    print_success "秒杀系统启动成功！"
    print_success "=========================================="
    echo ""
    echo -e "${GREEN}访问地址:${NC}"
    echo "  - 应用主页: http://localhost:8080"
    echo "  - Nginx 代理: http://localhost"
    echo "  - 健康检查: http://localhost:8080/actuator/health"
    echo ""
    echo -e "${GREEN}中间件连接:${NC}"
    echo "  - MySQL: localhost:3306 (root/root)"
    echo "  - Redis: localhost:6379"
    echo "  - Kafka: localhost:9092"
    echo ""
    echo -e "${BLUE}常用命令:${NC}"
    echo "  查看日志: docker-compose logs -f app"
    echo "  停止服务: docker-compose down"
    echo "  重启服务: docker-compose restart"
    echo "  进入容器: docker-compose exec app sh"
    echo ""
}

# 显示帮助
show_help() {
    echo "用法: $0 [选项]"
    echo ""
    echo "选项:"
    echo "  start      启动所有服务（默认）"
    echo "  stop       停止所有服务"
    echo "  restart    重启所有服务"
    echo "  rebuild    重新构建并启动"
    echo "  clean      清理所有容器和数据"
    echo "  logs       查看应用日志"
    echo "  status     查看服务状态"
    echo "  help       显示帮助信息"
    echo ""
}

# 主函数
main() {
    case "${1:-start}" in
        start)
            check_docker
            check_ports
            start_services
            wait_for_services
            show_status
            ;;
        stop)
            print_info "停止所有服务..."
            docker-compose down
            print_success "服务已停止"
            ;;
        restart)
            print_info "重启所有服务..."
            docker-compose restart
            wait_for_services
            show_status
            ;;
        rebuild)
            check_docker
            cleanup
            build_images
            start_services
            wait_for_services
            show_status
            ;;
        clean)
            print_warning "这将删除所有容器和数据，是否继续？"
            read -p "请输入 'yes' 确认: " confirm
            if [ "$confirm" = "yes" ]; then
                cleanup
                print_success "清理完成"
            else
                print_info "操作已取消"
            fi
            ;;
        logs)
            docker-compose logs -f app
            ;;
        status)
            docker-compose ps
            ;;
        help)
            show_help
            ;;
        *)
            print_error "未知选项: $1"
            show_help
            exit 1
            ;;
    esac
}

# 执行主函数
main "$@"
