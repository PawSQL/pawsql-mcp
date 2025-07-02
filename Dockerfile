# 使用官方 JDK 17 精简版作为基础镜像
FROM openjdk:17-slim

# 设置工作目录
WORKDIR /app

# 复制构建好的 jar 文件到容器中
COPY target/pawsql-mcp-server-0.0.1-SNAPSHOT.jar app.jar

# 设置环境变量，指定服务端口（建议容器内部也显式暴露此端口）
ENV PAWSQL_MCP_PORT=8080

# 启动应用
CMD ["java", "-jar", "app.jar"]

# 构建步骤:
# 1. 构建 jar 文件
#    mvn clean package

# 2. Build Docker image
# docker build -t pawsql/pawsql-mcp-server-sse:latest .
# docker buildx build --platform linux/amd64,linux/arm64 -t pawsql/pawsql-mcp-server:0.1.0_beta -t pawsql/pawsql-mcp-server:latest --push .

# 运行 SSE 服务（注意容器内外端口一致）:
# docker run -d --name pawsql-mcp-server -p 8080:8080 -e PAWSQL_API_BASE_URL=http://localhost:8002 pawsql/pawsql-mcp-server-sse:latest
