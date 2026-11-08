#!/usr/bin/env bash
# 输电线路巡检 · NAS 服务端一键部署
# 用法： bash deploy.sh
set -eu

cd "$(dirname "$0")"
echo "==> 工作目录：$(pwd)"

if ! command -v docker >/dev/null 2>&1; then
  echo "!! 未找到 docker，请先在 NAS 套件中心安装 Docker / Container Manager" >&2
  exit 1
fi
if docker compose version >/dev/null 2>&1; then
  DC="docker compose"
elif command -v docker-compose >/dev/null 2>&1; then
  DC="docker-compose"
else
  echo "!! 未找到 docker compose，请升级 Docker 或安装独立 compose" >&2
  exit 1
fi

# 1) 瓦片目录 + 透明占位图（未下载的格子返回透明，不留灰块）
mkdir -p data/tiles/yn_img data/tiles/cn_img data/tiles/yn_vec
if [ ! -f data/tiles/blank.png ]; then
  echo 'iVBORw0KGgoAAAANSUhEUgAAAQAAAAEACAYAAABccqhmAAABFUlEQVR42u3BMQEAAADCoPVP7WsIoAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAeAMBPAAB2ClDBAAAAABJRU5ErkJggg==' \
    | base64 -d > data/tiles/blank.png
  echo "==> 已生成透明占位瓦片"
fi

# 2) 环境文件
if [ ! -f .env ]; then
  cp .env.example .env
  echo "==> 已生成 .env，请按需修改 NAS_LAN_IP / 端口 / TIANDITU_KEY"
fi

# 3) 启动
echo "==> 构建并启动容器..."
if ! $DC up -d --build; then
  echo
  echo "!! 构建/启动失败。" >&2
  echo "   若日志出现 docker.fnnas.com / no route to host / 401 Unauthorized，" >&2
  echo "   那是飞牛 fnOS 自带的镜像加速源不可用，与本工程代码无关：" >&2
  echo "   构建卡在拉取基础镜像元数据这一步，Dockerfile 一行都没执行到。" >&2
  echo "   任选其一处理：" >&2
  echo "     1) 飞牛 Docker 面板 → 镜像仓库 → 仓库设置，换成 https://docker.1ms.run 并重启 Docker 服务" >&2
  echo "     2) 在 .env 里设 BASE_IMAGE / TILES_IMAGE 指向可用加速源（见 .env.example）" >&2
  echo "     3) bash tools/prepare-images.sh import  离线导入成品镜像后用 --no-build 启动" >&2
  echo "     4) bash tools/run-local.sh             完全不用 Docker" >&2
  echo "   详见 README「镜像拉取失败」一节。" >&2
  exit 1
fi

# 4) 健康检查
echo "==> 等待服务就绪..."
for i in $(seq 1 20); do
  if curl -fsS "http://127.0.0.1:${PMS_PORT:-8080}/health" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

NAS_IP=$(grep -E '^NAS_LAN_IP=' .env | cut -d= -f2- | tr -d ' \r')
PMS_PORT=$(grep -E '^PMS_PORT=' .env | cut -d= -f2- | tr -d ' \r'); PMS_PORT=${PMS_PORT:-8080}
TILE_PORT=$(grep -E '^TILE_PORT=' .env | cut -d= -f2- | tr -d ' \r'); TILE_PORT=${TILE_PORT:-8090}

echo
echo "================ 部署完成 ================"
echo "健康检查： http://${NAS_IP}:${PMS_PORT}/health"
echo "瓦片服务： http://${NAS_IP}:${TILE_PORT}/tiles/0/0/0.png"
echo
echo "把下面两行写进 App 工程的 local.properties，然后重新打包 APK："
echo "  PMS_BASE_URL=http://${NAS_IP}:${PMS_PORT}/"
echo "  TILE_SERVER_URL=http://${NAS_IP}:${TILE_PORT}/tiles/{z}/{x}/{y}.png"
echo
echo "导入自己的台账： python3 tools/import_excel.py 台账.xlsx --replace"
echo "预下载瓦片：     python3 tools/fetch_tiles.py --tk <天地图key> --bbox <范围> --zmin 6 --zmax 14 --out data/tiles/yn_img"
echo "=========================================="
