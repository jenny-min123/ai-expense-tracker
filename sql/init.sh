#!/usr/bin/env bash
# 数据库初始化脚本
# 用法：DB_PASSWORD='你的密码' bash sql/init.sh
# 可选：MYSQL_ADMIN 指定管理员连接方式，默认 "sudo mysql"；
#       例如 root 认证方式特殊时：
#       DB_PASSWORD='...' MYSQL_ADMIN="mysql --protocol=socket -udebian-sys-maint" bash sql/init.sh
set -euo pipefail

: "${DB_PASSWORD:?请先设置 DB_PASSWORD 环境变量}"
MYSQL_ADMIN="${MYSQL_ADMIN:-sudo mysql}"

cd "$(dirname "$0")/.."

# 将 SQL 中的 __DB_PASSWORD__ 替换为环境变量值（单引号转义），再交给 mysql 执行
# shellcheck disable=SC2086
python3 - "$PWD/sql/init.sql" <<'PY' | $MYSQL_ADMIN --default-character-set=utf8mb4
import os, sys

pw = os.environ["DB_PASSWORD"].replace("'", "''")
with open(sys.argv[1], encoding="utf-8") as f:
    sys.stdout.write(f.read().replace("__DB_PASSWORD__", pw))
PY

echo "数据库初始化完成"
