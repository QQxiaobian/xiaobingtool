#!/usr/bin/env bash
# 无 Docker 兜底方案：直接用 NAS / 主机上的 Python3 跑 Flask 服务。
# 8080 端口已内置 /tiles/ 静态瓦片路由，不跑 nginx 也能出图（内网性能足够）。
# App「服务器地址」仍填 NAS_IP:8080，配置方式完全不变。
#
# 用法： bash tools/run-local.sh            （前台运行，Ctrl+C 停止）
#        PIP_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple bash tools/run-local.sh
set -eu

cd "$(dirname "$0")/.."

PYTHON="${PYTHON:-python3}"
export PORT="${PORT:-8080}"
export PMS_DB="${PMS_DB:-$(pwd)/data/pms.db}"
export TILE_ROOT="${TILE_ROOT:-$(pwd)/data/tiles}"
export FILE_ROOT="${FILE_ROOT:-$(pwd)/data/files}"
export SEED_DEMO="${SEED_DEMO:-1}"
export API_TOKEN="${API_TOKEN:-}"
export TILE_PUBLIC_URL="${TILE_PUBLIC_URL:-}"
export TZ="${TZ:-Asia/Shanghai}"

mkdir -p "$(dirname "$PMS_DB")" "$TILE_ROOT" "$FILE_ROOT" \
         "$TILE_ROOT/yn_img" "$TILE_ROOT/cn_img" "$TILE_ROOT/yn_vec"

# 透明占位瓦片（与 deploy.sh 一致，避免地图边缘灰块）
if [ ! -f "$TILE_ROOT/blank.png" ]; then
  echo 'iVBORw0KGgoAAAANSUhEUgAAAQAAAAEACAYAAABccqhmAAABFUlEQVR42u3BMQEAAADCoPVP7WsIoAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAeAMBPAAB2ClDBAAAAABJRU5ErkJggg==' \
    | base64 -d > "$TILE_ROOT/blank.png"
  echo "==> 已生成透明占位瓦片"
fi

VENV="$(pwd)/.venv"
if [ ! -x "$VENV/bin/python" ]; then
  echo "==> 创建虚拟环境 $VENV"
  "$PYTHON" -m venv "$VENV"
fi
if ! "$VENV/bin/python" -c 'import flask, openpyxl' >/dev/null 2>&1; then
  echo "==> 安装依赖"
  if [ -n "${PIP_INDEX_URL:-}" ]; then
    "$VENV/bin/pip" install -i "$PIP_INDEX_URL" -r pms/requirements.txt
  else
    "$VENV/bin/pip" install -r pms/requirements.txt
  fi
fi

echo "==> 启动 http://0.0.0.0:${PORT}/health  （台账库 $PMS_DB）"
echo "    需要开机自启时，用 systemd / 飞牛「计划任务」指向本脚本即可。"
exec "$VENV/bin/python" pms/app.py
