#!/usr/bin/env bash
# =============================================================================
# Bee Family · V2 迁移回滚方案 A · 整体回退（docs/07 §11.4 / §8.1 七步）
#
# 触发条件：V2 任一步失败 / 启动失败 / 冒烟不通过。
# 动作：停机（人工）→ DROP DATABASE → CREATE DATABASE → 导入全量备份
#       （含 flyway_schema_history，恢复到 V1）→ 换回旧 jar 启动 → 旧 schema 冒烟。
# 数据影响：停机窗口内零数据丢失；V2 期间产生的新申请 / 新家庭 / HEIC 照片随回退
#           一并撤销，需在恢复后由用户重做。
#
# 安全设计：
#   - 不写死口令；缺失环境变量立即失败；
#   - 默认 DRY-RUN，必须显式 CONFIRM=YES 才执行破坏性动作；
#   - 执行前二次核对备份文件可读、gzip 完整、含 CREATE TABLE；
#   - 绝不手工改 flyway_schema_history，绝不 ddl-auto:update；
#   - 只回滚 schema + 代码，不触碰 uploads/ 磁盘目录（孤儿文件由后续清理任务处理）。
#
# 用法：
#   DB_USERNAME=<用户名> DB_PASSWORD=<口令> bash scripts/rollback.sh <备份.sql.gz>          # 预演
#   DB_USERNAME=<用户名> DB_PASSWORD=<口令> CONFIRM=YES bash scripts/rollback.sh <备份.sql.gz>
#   可选：DB_HOST / DB_PORT / DB_NAME（默认 bee_family）/ RESTORE_PROBE（默认开启恢复演练）
# =============================================================================
set -euo pipefail

BACKUP_FILE="${1:?用法：DB_USERNAME=<用户名> DB_PASSWORD=<口令> [CONFIRM=YES] bash scripts/rollback.sh <备份.sql.gz>}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-bee_family}"
DB_USERNAME="${DB_USERNAME:?用法：DB_USERNAME=<用户名> DB_PASSWORD=<口令> bash scripts/rollback.sh <备份.sql.gz>}"
DB_PASSWORD="${DB_PASSWORD:?用法：DB_USERNAME=<用户名> DB_PASSWORD=<口令> bash scripts/rollback.sh <备份.sql.gz>}"
CONFIRM="${CONFIRM:-NO}"
RESTORE_PROBE="${RESTORE_PROBE:-YES}"

command -v mysql >/dev/null 2>&1 || { echo "错误：未找到 mysql 客户端" >&2; exit 127; }
command -v gzip >/dev/null 2>&1 || { echo "错误：未找到 gzip" >&2; exit 127; }
[ -r "$BACKUP_FILE" ] || { echo "错误：备份文件不可读：$BACKUP_FILE" >&2; exit 1; }

# 备份完整性自检：破坏性动作之前必须全部通过
gzip -t "$BACKUP_FILE" || { echo "错误：备份 gzip 校验失败，禁止回滚" >&2; exit 1; }
gzip -cd "$BACKUP_FILE" | grep -q "CREATE TABLE" || {
  echo "错误：备份不含 CREATE TABLE，禁止回滚" >&2; exit 1; }
gzip -cd "$BACKUP_FILE" | grep -q "flyway_schema_history" || {
  echo "错误：备份不含 flyway_schema_history，恢复后版本记录会丢失，禁止回滚" >&2; exit 1; }
BYTES="$(wc -c < "$BACKUP_FILE" | tr -d ' ')"
echo "备份校验通过：$BACKUP_FILE（$BYTES 字节）"

DEFAULTS_FILE="$(mktemp "${TMPDIR:-/tmp}/bee-rollback-XXXXXX" 2>/dev/null || printf '%s' "./.bee-rollback-$$")"
cleanup() { rm -f "$DEFAULTS_FILE" 2>/dev/null || true; }
trap cleanup EXIT INT TERM
(umask 077; printf '[client]\nhost=%s\nport=%s\nuser=%s\npassword=%s\n' \
  "$DB_HOST" "$DB_PORT" "$DB_USERNAME" "$DB_PASSWORD" > "$DEFAULTS_FILE")

