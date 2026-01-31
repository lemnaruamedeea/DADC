#!/usr/bin/env bash
set -euo pipefail
echo "[C02] Starting JMS Broker (ActiveMQ) on 61616..."
/opt/activemq/bin/activemq start || true
sleep 8
echo "[C02] Starting Apache TomEE 10 on 8080..."
exec /opt/tomee/bin/catalina.sh run
