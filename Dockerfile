FROM eclipse-temurin:8-jdk-focal
MAINTAINER qianmoQ "shicheng@devlive.com"

# 安装并配置 Nginx
RUN apt-get update && \
    apt-get install -y nginx && \
    rm -rf /var/lib/apt/lists/*
COPY configure/proxy/nginx.conf /etc/nginx/conf.d/grantforge.conf
COPY core/grantforge-web/dist /opt/app/grantforge-web
RUN nginx -V

# 安装 MySQL
RUN apt-get update && \
    apt-get install -y mysql-server && \
    rm -rf /var/lib/apt/lists/*
COPY configure/docker/my.cnf /etc/mysql/my.cnf
# Database creation and schema migration happen at runtime, not during image build.

# 安装 GrantForge
RUN mkdir -p /opt/app
ADD dist/grantforge-release.tar.gz /opt/app/
COPY configure/docker/entrypoint.sh /opt/app/grantforge
WORKDIR /opt/app/grantforge

# MySQL端口 3306
EXPOSE 3306
# Web端口 9998
EXPOSE 9096
# API端口 9999
EXPOSE 9999

# 运行主服务
ENTRYPOINT ["sh", "entrypoint.sh"]