my() { mysql --defaults-extra-file="$DEFAULTS_FILE" --default-character-set=utf8mb4 "$@"; }
PROBE_DB="${DB_NAME}_v2_restore"

echo "=============================================================="
echo "回滚目标库：mysql://$DB_HOST:$DB_PORT/$DB_NAME"
echo "恢复演练库：$PROBE_DB（独立库，不影响任何现有数据）"
echo "执行模式：$( [ "$CONFIRM" = "YES" ] && echo "实际执行" || echo "预演 DRY-RUN" )"
echo "=============================================================="
echo "前置人工步骤（脚本不代做）："
echo "  1) 停止应用进程，确认无新写入（单机部署，停机窗口内零数据丢失）"
echo "  2) 确认 V2 期间新产生的申请 / 新家庭 / HEIC 照片可撤销，并已告知用户"
echo "  3) 确认已保留 V1 版本的旧 jar"

if [ "$CONFIRM" != "YES" ]; then
  echo "预演结束：未做任何变更。确认无误后加 CONFIRM=YES 重跑。"
  exit 0
fi

# ---- 步骤 1：副本库实测恢复（§11.4「备份校验」：备份必须实测可恢复到独立库）----
if [ "$RESTORE_PROBE" = "YES" ]; then
  echo "[1/4] 独立库恢复演练：$PROBE_DB"
  my -e "DROP DATABASE IF EXISTS \`$PROBE_DB\`; CREATE DATABASE \`$PROBE_DB\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
  gzip -cd "$BACKUP_FILE" | my "$PROBE_DB"
  TABLES="$(my -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$PROBE_DB';")"
  FLYWAY="$(my -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$PROBE_DB' AND table_name='flyway_schema_history';")"
  echo "      恢复后表数=$TABLES，flyway_schema_history=$FLYWAY"
  [ "$FLYWAY" = "1" ] || { echo "错误：恢复演练缺 flyway_schema_history，禁止继续" >&2; exit 1; }
  V2_COLS="$(my -N -B -e "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='$PROBE_DB' AND table_name='family' AND column_name='family_code';")"
  echo "      family.family_code 列数=$V2_COLS（应为 0，证明备份是 V1 基线）"
  [ "$V2_COLS" = "0" ] || { echo "错误：备份已是 V2 结构，不能作为回滚基线" >&2; exit 1; }
  my -e "DROP DATABASE \`$PROBE_DB\`;"
  echo "      演练通过，独立库已清理"
else
  echo "[1/4] 已按 RESTORE_PROBE=NO 跳过恢复演练（不推荐）"
fi

# ---- 步骤 2：整体回退 ----
echo "[2/4] DROP + CREATE $DB_NAME"
my -e "DROP DATABASE \`$DB_NAME\`; CREATE DATABASE \`$DB_NAME\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"

echo "[3/4] 导入 V1 全量备份"
gzip -cd "$BACKUP_FILE" | my "$DB_NAME"

echo "[4/4] 回退后校验"
TABLES="$(my -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME';")"
INVITE="$(my -N -B -e "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='$DB_NAME' AND table_name='family' AND column_name='invite_code';")"
VERSION="$(my -N -B -e "SELECT MAX(version) FROM \`$DB_NAME\`.flyway_schema_history WHERE success=1;" 2>/dev/null || echo "unknown")"
echo "      表数=$TABLES  family.invite_code 列数=$INVITE  flyway 最大版本=$VERSION"
[ "$INVITE" = "1" ] || { echo "警告：未检测到 V1 的 invite_code 列，请人工核对" >&2; }

echo "数据库已回退到 V1。剩余人工步骤："
echo "  - 换回 V1 版本的旧 jar 启动（Flyway 会看到 V1 版本记录，不会重跑迁移）"
echo "  - 用旧 schema + 旧代码重新执行 docs/api-smoke.sh 冒烟"
echo "  - 磁盘 uploads/ 目录无需恢复；V2 期间新增的孤儿文件由后续清理任务处理"
