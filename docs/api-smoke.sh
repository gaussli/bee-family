#!/usr/bin/env bash
#
# 蜜记 · Bee Family —— 后端接口冒烟测试
#
# 覆盖 42 个接口的「能通 + 状态码符合预期」，用于改动后的快速回归。
# 不做业务断言细节（那是 §10 验收脚本的职责），只看链路是否健康。
#
# 用法：
#   EMAIL=<你的邮箱> PASSWORD=<你的密码> docs/api-smoke.sh
#   BASE_URL=http://localhost:5173/api docs/api-smoke.sh   # 顺带验证 Vite 代理
#
# 说明：
#   - 口令**没有默认值**，必须由环境变量传入（仓库里不落任何口令）。
#   - EMAIL 可选，默认 mom@home.local；该账号不存在时会自动注册一个随机邮箱。
#
# 依赖：bash / curl / 以下之一用于解析 JSON：python3 或 jq
#       （两者都没有时退化为 grep/sed，可能不稳，脚本会打印提示）

set -uo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080/api}"
EMAIL="${EMAIL:-mom@home.local}"
PASSWORD="${PASSWORD:?用法：EMAIL=<你的邮箱> PASSWORD=<你的密码> docs/api-smoke.sh}"

PASS=0
FAIL=0
FAILED_CASES=()

TOKEN=""
BABY_ID=""
ALBUM_ID=""
PHOTO_ID=""
MILESTONE_ID=""
GROWTH_ID=""
UPLOAD_KEY=""

# ---------------------------------------------------------------------------
# JSON 解析后端探测
# ---------------------------------------------------------------------------
# 为什么不用 here-document 方式调用 python3：它需要创建一个临时文件，在受限环境
# （TMPDIR 不可写）里会直接失败：cannot create temp file for here document。
# 全部改为 `python3 -c '...'` 与 `jq -r`，不落任何临时文件。
if command -v python3 >/dev/null 2>&1; then
  JSON_TOOL="python3"
elif command -v jq >/dev/null 2>&1; then
  JSON_TOOL="jq"
else
  JSON_TOOL=""
  printf '%s\n' "提示：未找到 python3 / jq，JSON 解析退化为 grep 兜底（结果可能不稳）" >&2
fi

# 8×8 纯色 PNG 的 base64（与 python3 分支生成的字节完全一致），
# 供「没有 python3」的环境解码用，避免依赖仓库里的样例图。
SAMPLE_PNG_B64="iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAIAAABLbSncAAAAEUlEQVR42mN4MUsbK2IYWhIAF35rQbgFoVQAAAAASUVORK5CYII="

# 可写的临时文件路径：优先 mktemp，退化为 $TMPDIR，再退化为当前目录
new_tmp() {
  local name="$1" path
  path="$(mktemp -t "$name" 2>/dev/null)" || path=""
  if [ -z "$path" ] && [ ! -e "$path" ]; then
    path="${TMPDIR:-/tmp}/$name-$$"
    : >"$path" 2>/dev/null || path="./$name-$$"
    : >"$path" 2>/dev/null || path="/dev/null"
  fi
  printf '%s' "$path"
}

tmp_body="$(new_tmp beesmoke-body)"
SAMPLE_IMAGE="$(new_tmp beesmoke-image)"
trap 'rm -f "$tmp_body" "$SAMPLE_IMAGE" 2>/dev/null || true' EXIT

# ---------------------------------------------------------------------------
# 工具函数
# ---------------------------------------------------------------------------

color() {
  if [ -t 1 ]; then printf "\033[%sm%s\033[0m" "$1" "$2"; else printf "%s" "$2"; fi
}

ok() { PASS=$((PASS + 1)); printf "  %s %s\n" "$(color 32 "PASS")" "$1"; }

ng() {
  FAIL=$((FAIL + 1))
  FAILED_CASES+=("$1")
  printf "  %s %s  <-- %s\n" "$(color 31 "FAIL")" "$1" "$2"
}

# 从 JSON 的 data 对象里取字符串字段（$1=json $2=字段名）
json_str() {
  case "$JSON_TOOL" in
    python3)
      python3 -c 'import json, sys
try:
    data = json.loads(sys.argv[1]).get("data") or {}
    if isinstance(data, dict) and data.get(sys.argv[2]) is not None:
        sys.stdout.write(str(data[sys.argv[2]]))
