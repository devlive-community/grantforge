FROM eclipse-temurin:8-jdk-focal
MAINTAINER qianmoQ "shicheng@devlive.com"

# 安装并配置 Nginx
RUN apt-get update && \
    apt-get install -y nginx && \
    rm -rf /var/lib/apt/lists/*
COPY configure/proxy/nginx.conf /etc/nginx/conf.d/grantforge.conf
COPY core/grantforge-web/dist /opt/app/grantforge-web
RUN nginx -V
CMD ["nginx", "-g", "daemon off;"]

# 安装 MySQL
RUN apt-get update && \
    apt-get install -y mysql-server && \
    rm -rf /var/lib/apt/lists/*
RUN service mysql start && \
    mysqladmin -u root password '12345678'
COPY configure/docker/my.cnf /etc/mysql/my.cnf
# 执行MySQL命令来加载数据
RUN mkdir -p /opt/authx_mysql/
COPY script/schema/schema.sql /opt/authx_mysql/
RUN service mysql start && \
    mysql -u root -p'12345678' -e "CREATE DATABASE authx;" && \
    mysql -u root -p'12345678' -e  "CREATE USER 'root'@'%' IDENTIFIED BY '12345678';" && \
    mysql -u root -p'12345678' -e  "GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION;" && \
    mysql -u root -p'12345678' -e  "FLUSH PRIVILEGES;" && \
    mysql -u root -p'12345678' -e "delete from mysql.user where user = 'root' and host = 'localhost';" && \
    mysql -u root -p'12345678' -e "ALTER USER 'root'@'%' IDENTIFIED WITH mysql_native_password BY '12345678';" && \
    mysql -u root -p'12345678' authx < /opt/authx_mysql/schema.sql
CMD ["mysqld_safe"]

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
