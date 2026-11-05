#!/usr/bin/env bash
#
# 蜜记 · Bee Family —— 后端接口冒烟测试
#
# 覆盖 42 个接口的「能通 + 状态码符合预期」，用于改动后的快速回归。
# 不做业务断言细节（那是 §10 验收脚本的职责），只看链路是否健康。
#
# 用法：
#   docs/api-smoke.sh                       # 默认 http://localhost:8080/api
#   BASE_URL=http://localhost:5173/api docs/api-smoke.sh   # 顺带验证 Vite 代理
#   EMAIL=mom@home.local PASSWORD=Bee123456 docs/api-smoke.sh
#
# 依赖：bash / curl / python3（仅用于生成随机邮箱与解析 id）

set -uo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080/api}"
EMAIL="${EMAIL:-mom@home.local}"
PASSWORD="${PASSWORD:-Bee123456}"

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

tmp_body="$(mktemp -t beesmoke-body.XXXXXX)"
tmp_head="$(mktemp -t beesmoke-head.XXXXXX)"
trap 'rm -f "$tmp_body" "$tmp_head"' EXIT

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

# 从 JSON 里取字符串字段（不依赖 jq）
json_str() {
  python3 - "$1" "$2" <<'PY'
import json, sys
raw, key = sys.argv[1], sys.argv[2]
try:
    data = json.loads(raw)
    value = data.get("data") or {}
    if key in value:
        print(value[key])
except Exception:
    pass
PY
}

# 取 data 下某个数组的第一项 id
json_first_id() {
  python3 - "$1" "$2" <<'PY'
import json, sys
raw, key = sys.argv[1], sys.argv[2]
try:
    data = json.loads(raw).get("data")
    items = data
    if isinstance(data, dict):
        items = data.get(key) or []
    if isinstance(items, list) and items:
        print(items[0].get("id", ""))
except Exception:
    pass
PY
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
req PATCH /auth/profile 200 "{\"nickname\":\"冒烟测试\"}"
req GET /auth/family 200
req GET /auth/members 200
req GET /auth/invite-code 200
req POST /auth/password 400 '{"oldPassword":"wrong-password","newPassword":"Bee123456"}'

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
req PUT "/babies/$BABY_ID" 200 "$BABY_BODY"

# ---------------------------------------------------------------------------
# 4. 成长记录
# ---------------------------------------------------------------------------
printf "\n[4] 成长记录\n"
GROWTH_BODY='{"recordedAt":"2026-10-24","heightCm":78.5,"weightKg":9.8,"headCm":46.2,"note":"冒烟测试"}'
if req PUT "/babies/$BABY_ID/growth-records" 200 "$GROWTH_BODY"; then
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
SAMPLE_IMAGE="$(mktemp -t beesmoke.XXXXXX).png"
python3 - "$SAMPLE_IMAGE" <<'PY'
import struct, sys, zlib

# 生成 8×8 纯色 PNG，避免依赖仓库里的样例图
def chunk(tag, data):
    return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xFFFFFFFF)

width = height = 8
raw = b''.join(b'\x00' + b'\xE8\x9A\x2B' * width for _ in range(height))
png = b'\x89PNG\r\n\x1a\n'
png += chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 2, 0, 0, 0))
png += chunk(b'IDAT', zlib.compress(raw, 9))
png += chunk(b'IEND', b'')
open(sys.argv[1], 'wb').write(png)
PY

if req POST /photos/upload 200 "" -F "files=@$SAMPLE_IMAGE;type=image/png" -F "albumId=$ALBUM_ID"; then
  PHOTO_ID="$(json_first_id "$(cat "$tmp_body")" successes)"
  UPLOAD_KEY="$(python3 - "$(cat "$tmp_body")" <<'PY'
import json, sys
try:
    data = json.loads(sys.argv[1]).get('data') or {}
    items = data.get('successes') or []
    if items:
        print(items[0].get('thumbKey') or '')
except Exception:
    pass
PY
)"
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
