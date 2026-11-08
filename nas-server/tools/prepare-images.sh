#!/usr/bin/env bash
# 离线镜像准备：绕开飞牛 fnOS 失效的镜像加速源 docker.fnnas.com。
# 导出的是【已装好 pip 依赖的成品镜像】，NAS 端无需任何外网即可运行。
#
# 在【能联网的电脑】上（nas-server 目录）：
#   bash tools/prepare-images.sh export
#   scp -r offline-images/ user@NAS:/path/to/nas-server/
# 在【NAS】上：
#   bash tools/prepare-images.sh import
#   docker compose up -d --no-build
#
# 加速源可覆盖（该域名可能随时间失效，换一个即可）：
#   REGISTRY_PREFIX=docker.1panel.live bash tools/prepare-images.sh export
set -eu

cd "$(dirname "$0")/.."
ACTION="${1:-}"
PREFIX="${REGISTRY_PREFIX:-docker.1ms.run}"
OUT_DIR="${OUT_DIR:-offline-images}"
PMS_LOCAL="powerline-pms:local"

PY_SHORT="${BASE_IMAGE:-python:3.11-slim}"
NGINX_SHORT="${TILES_IMAGE:-nginx:1.27-alpine}"

# 镜像名已带仓库前缀（含 /）就原样用，否则补 <加速源>/library/
long_ref() {
  case "$1" in
    */*) printf '%s' "$1" ;;
    *)   printf '%s/library/%s' "$PREFIX" "$1" ;;
  esac
}

# tar 文件名：把 : 和 / 换成 -，避免路径分隔符
tar_name() { printf '%s.tar' "$(printf '%s' "$1" | tr ':/' '--')"; }

case "$ACTION" in
  export)
    mkdir -p "$OUT_DIR"
    : > "$OUT_DIR/images.txt"
    py_long="$(long_ref "$PY_SHORT")"
    nginx_long="$(long_ref "$NGINX_SHORT")"

    echo "==> 拉取基础镜像 $py_long"
    docker pull "$py_long"
    echo "==> 在本机构建成品镜像 $PMS_LOCAL（pip 依赖一并装进镜像，NAS 端不必联网）"
    docker build --build-arg BASE_IMAGE="$py_long" -t "$PMS_LOCAL" ./pms

    echo "==> 拉取 $nginx_long"
    docker pull "$nginx_long"

    docker save -o "$OUT_DIR/pms-local.tar" "$PMS_LOCAL"
    printf '%s %s %s\n' "pms-local.tar" "$PMS_LOCAL" "$PMS_LOCAL" >> "$OUT_DIR/images.txt"

    nginx_tar="$(tar_name "$NGINX_SHORT")"
    docker save -o "$OUT_DIR/$nginx_tar" "$nginx_long"
    printf '%s %s %s\n' "$nginx_tar" "$nginx_long" "$NGINX_SHORT" >> "$OUT_DIR/images.txt"

    echo
    echo "完成。把整个 offline-images/ 目录拷到 NAS 的 nas-server/ 下，然后执行："
    echo "  bash tools/prepare-images.sh import"
    echo "  docker compose up -d --no-build"
    ;;
  import)
    if [ ! -f "$OUT_DIR/images.txt" ]; then
      echo "!! 找不到 $OUT_DIR/images.txt，请先在能联网的机器上执行 export" >&2
      exit 1
    fi
    while read -r tar long short; do
      [ -z "$tar" ] && continue
      echo "==> 导入 $OUT_DIR/$tar"
      docker load -i "$OUT_DIR/$tar" < /dev/null
      if [ "$long" != "$short" ]; then
        echo "==> 打本地标签 $short（compose 直接命中本地镜像）"
        docker tag "$long" "$short" < /dev/null
      fi
    done < "$OUT_DIR/images.txt"
    echo
    echo "完成。现在执行： docker compose up -d --no-build"
    echo "--no-build 直接使用本地成品镜像，全程不访问镜像仓库、也不需要 pip 联网。"
    echo "以后要重新构建时再执行 docker compose up -d --build（那一步需要外网）。"
    ;;
  *)
    echo "用法: bash tools/prepare-images.sh export|import" >&2
    echo "可覆盖: REGISTRY_PREFIX=<加速源> BASE_IMAGE=<镜像> TILES_IMAGE=<镜像> OUT_DIR=<目录>" >&2
    exit 1
    ;;
esac
