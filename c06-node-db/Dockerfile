FROM critoma/linux-u20-dev-security-ism:latest

WORKDIR /app

RUN apt-get update -qq && apt-get install -y -qq --no-install-recommends \
    mysql-server mysql-client \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /var/run/mysqld && chown mysql:mysql /var/run/mysqld

RUN apt-get update -qq && apt-get install -y -qq --no-install-recommends gnupg curl ca-certificates \
    && curl -fsSL https://www.mongodb.org/static/pgp/server-6.0.asc | gpg --dearmor -o /usr/share/keyrings/mongodb-server-6.0.gpg \
    && echo "deb [ signed-by=/usr/share/keyrings/mongodb-server-6.0.gpg ] https://repo.mongodb.org/apt/ubuntu focal/mongodb-org/6.0 multiverse" | tee /etc/apt/sources.list.d/mongodb-org-6.0.list \
    && apt-get update -qq && apt-get install -y -qq mongodb-org \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /data/db && chown mongodb:mongodb /data/db 2>/dev/null || true

RUN apt-get update -qq && apt-get install -y -qq curl ca-certificates && rm -rf /var/lib/apt/lists/* \
    && curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get install -y -qq nodejs && rm -rf /var/lib/apt/lists/*

COPY c06-node-db/package.json ./
RUN npm install --production

COPY c06-node-db/server.js c06-node-db/start.sh ./
RUN sed -i 's/\r$//' start.sh && chmod +x start.sh

RUN if [ ! -d /var/lib/mysql/mysql ]; then \
      /usr/sbin/mysqld --initialize-insecure --user=mysql 2>/dev/null || true; \
    fi

ENV NODE_ENV=production
ENV MYSQL_HOST=localhost
ENV MYSQL_USER=root
ENV MYSQL_PASSWORD=
ENV MYSQL_DB=bmpdb
ENV MONGO_URL=mongodb://localhost:27017
ENV MONGO_DB=snmpdb
ENV PORT=3000

EXPOSE 3000

CMD ["/bin/bash", "./start.sh"]
