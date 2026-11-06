#!/usr/bin/env bash
# =============================================================================
# 把 backend/lib/maven-repo 里的 vendored HEIC 依赖装进 Maven 本地仓库。
#
# 为什么需要这一步：pom.xml 用 <repositories> + file:// 引用 vendor 目录，
# 但 Maven 的 `-o`（offline）会禁用**所有** remote repository（file:// 也算），
# 只允许读「本地仓库里已带正确 _remote.repositories 标记」的 artifact。
# 全新环境首次直接跑 `mvn -o package` 会报：
#   Cannot access bee-vendor (file://...) in offline mode and the artifact ...
#   has not been downloaded from it before.
#
# 两条等价路径，任选其一：
#   A) 本项目脚本（无需联网、无需 Maven 插件解析）：bash scripts/bootstrap-vendor.sh
#   B) 先跑一次不带 -o 的构建（file:// 是本地文件系统，不访问外网）：
#      cd backend && mvn -DskipTests package
#
# 幂等：可重复执行；已存在的本地仓库副本会被覆盖为仓库内锁定的版本。
# 不删除任何文件（.lastUpdated 采用清空而非 unlink，兼容受限文件系统）。
# =============================================================================
set -euo pipefail

usage() {
  printf '%s\n' "用法: bash $0 [MAVEN_LOCAL_REPO]" >&2
  printf '%s\n' "  MAVEN_LOCAL_REPO 可选；也可设 MAVEN_REPO_LOCAL 环境变量。" >&2
  printf '%s\n' "  缺省时依次探测 ~/.m2/settings.xml 的 <localRepository>、~/.m2/repository。" >&2
}

if [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then
  usage
  exit 0
fi

script_dir="$(cd "$(dirname "$0")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
vendor_repo="$repo_root/backend/lib/maven-repo"
group_path="openize/openize-heic/26.5.1"
artifact="openize-heic-26.5.1"
repo_id="bee-vendor"

jar_src="$vendor_repo/$group_path/$artifact.jar"
pom_src="$vendor_repo/$group_path/$artifact.pom"
if [ ! -f "$jar_src" ] || [ ! -f "$pom_src" ]; then
  printf '错误：vendored 产物缺失，期望目录 %s\n' "$vendor_repo/$group_path" >&2
  exit 1
fi

# 探测 Maven 本地仓库：显式参数 > 环境变量 > settings.xml > 默认 ~/.m2/repository。
# `<localRepository>/path/to/local/repo</localRepository>` 是 Maven 模板里注释掉的示例值，
# 用 grep -v 排除；若本机 settings.xml 结构特殊，用第一个参数显式覆盖。
detect_local_repo() {
  if [ -n "${1:-}" ]; then
    printf '%s' "$1"
    return 0
  fi
  if [ -n "${MAVEN_REPO_LOCAL:-}" ]; then
    printf '%s' "$MAVEN_REPO_LOCAL"
    return 0
  fi
  local settings="${HOME}/.m2/settings.xml"
  if [ -f "$settings" ]; then
    local parsed
    parsed="$(grep -o '<localRepository>[^<]*</localRepository>' "$settings" 2>/dev/null \
      | grep -v '/path/to/' \
      | tail -n 1 \
      | sed -e 's/<[^>]*>//g' -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
    if [ -n "$parsed" ]; then
      printf '%s' "$parsed"
      return 0
    fi
  fi
  printf '%s' "${HOME}/.m2/repository"
}

local_repo="$(detect_local_repo "${1:-}")"
case "$local_repo" in
  '~'/*) local_repo="${HOME}${local_repo#'~'}" ;;
esac

dest="$local_repo/$group_path"
mkdir -p "$dest"
cp "$jar_src" "$dest/$artifact.jar"
cp "$pom_src" "$dest/$artifact.pom"

# Maven Resolver 内部元数据：标记这两个文件「已从 bee-vendor 下载过」，
# 否则 offline 模式仍会拒绝使用（实测 Maven 3.9.9 格式）。
printf '%s\n' \
  '#NOTE: This is a Maven Resolver internal implementation file, its format can be changed without prior notice.' \
  "$artifact.jar>$repo_id=" \
  "$artifact.pom>$repo_id=" > "$dest/_remote.repositories"

# 清掉之前 offline miss 留下的 .lastUpdated；用重定向清空而非 unlink。
for marker in "$dest"/*.lastUpdated; do
  [ -e "$marker" ] || continue
  : > "$marker"
done

printf 'vendored HEIC 依赖已安装到：%s\n' "$dest"
printf '版本：openize:openize-heic:26.5.1（sha256 见 backend/lib/COMMIT.lock）\n'
printf '现在可执行：cd backend && mvn -o package -DskipTests && mvn -o test\n'
