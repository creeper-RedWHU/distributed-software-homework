#!/bin/bash

# =============================================
# Kubernetes 集群部署脚本
# =============================================

set -e

# 颜色输出
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

NAMESPACE="seckill"
K8S_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 检查 kubectl
check_kubectl() {
    if ! command -v kubectl &> /dev/null; then
        print_error "kubectl 未安装"
        exit 1
    fi
    print_success "kubectl 已安装"
}

# 创建命名空间
create_namespace() {
    print_info "创建命名空间 $NAMESPACE..."
    kubectl apply -f "$K8S_DIR/namespace.yaml"
    print_success "命名空间创建成功"
}

# 创建 Secrets
create_secrets() {
    print_info "创建 Secrets..."

    # 检查 secret 是否已存在
    if kubectl get secret mysql-secret -n $NAMESPACE &> /dev/null; then
        print_warning "mysql-secret 已存在，跳过创建"
    else
        kubectl create secret generic mysql-secret \
            --from-literal=root-password='root' \
            --from-literal=user-password='seckill123' \
            -n $NAMESPACE
        print_success "mysql-secret 创建成功"
    fi
}

# 创建 ConfigMap
create_configmap() {
    print_info "创建 ConfigMap..."
    kubectl apply -f "$K8S_DIR/configmap.yaml"

    # 创建 MySQL 初始化 SQL ConfigMap
    if [ -f "../src/main/resources/sql/schema.sql" ]; then
        kubectl create configmap mysql-init-sql \
            --from-file=schema.sql=../src/main/resources/sql/schema.sql \
            -n $NAMESPACE \
            --dry-run=client -o yaml | kubectl apply -f -
        print_success "ConfigMap 创建成功"
    fi
}

# 部署中间件
deploy_middleware() {
    print_info "部署 MySQL..."
    kubectl apply -f "$K8S_DIR/mysql-statefulset.yaml"

    print_info "部署 Redis..."
    kubectl apply -f "$K8S_DIR/redis-statefulset.yaml"

    print_info "部署 Kafka..."
    kubectl apply -f "$K8S_DIR/kafka-statefulset.yaml"

    print_success "中间件部署完成"
}

# 等待中间件就绪
wait_middleware() {
    print_info "等待中间件就绪..."

    kubectl wait --for=condition=ready pod -l app=mysql -n $NAMESPACE --timeout=300s
    kubectl wait --for=condition=ready pod -l app=redis -n $NAMESPACE --timeout=300s
    kubectl wait --for=condition=ready pod -l app=kafka -n $NAMESPACE --timeout=300s

    print_success "中间件已就绪"
}

# 部署应用
deploy_app() {
    print_info "部署应用..."
    kubectl apply -f "$K8S_DIR/app-deployment.yaml"
    kubectl apply -f "$K8S_DIR/app-service.yaml"

    print_success "应用部署完成"
}

# 等待应用就绪
wait_app() {
    print_info "等待应用就绪..."
    kubectl wait --for=condition=ready pod -l app=seckill-app -n $NAMESPACE --timeout=300s
    print_success "应用已就绪"
}

# 部署 Ingress
deploy_ingress() {
    print_info "部署 Ingress..."
    kubectl apply -f "$K8S_DIR/ingress.yaml"
    print_success "Ingress 部署完成"
}

# 部署 HPA
deploy_hpa() {
    print_info "部署 HPA..."
    kubectl apply -f "$K8S_DIR/hpa.yaml"
    print_success "HPA 部署完成"
}

# 显示状态
show_status() {
    echo ""
    print_info "集群状态："
    kubectl get all -n $NAMESPACE

    echo ""
    print_info "Ingress 状态："
    kubectl get ingress -n $NAMESPACE

    echo ""
    print_success "=========================================="
    print_success "Kubernetes 部署成功！"
    print_success "=========================================="
    echo ""
    echo -e "${GREEN}访问信息：${NC}"
    echo "  命名空间: $NAMESPACE"
    echo "  应用服务: seckill-app-service"
    echo ""
    echo -e "${BLUE}常用命令：${NC}"
    echo "  查看 Pod: kubectl get pods -n $NAMESPACE"
    echo "  查看日志: kubectl logs -f deployment/seckill-app -n $NAMESPACE"
    echo "  查看服务: kubectl get svc -n $NAMESPACE"
    echo "  进入容器: kubectl exec -it deployment/seckill-app -n $NAMESPACE -- sh"
    echo ""
}

# 清理资源
cleanup() {
    print_warning "清理所有资源..."
    read -p "确认删除命名空间 $NAMESPACE 及所有资源？(yes/no) " confirm
    if [ "$confirm" = "yes" ]; then
        kubectl delete namespace $NAMESPACE
        print_success "资源清理完成"
    else
        print_info "取消清理"
    fi
}

# 主函数
main() {
    case "${1:-deploy}" in
        deploy)
            check_kubectl
            create_namespace
            create_secrets
            create_configmap
            deploy_middleware
            wait_middleware
            deploy_app
            wait_app
            deploy_ingress
            deploy_hpa
            show_status
            ;;
        cleanup)
            cleanup
            ;;
        status)
            kubectl get all -n $NAMESPACE
            ;;
        logs)
            kubectl logs -f deployment/seckill-app -n $NAMESPACE
            ;;
        *)
            echo "用法: $0 {deploy|cleanup|status|logs}"
            exit 1
            ;;
    esac
}

main "$@"