except Exception:
    pass' "$1" "$2"
      ;;
    jq)
      printf '%s' "$1" | jq -r --arg k "$2" \
        'if (.data | type) == "object" then (.data[$k] // "" | tostring) else "" end' 2>/dev/null
      ;;
    *)
      printf '%s' "$1" | sed -n 's/.*"'"$2"'"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n 1
      ;;
  esac
}

# 取 data（数组，或 data[$2] 数组）第一项的 id
json_first_id() {
  case "$JSON_TOOL" in
    python3)
      python3 -c 'import json, sys
try:
    data = json.loads(sys.argv[1]).get("data")
    items = data if isinstance(data, list) else (data or {}).get(sys.argv[2] or "") or []
    if isinstance(items, list) and items and items[0].get("id") is not None:
        sys.stdout.write(str(items[0]["id"]))
except Exception:
    pass' "$1" "$2"
      ;;
    jq)
      printf '%s' "$1" | jq -r --arg k "$2" \
        '(if (.data | type) == "array" then .data else (.data[$k] // []) end) | (.[0].id // "") | tostring' \
        2>/dev/null
      ;;
    *)
      printf '%s' "$1" | sed -n 's/.*"id"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p' | head -n 1
      ;;
  esac
}

# 取 data[$2] 数组第一项的 $3 字段（用于上传结果的 thumbKey）
json_first_field() {
  case "$JSON_TOOL" in
    python3)
      python3 -c 'import json, sys
try:
    data = json.loads(sys.argv[1]).get("data") or {}
    items = data.get(sys.argv[2]) or []
    if isinstance(items, list) and items and items[0].get(sys.argv[3]) is not None:
        sys.stdout.write(str(items[0][sys.argv[3]]))
except Exception:
    pass' "$1" "$2" "$3"
      ;;
    jq)
      printf '%s' "$1" | jq -r --arg k "$2" --arg f "$3" \
        '(.data[$k] // []) | (.[0][$f] // "") | tostring' 2>/dev/null
      ;;
    *)
      printf '%s' "$1" | sed -n 's/.*"'"$3"'"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n 1
      ;;
  esac
}

# req METHOD PATH EXPECTED [JSON_BODY] [EXTRA_CURL_ARGS...]
req() {
  local method="$1" path="$2" expected="$3" body="${4:-}"
  shift 4 2>/dev/null || shift 3
  local args=(-s -o "$tmp_body" -w '%{http_code}' -X "$method" "$BASE_URL$path")

  if [ -n "$TOKEN" ]; then
    args+=(-H "Authorization: Bearer $TOKEN")
  fi
  if [ -n "$body" ]; then
    args+=(-H 'Content-Type: application/json' -d "$body")
  fi
  if [ "$#" -gt 0 ]; then
    args+=("$@")
  fi

  local status
  status="$(curl "${args[@]}" 2>/dev/null || echo 000)"
  local name="$method $path"

  if [ "$status" = "$expected" ]; then
    ok "$name → $status"
    return 0
  fi
  ng "$name → $status (期望 $expected)" "$(head -c 160 "$tmp_body" 2>/dev/null)"
  return 1
}

# 允许一组状态码中的任意一个（用于「未登录 401 / 资源 404」等语义等价场景）
req_any() {
  local method="$1" path="$2" expected_list="$3" body="${4:-}"
  shift 4 2>/dev/null || shift 3

  local args=(-s -o "$tmp_body" -w '%{http_code}' -X "$method" "$BASE_URL$path")
  if [ -n "$TOKEN" ]; then args+=(-H "Authorization: Bearer $TOKEN"); fi
  if [ -n "$body" ]; then args+=(-H 'Content-Type: application/json' -d "$body"); fi
  if [ "$#" -gt 0 ]; then args+=("$@"); fi

  local status name
  status="$(curl "${args[@]}" 2>/dev/null || echo 000)"
  name="$method $path"

  for candidate in $expected_list; do
    if [ "$status" = "$candidate" ]; then
      ok "$name → $status"
      return 0
    fi
  done
  ng "$name → $status (期望 $expected_list)" "$(head -c 160 "$tmp_body" 2>/dev/null)"
  return 1
}

# ---------------------------------------------------------------------------
# 0. 健康检查
# ---------------------------------------------------------------------------
printf "\n%s\n" "$(color 1 "蜜记接口冒烟测试") · $BASE_URL"

printf "\n[0] 健康检查\n"
# /health 未登录时可能是 200（放行）或 401（与其它 /api 一致要求鉴权），二者都视为链路正常
req_any GET /health "200 401"

