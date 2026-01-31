#!/bin/bash
set -e
export MYSQL_HOST="${MYSQL_HOST:-localhost}"
export MYSQL_USER="${MYSQL_USER:-root}"
export MYSQL_PASSWORD="${MYSQL_PASSWORD:-}"
export MYSQL_DB="${MYSQL_DB:-bmpdb}"

if command -v mysqld_safe &>/dev/null; then
  echo "[C06] Starting MySQL..."
  mysqld_safe --user=mysql --datadir=/var/lib/mysql &
elif command -v mysqld &>/dev/null; then
  /usr/sbin/mysqld --user=mysql &
fi

if command -v mongod &>/dev/null; then
  echo "[C06] Starting MongoDB..."
  mkdir -p /data/db
  mongod --bind_ip_all --dbpath=/data/db --fork --logpath=/tmp/mongod.log 2>/dev/null || mongod --bind_ip_all --dbpath=/data/db --fork --logpath=/tmp/mongod.log || true
fi

export MYSQL_SOCKET="${MYSQL_SOCKET:-/var/run/mysqld/mysqld.sock}"
for i in $(seq 1 60); do
  if [ -S "$MYSQL_SOCKET" ] 2>/dev/null; then
    if mysql -S "$MYSQL_SOCKET" -u"$MYSQL_USER" -e "SELECT 1" 2>/dev/null; then
      mysql -S "$MYSQL_SOCKET" -u"$MYSQL_USER" -e "CREATE DATABASE IF NOT EXISTS $MYSQL_DB" 2>/dev/null || true
      echo "[C06] MySQL ready"
      break
    fi
  fi
  [ $i -eq 60 ] && echo "[C06] WARN: MySQL not ready after 60 tries"
  sleep 2
done

for i in $(seq 1 30); do
  if (command -v mongosh &>/dev/null && mongosh --quiet --eval "db.runCommand({ping:1})" 2>/dev/null) || (command -v mongo &>/dev/null && mongo --quiet --eval "db.runCommand({ping:1})" 2>/dev/null); then
    echo "[C06] MongoDB ready"
    break
  fi
  sleep 2
done

exec node server.js
