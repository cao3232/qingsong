#!/usr/bin/env bash
# 导出通用配置表数据(无隐私),会话/消息/复盘表绝不导出
set -euo pipefail

USER="root"
PASSWORD=""
DATABASE="big_event"
HOST="127.0.0.1"
PORT="3306"
OUT_FILE="sql/data-backup.sql"

while [[ $# -gt 0 ]]; do
  case "$1" in
    -u|--user) USER="$2"; shift 2;;
    -p|--password) PASSWORD="$2"; shift 2;;
    -h|--host) HOST="$2"; shift 2;;
    -P|--port) PORT="$2"; shift 2;;
    -o|--out) OUT_FILE="$2"; shift 2;;
    *) DATABASE="$1"; shift;;
  esac
done

MYSQL_PWD="$PASSWORD" mysqldump \
  --host="$HOST" --port="$PORT" --user="$USER" \
  --no-create-info --skip-comments --skip-add-locks \
  "$DATABASE" role role_phrases model_source model_config \
  > "$OUT_FILE"

echo "已导出配置表数据到 $OUT_FILE(注意检查 model_source.api_key 是否需脱敏)"