# ---------------------------------------------------------------------------
# 1. 认证
# ---------------------------------------------------------------------------
printf "\n[1] 认证\n"
LOGIN_BODY="{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}"
if req POST /auth/login 200 "$LOGIN_BODY"; then
  TOKEN="$(json_str "$(cat "$tmp_body")" token)"
else
  # 首次运行：库里没有账号，注册一个（首个用户自动建家庭）
  RANDOM_MAIL="smoke-$(date +%s)@home.local"
  REG_BODY="{\"email\":\"$RANDOM_MAIL\",\"password\":\"$PASSWORD\",\"nickname\":\"冒烟测试\"}"
  if req POST /auth/register 200 "$REG_BODY"; then
    TOKEN="$(json_str "$(cat "$tmp_body")" token)"
    EMAIL="$RANDOM_MAIL"
  fi
fi

if [ -z "$TOKEN" ]; then
  printf "\n%s\n" "$(color 31 "登录/注册失败，后续用例无法执行")"
  printf "  %s\n" "$(head -c 200 "$tmp_body")"
  exit 1
fi

req GET /auth/me 200
req PATCH /auth/me 200 "{\"nickname\":\"冒烟测试\"}"
# 家庭信息挂在 /family 下（/auth/* 只有 register/login/logout/me/password）
req GET /family 200
req GET /family/members 200
req GET /family/invite-code 200
# 原密码故意传错，只验证「校验失败」这条分支：后端 40102（原密码不正确）按错误码表映射 HTTP 401；
# 新口令复用环境变量，不落任何字面量
WRONG_PWD_BODY="{\"oldPassword\":\"wrong-password\",\"newPassword\":\"$PASSWORD\"}"
req PUT /auth/password 401 "$WRONG_PWD_BODY"

# ---------------------------------------------------------------------------
# 2. 家庭
# ---------------------------------------------------------------------------
printf "\n[2] 家庭\n"
req GET /family 200
req GET /family/members 200
req PATCH /family 200 '{"name":"冒烟测试之家"}'
req POST /family/invite-code/reset 200
req_any DELETE /family/members/999999 "404 403 400"

# ---------------------------------------------------------------------------
# 3. 宝宝
# ---------------------------------------------------------------------------
printf "\n[3] 宝宝\n"
req GET /babies 200
BABY_BODY="{\"name\":\"冒烟宝宝\",\"gender\":\"female\",\"birthday\":\"2025-08-18\",\"birthHeightCm\":50.0,\"birthWeightKg\":3.35}"
if req POST /babies 200 "$BABY_BODY"; then
  BABY_ID="$(json_str "$(cat "$tmp_body")" id)"
fi
if [ -z "$BABY_ID" ]; then
  BABY_ID="$(json_first_id "$(cat "$tmp_body")" "")"
fi
req GET "/babies/$BABY_ID" 200
req PATCH "/babies/$BABY_ID" 200 "$BABY_BODY"

# ---------------------------------------------------------------------------
# 4. 成长记录
# ---------------------------------------------------------------------------
printf "\n[4] 成长记录\n"
GROWTH_BODY='{"recordedAt":"2026-10-24","heightCm":78.5,"weightKg":9.8,"headCm":46.2,"note":"冒烟测试"}'
# 成长记录是「按日期 upsert」语义，方法是 POST
if req POST "/babies/$BABY_ID/growth-records" 200 "$GROWTH_BODY"; then
  GROWTH_ID="$(json_str "$(cat "$tmp_body")" id)"
fi
req GET "/babies/$BABY_ID/growth-records" 200
req GET "/babies/$BABY_ID/growth-curve?range=6m" 200
req PATCH "/growth-records/$GROWTH_ID" 200 "$GROWTH_BODY"

# ---------------------------------------------------------------------------
# 5. 里程碑
# ---------------------------------------------------------------------------
printf "\n[5] 里程碑\n"
MILESTONE_BODY="{\"babyId\":$BABY_ID,\"title\":\"第一次独立走路\",\"description\":\"走了 6 步\",\"achievedAt\":\"2026-10-22\"}"
if req POST /milestones 200 "$MILESTONE_BODY"; then
  MILESTONE_ID="$(json_str "$(cat "$tmp_body")" id)"
fi
req GET /milestones 200
req PATCH "/milestones/$MILESTONE_ID" 200 "$MILESTONE_BODY"

# ---------------------------------------------------------------------------
# 6. 相册
# ---------------------------------------------------------------------------
printf "\n[6] 相册\n"
req GET /albums 200
if req POST /albums 200 '{"name":"冒烟相册","description":"自动化冒烟"}'; then
  ALBUM_ID="$(json_str "$(cat "$tmp_body")" id)"
