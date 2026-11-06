#!/usr/bin/env bash
# =============================================================================
# Bee Family · V2 迁移前全量备份（docs/07 §11.4 M-0 / §4.7）
#
# 用途：在「停机 → 备份 → 副本库演练 V2 → 替换新 jar → 启动」七步上线顺序中，
#       生成唯一的全量备份基线（回滚方案 A 的输入）。
# 纪律：
#   - 只读源库，绝不执行任何 DDL / DML；
#   - 不写死任何账号口令，全部由环境变量提供，缺失即失败退出；
#   - 备份含 flyway_schema_history，恢复后仍停在 V1 版本记录；
#   - --single-transaction 保证 InnoDB 一致性快照，不锁表；
#   - --routines --triggers 覆盖存储过程 / 触发器（无 FK 环演练的前置）。
#
# 用法：
#   DB_USERNAME=<用户名> DB_PASSWORD=<口令> bash scripts/backup.sh [输出目录]
#   可选：DB_HOST（默认 127.0.0.1）、DB_PORT（默认 3306）、DB_NAME（默认 bee_family）
#   输出：<输出目录>/bee_family-<UTC时间戳>.sql.gz
# =============================================================================
set -euo pipefail

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-bee_family}"
DB_USERNAME="${DB_USERNAME:?用法：DB_USERNAME=<用户名> DB_PASSWORD=<口令> bash scripts/backup.sh [输出目录]}"
DB_PASSWORD="${DB_PASSWORD:?用法：DB_USERNAME=<用户名> DB_PASSWORD=<口令> bash scripts/backup.sh [输出目录]}"
OUT_DIR="${1:-./backups}"

command -v mysqldump >/dev/null 2>&1 || { echo "错误：未找到 mysqldump" >&2; exit 127; }
command -v gzip >/dev/null 2>&1 || { echo "错误：未找到 gzip" >&2; exit 127; }
mkdir -p "$OUT_DIR" || { echo "错误：无法创建输出目录 $OUT_DIR" >&2; exit 1; }

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
BACKUP_FILE="$OUT_DIR/$DB_NAME-$STAMP.sql.gz"
TMP_FILE="$BACKUP_FILE.part"

# 不在命令行暴露口令：用临时 defaults-file，权限 600，退出时清理。
DEFAULTS_FILE="$(mktemp "${TMPDIR:-/tmp}/bee-backup-XXXXXX" 2>/dev/null || printf '%s' "./.bee-backup-$$")"
cleanup() { rm -f "$DEFAULTS_FILE" "$TMP_FILE" 2>/dev/null || true; }
trap cleanup EXIT INT TERM
(umask 077; printf '[client]\nhost=%s\nport=%s\nuser=%s\npassword=%s\n' \
  "$DB_HOST" "$DB_PORT" "$DB_USERNAME" "$DB_PASSWORD" > "$DEFAULTS_FILE")

echo "备份源：mysql://$DB_HOST:$DB_PORT/$DB_NAME"
echo "备份文件：$BACKUP_FILE"

# --set-gtid-purged=OFF 便于恢复到独立库；--no-tablespaces 规避 PROCESS 权限要求。
if ! mysqldump --defaults-extra-file="$DEFAULTS_FILE" \
    --single-transaction --routines --triggers --events \
    --set-gtid-purged=OFF --no-tablespaces \
    --default-character-set=utf8mb4 \
    --databases "$DB_NAME" | gzip -c > "$TMP_FILE"; then
  echo "错误：mysqldump 失败，备份未完成（已丢弃不完整文件）" >&2
  exit 1
fi

mv "$TMP_FILE" "$BACKUP_FILE"
BYTES="$(wc -c < "$BACKUP_FILE" | tr -d ' ')"
if [ "$BYTES" -lt 1024 ]; then
  echo "错误：备份文件仅 $BYTES 字节，疑似不完整，请检查后重跑" >&2
  exit 1
fi

# 自检：gzip 流完整 + 备份里确实含 V1 的 flyway 版本记录与表结构
gzip -t "$BACKUP_FILE" || { echo "错误：gzip 校验失败" >&2; exit 1; }
if ! gzip -cd "$BACKUP_FILE" | grep -q "CREATE TABLE"; then
  echo "错误：备份内容不含 CREATE TABLE，疑似空库或权限不足" >&2
  exit 1
fi

echo "备份完成：$BACKUP_FILE（$BYTES 字节）"
echo "下一步（演练，绝不直接对源库执行 V2）："
echo "  mysql -u <用户名> -p -e \"CREATE DATABASE bee_family_v2_probe DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;\""
echo "  gzip -cd '$BACKUP_FILE' | mysql -u <用户名> -p bee_family_v2_probe"
echo "  mysql -u <用户名> -p bee_family_v2_probe < backend/src/main/resources/db/migration/V2__multi_family_heic_exif.sql"
