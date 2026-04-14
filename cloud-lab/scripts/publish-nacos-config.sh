#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${NACOS_BASE_URL:-http://127.0.0.1:8848/nacos/v1/cs/configs}"
GROUP="${NACOS_GROUP:-DEFAULT_GROUP}"
NAMESPACE="${NACOS_NAMESPACE:-public}"

publish() {
  local data_id="$1"
  local type="$2"
  local file="$3"

  curl -fsS -X POST "${BASE_URL}" \
    --data-urlencode "dataId=${data_id}" \
    --data-urlencode "group=${GROUP}" \
    --data-urlencode "tenant=${NAMESPACE}" \
    --data-urlencode "type=${type}" \
    --data-urlencode "content@${file}"
  printf '\n'
}

publish "stock-service.yaml" "yaml" "$(dirname "$0")/../nacos-config/stock-service.yaml"
publish "stock-service-sentinel-flow.json" "json" "$(dirname "$0")/../nacos-config/stock-service-sentinel-flow.json"
publish "stock-service-sentinel-degrade.json" "json" "$(dirname "$0")/../nacos-config/stock-service-sentinel-degrade.json"
publish "gateway-service-sentinel-gateway.json" "json" "$(dirname "$0")/../nacos-config/gateway-service-sentinel-gateway.json"