fi
req GET "/albums/$ALBUM_ID" 200
req PATCH "/albums/$ALBUM_ID" 200 '{"description":"更新后的描述"}'

# ---------------------------------------------------------------------------
# 7. 照片（含上传）
# ---------------------------------------------------------------------------
printf "\n[7] 照片\n"
# 生成 8×8 纯色 PNG：python3 直接写 stdout，不落临时文件；没有 python3 时用内置 base64
if [ "$JSON_TOOL" = "python3" ]; then
  python3 -c 'import struct, sys, zlib


def chunk(tag, data):
    return (struct.pack(">I", len(data)) + tag + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))


width = height = 8
raw = b"".join(b"\x00" + b"\xE8\x9A\x2B" * width for _ in range(height))
png = b"\x89PNG\r\n\x1a\n"
png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
png += chunk(b"IDAT", zlib.compress(raw, 9))
png += chunk(b"IEND", b"")
sys.stdout.buffer.write(png)' >"$SAMPLE_IMAGE" 2>/dev/null
else
  printf '%s' "$SAMPLE_PNG_B64" | base64 -d >"$SAMPLE_IMAGE" 2>/dev/null \
    || printf '%s' "$SAMPLE_PNG_B64" | base64 -D >"$SAMPLE_IMAGE" 2>/dev/null
fi

if req POST /photos/upload 200 "" -F "files=@$SAMPLE_IMAGE;type=image/png" -F "albumId=$ALBUM_ID"; then
  PHOTO_ID="$(json_first_id "$(cat "$tmp_body")" successes)"
  UPLOAD_KEY="$(json_first_field "$(cat "$tmp_body")" successes thumbKey)"
fi

req GET "/photos?albumId=$ALBUM_ID&page=0&size=10" 200
req GET "/photos/$PHOTO_ID" 200
req PATCH "/photos/$PHOTO_ID" 200 '{"title":"冒烟照片","tags":["冒烟"]}'
req POST /photos/batch-move 200 "{\"photoIds\":[$PHOTO_ID],\"albumId\":null}"
req POST /photos/batch-delete 200 "{\"photoIds\":[$PHOTO_ID]}"

# ---------------------------------------------------------------------------
# 8. 时间轴 / 搜索 / 标签 / 统计
# ---------------------------------------------------------------------------
printf "\n[8] 时间轴 / 搜索 / 统计\n"
req GET "/timeline?page=0&size=24" 200
req GET "/search?q=%E5%86%92%E7%83%9F&type=all&limit=10" 200
req GET "/tags" 200
req GET /stats/overview 200

# ---------------------------------------------------------------------------
# 9. 文件与鉴权
# ---------------------------------------------------------------------------
printf "\n[9] 文件与鉴权\n"
if [ -n "$UPLOAD_KEY" ]; then
  # 图片走 ?token=，不带 Authorization 头
  status="$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/files/$UPLOAD_KEY?token=$TOKEN")"
  if [ "$status" = "200" ]; then ok "GET /files/{key}?token=… → 200"; else ng "GET /files/{key}?token=… → $status" "期望 200"; fi
else
  ng "GET /files/{key}?token=…" "上传未成功，跳过"
fi

SAVED_TOKEN="$TOKEN"
TOKEN=""
req GET /auth/me 401
TOKEN="$SAVED_TOKEN"

# ---------------------------------------------------------------------------
# 10. 清理（删除前面创建的数据，避免污染数据库）
# ---------------------------------------------------------------------------
printf "\n[10] 清理\n"
req DELETE "/milestones/$MILESTONE_ID" 200
req DELETE "/growth-records/$GROWTH_ID" 200
req DELETE "/albums/$ALBUM_ID?photoStrategy=move" 200
req DELETE "/babies/$BABY_ID" 200

# ---------------------------------------------------------------------------
# 汇总
# ---------------------------------------------------------------------------
TOTAL=$((PASS + FAIL))
printf "\n%s\n" "──────────────────────────────────────────────"
printf "  合计 %d 项：%s  %s\n" "$TOTAL" "$(color 32 "$PASS PASS")" "$(color 31 "$FAIL FAIL")"
if [ "$FAIL" -gt 0 ]; then
  printf "  失败用例：\n"
  for case_name in "${FAILED_CASES[@]}"; do
    printf "    - %s\n" "$case_name"
  done
  exit 1
fi
printf "  %s\n" "$(color 32 "全部通过")"
