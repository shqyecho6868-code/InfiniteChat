# 使用轻量 JRE
FROM eclipse-temurin:17-jre

# 1. 创建指定 UID 的用户 (关键修改：固定 UID 为 1001)
RUN groupadd -g 1001 appgroup && \
    useradd -r -u 1001 -g appgroup appuser

WORKDIR /app

# 设置时区 + 安装 curl
ENV TZ=Asia/Shanghai
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone \
    && rm -rf /var/lib/apt/lists/*

# 复制 jar
COPY target/InfinteChat-Agent-0.0.1-SNAPSHOT.jar app.jar

# 复制初始文档到镜像内的备份目录
COPY src/main/resources/docs/ /init-docs/

# 创建数据目录
RUN mkdir -p /data/docs

# 2. 设置目录权限归属给 appuser (关键)
RUN chown -R appuser:appgroup /app /init-docs /data

USER appuser

EXPOSE 10010

VOLUME /tmp

# 3. 启动脚本：去掉 2>/dev/null 以便看到错误，并增加调试信息
ENTRYPOINT ["sh", "-c", "echo 'Current User:' $(whoami) && echo 'Copying docs...' && cp -rn /init-docs/* /data/docs/ && ls -l /data/docs/ && exec java $JAVA_OPTS -jar /app/app.jar"]