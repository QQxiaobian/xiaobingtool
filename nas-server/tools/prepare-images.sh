#!/usr/bin/env bash
# 离线镜像准备：绕开飞牛 fnOS 失效的镜像加速源 docker.fnnas.com。
#
# 在【能联网的电脑】上：
#   bash tools/prepare-images.sh export
#   scp -r offline-images/ user@NAS:/path/to/nas-server/
# 在【NAS】上：
#   bash tools/prepare-images.sh import
#   DOCKER_BUILDKIT=0 docker compose up -d --build
#
# 加速源可覆盖（该域名可能随时间失效，换一个即可）：
#   REGISTRY_PREFIX=docker.1panel.live bash tools/prepare-images.sh export
set -eu

cd "$(dirname "$0")/.."
ACTION="${1:-}"
PREFIX="${REGISTRY_PREFIX:-docker.1ms.run}"
OUT_DIR="${OUT_DIR:-offline-images}"

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
    for short in "$PY_SHORT" "$NGINX_SHORT"; do
      long="$(long_ref "$short")"
      tar="$(tar_name "$short")"
      echo "==> 拉取 $long"
      docker pull "$long"
      echo "==> 导出 $OUT_DIR/$tar"
      docker save -o "$OUT_DIR/$tar" "$long"
      printf '%s %s %s\n' "$tar" "$long" "$short" >> "$OUT_DIR/images.txt"
    done
    echo
    echo "完成。把整个 offline-images/ 目录拷到 NAS 的 nas-server/ 下，然后执行："
    echo "  bash tools/prepare-images.sh import"
    echo "  DOCKER_BUILDKIT=0 docker compose up -d --build"
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
      echo "==> 打本地标签 $short（compose 直接命中本地镜像，不再访问加速源）"
      docker tag "$long" "$short" < /dev/null
    done < "$OUT_DIR/images.txt"
    echo
    echo "完成。现在执行： DOCKER_BUILDKIT=0 docker compose up -d --build"
    echo "若仍提示拉取 metadata，请在飞牛 Docker 面板 → 镜像仓库 → 仓库设置"
    echo "换成 https://docker.1ms.run 并重启 Docker 服务（或逐条 docker pull + docker tag）。"
    ;;
  *)
    echo "用法: bash tools/prepare-images.sh export|import" >&2
    echo "可覆盖: REGISTRY_PREFIX=<加速源> BASE_IMAGE=<镜像> TILES_IMAGE=<镜像> OUT_DIR=<目录>" >&2
    exit 1
    ;;
esac
