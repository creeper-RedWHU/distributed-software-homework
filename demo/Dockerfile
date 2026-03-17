# =============================================
# 商品库存与秒杀系统 - Dockerfile
# 使用多阶段构建优化镜像大小
# =============================================

# 第一阶段：构建阶段（使用非 alpine 镜像，兼容 ARM64 / Apple Silicon）
FROM maven:3.9-eclipse-temurin-17 AS builder

# 设置工作目录
WORKDIR /build

# 复制 Maven 配置文件
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
COPY mvnw.cmd .

# 下载依赖（利用 Docker 缓存层）
RUN mvn dependency:go-offline -B

# 复制源代码
COPY src ./src

# 构建应用（跳过测试以加快构建速度）
RUN mvn clean package -DskipTests -B

# 第二阶段：运行阶段
FROM eclipse-temurin:17-jre

# 设置工作目录
WORKDIR /app

# 创建非 root 用户运行应用
RUN groupadd -r spring && useradd -r -g spring spring
USER spring:spring

# 从构建阶段复制 jar 包
COPY --from=builder /build/target/*.jar app.jar

# 暴露端口（可通过 SERVER_PORT 环境变量覆盖）
EXPOSE 8080

# 健康检查 - 使用 ping 接口
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:${SERVER_PORT:-8080}/ping/ || exit 1

# JVM 参数优化
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
ENV SERVER_PORT=8080

# 启动应用（支持通过 SERVER_PORT 环境变量配置端口）
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -Dserver.port=${SERVER_PORT} -jar /app/app.jar"]
