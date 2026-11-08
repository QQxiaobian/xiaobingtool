# NAS 自建地图服务器 / 线路服务器

给「输电线路智能巡检」App 用的内网服务端。部署在群晖、威联通、绿联、极空间或任意 Docker 主机上，
手机连同一局域网（或 VPN）即可访问，不再依赖公网天地图额度与 PMS 外网。

## 一、要建的是哪三个服务

App 里已经预留了两个地址（`app/build.gradle.kts` 的 `buildConfigField`），一一对应：

| 服务 | 端口 | App 配置项 | 作用 |
|---|---|---|---|
| **瓦片服务**（nginx 静态） | 8090 | `TILE_SERVER_URL` | 卫星影像 / 矢量 / 路网 XYZ 瓦片 |
| **线路台账服务**（Flask REST） | 8080 | `PMS_BASE_URL` | 线路、杆塔、交叉跨越、导地线、运维单位、原始文件库 |

两个容器：`powerline-tiles`（纯静态，性能好）、`powerline-pms`（REST + 瓦片兜底 + `/api/files`）。
> **位置共享 WebSocket 已移除**：App 1.5.0 起不再有位置共享功能，服务端 `/ws/location` 与
> `flask-sock` / `simple-websocket` 依赖一并删除。本工程只提供 REST、瓦片、原始文件三类能力。
台账存 SQLite `data/pms.db`，瓦片就是 `data/tiles/` 下的普通文件，**备份等于打包 `data/` 目录**。

> **1.4.0 端口对齐**：App 打包默认地址改为 `192.168.1.12:8902`（单端口 REST / 瓦片 / 文件库），
> 因此本工程 `.env` 的 `PMS_PORT` 默认同步为 `8902`。下文示例里的 `8080` 是自定义端口写法，
> 沿用 8080 时把 `.env` 改回 `PMS_PORT=8080`，并在 App「设置 → 服务器地址」里同步即可，
> 服务端代码无需任何改动。

> 接口不是随便定的，全部对齐 App 源码：`data/remote/PmsApi.kt`、`PmsModels.kt`、
> `ui/map/Basemap.kt`（`serverTileUrl` 会拼出 `tiles/yn_img/`、`tiles/cn_img/`、`tiles/yn_vec/`）、
> `data/remote/model/ServerFileModels.kt`。按本工程部署即可直接连通，无需改 App 代码。

### REST 接口清单

| 方法 | 路径 | 返回 |
|---|---|---|
| GET | `/api/lines?unit=&voltage=&keyword=` | `PmsLineDto[]` |
| GET | `/api/towers?lineId=` | `PmsTowerDto[]` |
| GET | `/api/units` | `{"units":[...]}` |
| GET | `/api/import/pms-ledger?lineIds=a,b&includeTowers=true...` | `PmsLedgerBundle`（4 个数组） |
| POST | `/api/import/pms-ledger` | `ImportAckDto`（按主键 UPSERT） |
| GET | `/health` | 服务状态与统计（1.2.0 起含 `datasets`、1.3.0 起含 `files` 数量） |
| GET | `/api/datasets` | `DatasetMetaDto[]`（仅元数据，传输量小） |
| GET | `/api/datasets/{id}` | 完整 `DatasetBundleDto`（图层 + 要素 + 线路台账） |
| GET | `/api/datasets/{id}/download` | 下载为 `.json` 文件 |
| POST | `/api/datasets` | 上传 / 覆盖数据集（同 id 递增 version） |
| POST | `/api/datasets/import` | 上传本地 JSON 文件导入 |
| DELETE | `/api/datasets/{id}` | 删除数据集 |
| GET | `/api/files` | `ServerFileDto[]`（扫描 files 目录，仅元数据） |
| GET | `/api/files/{id}/download` | 下载原始文件字节（KML/KMZ/GeoJSON/GPX/SHP） |
| POST | `/api/files` | 上传原始文件（multipart `file`；中文名走 `X-File-Name`） |
| DELETE | `/api/files/{id}` | 删除服务器文件 |

### 数据集服务（1.2.0 新增）

App「设置 → 自定义地图与数据 → 数据同步」可以把**专题图层 + 要素**、**线路 / 杆塔台账**
打包上传，也可以浏览、下载、删除服务器上的数据集，或导出成本地 JSON 文件离线流转。

- 数据落在 SQLite 的 `datasets` 表（`/data/pms.db`，跟台账同一个卷，容器重建不丢），
  `payload` 字段保存完整 `powerline-dataset/1` JSON；**不需要额外端口**，仍走 8080 的 `PMS_BASE_URL`。
- 同 `id` 覆盖并自动递增 `version`；列表接口只返回元数据（名称 / 统计 / 时间 / 大小），不传要素。
- 服务端与 App 导出文件格式完全一致，因此 `App 导出 JSON → 拷到电脑 → 命令行上传` 是可行的。

命令行工具（`tools/dataset_cli.py`，只用 Python 标准库）：

```bash
python3 tools/dataset_cli.py --base http://192.168.1.10:8080 list
python3 tools/dataset_cli.py download DS-xxxxxxxx -o 备份.json
python3 tools/dataset_cli.py upload 备份.json
python3 tools/dataset_cli.py delete DS-xxxxxxxx
# 服务端开启 API_TOKEN 时加 --token <token>
```

也可直接用浏览器打开 `http://<NAS>:8080/api/datasets/<id>/download` 下载 JSON。

### 原始文件库（1.3.0 新增）

**这是「直接加载服务器上的 KML」的入口**：把 `KML / KMZ / GeoJSON / JSON / GPX / SHP / ZIP`
拷进 NAS 的 `nas-server/data/files/` 目录（容器内 `/data/files`，可用 `FILE_ROOT` 环境变量改），
服务端会自动扫描，App「设置 → 自定义地图与数据 → 服务器文件」即可列出并下载导入。
**不需要先在 App 里上传，也不需要额外端口**，仍走 8080 的 `PMS_BASE_URL`。

- 扩展名白名单：`.kml .kmz .geojson .json .gpx .shp .zip`；单文件上限 200 MB。
- `id` = 文件名的 SHA-256 前 16 位，稳定且 ASCII 安全，中文文件名不会破坏下载 URL。
- 上传时真实（可能含中文）文件名放在 `X-File-Name`（UTF-8 URL 编码），
  避免 OkHttp 的 multipart header 在 Werkzeug 侧乱码；multipart 的 `filename` 用 ASCII 占位。
- `/health` 新增 `files` / `fileRoot` 字段，`version` 升级为 `1.3`。
- 与 `datasets` 表的结构化 JSON 数据包互不冲突，两者可同时使用。

命令行工具（`tools/file_cli.py`，只用 Python 标准库）：

```bash
python3 tools/file_cli.py --base http://192.168.1.10:8080 list
python3 tools/file_cli.py upload 行政区划.kml
python3 tools/file_cli.py download <id> -o 行政区划.kml
python3 tools/file_cli.py delete <id>
# 服务端开启 API_TOKEN 时加 --token <token>
```

> 升级：用新版 `pms/app.py` 与 `tools/file_cli.py` 覆盖 NAS 后执行
> `docker compose up -d --build`，确认 `/health` 返回 `"version": "1.3"` 与 `files` 字段即可。
> 老的台账与数据集数据不受影响（无需改数据库表）。

## 二、部署（3 步）

**前置条件**

1. NAS 已装 Docker / Container Manager，且**给 NAS 绑定固定局域网 IP**（路由器 DHCP 保留），
   例如 `192.168.1.10`；防火墙放行 8080、8090。
2. 手机与 NAS 同一内网；Android 端已配置 `usesCleartextTraffic="true"`，HTTP/WS 明文可用，不必上证书。
3. 若是群晖：把 `nas-server` 整个目录放进 `/docker/powerline/`，用「项目 → 新增 → 从 docker-compose.yml 创建」，
   或 SSH 后执行 `bash deploy.sh`。
4. **Docker 能拉到基础镜像**（`python:3.11-slim`、`nginx:1.27-alpine`）。飞牛 fnOS 自带的
   加速源 `docker.fnnas.com` 经常失效，若构建卡在「load metadata」请看下一节。

```bash
cd nas-server
cp .env.example .env      # 改 NAS_LAN_IP，可选 TIANDITU_KEY
bash deploy.sh            # 生成占位瓦片、构建、启动、健康检查
docker compose logs -f pms
```

部署脚本最后会**直接打印两行 App 配置**，照抄即可。

### 2.5 镜像拉取失败（飞牛 fnOS 自带加速源不可用）

**典型报错**：

```
#3 [internal] load metadata for docker.io/library/python:3.11-slim
#3 ERROR: Head "https://docker.fnnas.com/v2/library/python/manifests/3.11-slim?ns=docker.io":
          dial tcp 171.15.36.24:443: connect: no route to host
failed to solve: python:3.11-slim: failed to resolve source metadata
```

**这不是本工程的代码问题**。构建停在第 3 步「解析基础镜像元数据」，连 `Dockerfile`
第 2 行都还没执行。飞牛 fnOS 的 Docker 守护进程把所有 `docker.io` 请求代理到自带的
加速域名 `docker.fnnas.com`（`171.15.36.24`）；当它不可达（`no route to host`）或对
官方库镜像返回 `401 Unauthorized` 时，构建就在这里中断。

> 顺带提醒：解决基础镜像后，`tiles` 容器的 `nginx:1.27-alpine` 和构建里的
> `pip install` 同样要走外网，需要一并可用，否则会在下一步继续失败。

四条路，按推荐顺序：

**① 换加速源（最省事，一次解决）**

飞牛桌面端：**Docker → 镜像仓库 → 仓库设置**，把 `docker.fnnas.com` 换成
`https://docker.1ms.run`（备用 `https://docker.1panel.live`、阿里云「容器镜像服务」
给你的个人加速器地址），然后在 Docker 面板右上角**停止并重启 Docker 服务**，重新
`docker compose up -d --build`。命令行方式是改 `/etc/docker/daemon.json` 的
`registry-mirrors` 后重启 docker。可先在 NAS 上自测链路：

```bash
curl -I https://docker.1ms.run/v2/          # 返回 401 即代表链路通（401 是正常鉴权响应）
docker pull docker.1ms.run/library/python:3.11-slim
```

**② 只改 `.env`，不改源码**

工程已把镜像名做成可配置项，在 `.env` 里填两行即可：

```properties
BASE_IMAGE=docker.1ms.run/library/python:3.11-slim
TILES_IMAGE=docker.1ms.run/library/nginx:1.27-alpine
# pip 装不上时再加：
PIP_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple
```

`BASE_IMAGE` 会作为 `--build-arg` 传给 `Dockerfile` 的 `FROM`，`TILES_IMAGE` 替换
nginx 镜像；默认值不变，不影响其他 NAS。也可用
`docker compose build --build-arg BASE_IMAGE=...` 临时覆盖。

**③ 完全离线：导入镜像再构建**

NAS 没有外网时用这个。在能联网的电脑上导出，NAS 上导入并打回本地标签，
compose 会直接命中本地镜像而不再访问加速源：

```bash
# 能联网的机器（nas-server 目录下）
bash tools/prepare-images.sh export
scp -r offline-images/ user@NAS:/path/to/nas-server/

# NAS 上
bash tools/prepare-images.sh import
docker compose up -d --no-build
```

脚本在联网机器上会先 `docker build` 出**成品镜像**（`pip` 依赖已装进镜像），
NAS 端导入后用 `--no-build` 直接跑本地镜像，全程不访问任何仓库、也不需要 pip 联网，
适合 NAS 完全没有外网的场景。`pms` 服务在 compose 里固定使用 `powerline-pms:local`
标签，`tiles` 使用 `nginx:1.27-alpine`，导入脚本会自动打好这两个标签。
脚本的加速源可用 `REGISTRY_PREFIX=docker.1panel.live` 覆盖；以后要重新构建再执行
`docker compose up -d --build`（那一步需要外网）。

**④ 不用 Docker（最终兜底）**

Flask 服务依赖极少，且 8080 已内置 `/tiles/` 瓦片路由，不跑 nginx 也能出图：

```bash
bash tools/run-local.sh          # 前台运行，Ctrl+C 停止
# 国内源： PIP_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple bash tools/run-local.sh
```

脚本会建好 `data/` 目录、透明占位瓦片与 `.venv` 并启动。App 里仍填 `NAS_IP:8080`，
`TILE_PUBLIC_URL` 保持留空（走 8080 内置瓦片）。需要开机自启就把该脚本挂到
systemd 或飞牛「计划任务」。

## 三、瓦片从哪来（三选一）

目录必须长成 `data/tiles/<子目录>/<z>/<x>/<y>.png`：

```
data/tiles/
├── {z}/{x}/{y}.png        # 默认 XYZ（serverTileUrl 的 base）
├── yn_img/{z}/{x}/{y}.png # 云南卫星（服务端版）
├── cn_img/{z}/{x}/{y}.png # 全国卫星（服务端版）
└── yn_vec/{z}/{x}/{y}.png # 云南矢量（服务端版）
```

1. **App 自带离线包**：在 App「设置 → 地图数据」区域预下载并导出 zip，解压到对应子目录即可（已是 `z/x/y.png`）。
2. **从天地图预下载**（推荐先把常用巡检区域缓存下来）：

   ```bash
   python3 tools/fetch_tiles.py --tk <天地图key> \
       --bbox 102.5,24.8,103.2,25.6 --zmin 6 --zmax 14 \
       --out data/tiles/yn_img --layer img_w
   ```

   `vec_w`→`yn_vec`，全国影像换更大 bbox 输出到 `cn_img`。级别每 +1 瓦片数翻 4 倍，
   昆明城区 6~14 级约几万张、几个 GB，先小范围试跑。已下载的会跳过，可断点续传。
3. **mbtiles 解包**：用 `mb-util` 把 mbtiles 导出成 XYZ，注意 TMS 的 y 轴要翻转（App 的
   `OfflineTileStore.kt` 也做了这件事）。

未下载到的格子由 nginx 返回 256×256 透明图（`blank.png`，部署脚本自动生成），
不会出现灰块或刷 404。

> **合规**：天地图瓦片有日请求额度与版权要求，本方案只用于单位内网缓存，请勿公开分发、
> 转售或对外提供瓦片服务，并遵守天地图服务条款。单位已有自有影像/正射影像时，切成 XYZ 直接放进来最稳妥。

## 四、导入自己的线路台账

```bash
python3 tools/import_excel.py --demo              # 生成可编辑的示例 Excel
python3 tools/import_excel.py 台账.xlsx --replace # 覆盖导入
```

Excel 需要 `线路` 和 `杆塔` 两个 Sheet，列名见文件头注释。字段与 App 的
`PmsModels.kt` 完全对应。导入不需要重启容器（SQLite WAL，服务端每次请求重开连接）。

也可让 App 端「线路管理 → PMS 内网导入」回传台账：`POST /api/import/pms-ledger` 会按
线路名 / 杆塔号 UPSERT，实现多端数据回写。

首次启动会写入 3 条示例线路方便验收，正式导入后把 `.env` 里的 `SEED_DEMO` 改为 `0`。

## 五、App 端配置（v1.1.1 起可在 App 里直接改）

### 方式 A：现场填写，不用重新打包（推荐）

**设置 → 服务器（自建 NAS）→ 服务器地址**，只填一个主地址：

```
192.168.1.10:8080
```

点「**测试连接**」→ App 会请求 `http://192.168.1.10:8080/health`，成功后自动把
服务端 `urls` 里返回的两个推荐地址填入高级输入框 → 点「**保存**」立即生效。

- 台账接口、瓦片地址改完立即生效，不用重启 App
- 瓦片地址变化会自动重建底图，镜头位置保持不变
- 高级里可单独覆盖两个地址（例如把瓦片改到 nginx 的 8090）
- 「恢复默认」回到打包时 `local.properties` 里的地址

### NAS 端要做什么？

**几乎不用改**——App 只是把请求目标换了个地址，服务端代码不需要为每个客户端单独配置。
只需保证三件事：

1. **NAS 有固定局域网 IP**（路由器 DHCP 保留），手机与 NAS 同网；换 IP 后在 App 里改一下即可。
2. **8080 端口可达**，且 `GET /health` 能返回 200——App 的「测试连接」就是打这个接口。
   若用反代，请透传 `X-Forwarded-Prefix`，`/health` 会据此自动带上子路径前缀。
3. **防火墙放行 8080**；要用独立瓦片服务再放行 8090。

`/health` 返回的 `urls` 是用**请求方实际访问的 host** 生成的（Flask `request.host_url`），
所以容器里**不需要预先写死 NAS 的 IP**，换了 IP、换了域名、走反代都能自动给出正确地址：

```json
{
  "service": "powerline-nas-server",
  "version": "1.5",
  "pmsLines": 3,
  "urls": {
    "pmsBase": "http://192.168.1.10:8080/",
    "tileServer": "http://192.168.1.10:8080/tiles/{z}/{x}/{y}.png"
  }
}
```

若希望 App 自动改用 nginx 8090 的高性能瓦片地址，在 `.env` 里设
`TILE_PUBLIC_URL=http://192.168.1.10:8090`，`/health` 会多返回 `tileServerNginx`，
App 会优先采用它。留空则 8080 单端口即可，tiles 容器可以不跑。

### 方式 B：编译期写死（批量分发、不希望现场改）

把两行写进 `local.properties` 后 `./gradlew :app:assembleRelease`：

```properties
PMS_BASE_URL=http://192.168.1.10:8080/
TILE_SERVER_URL=http://192.168.1.10:8090/tiles/{z}/{x}/{y}.png
```

编译期值作为**默认值**：用户在设置页改过之后以运行时配置为准，点「恢复默认」才回到这里。

## 六、验收清单

```bash
curl http://192.168.1.10:8080/health
curl http://192.168.1.10:8080/api/units
curl "http://192.168.1.10:8080/api/towers?lineId=L001"
curl "http://192.168.1.10:8080/api/import/pms-ledger?lineIds=L001"
curl -I http://192.168.1.10:8090/tiles/0/0/0.png
```

App 上逐项确认：

1. 底图切到「卫星（服务端）」能出图，不是空白；
2. 「线路管理 → PMS 内网导入」能列出线路并导入，杆塔坐标正确；
3. 「设置 → 服务器文件」能列出 NAS `files` 目录里的 KML 等文件，批量导入为线路 / 图层；
4. 关闭 NAS 后 App 会自动回退在线天地图（原有逻辑），不影响离线巡检。

## 七、外网访问与安全

- **推荐**：NAS 与手机都装 Tailscale / WireGuard 组虚拟内网，`local.properties` 里填虚拟 IP，
  不开公网端口。（位置共享功能已移除，无需为 WS 做反代证书。）
- 若必须端口映射：只映射 8080/8090 到内网，前面挂 Nginx/Caddy 做 HTTPS + 鉴权，
  并把 `.env` 的 `API_TOKEN` 打开（注意 App 的 OkHttp 目前不带自定义 Header，
  开启鉴权需同步在 `di/AppModule.kt` 加拦截器，或改用网关侧 IP 白名单）。
- 本服务默认**无鉴权**，仅适用于可信内网；不要直接暴露到公网。
- 备份：`tar czf pms-backup.tgz data/`，恢复时覆盖同名目录再 `docker compose restart`。

## 八、常见故障

| 现象 | 排查 |
|---|---|
| App 提示「接口调用失败 / 检查 BASE_URL」 | 手机与 NAS 是否同网；`curl .../health` 是否通；`PMS_BASE_URL` 是否以 `/` 结尾 |
| 底图空白 | `curl -I http://NAS:8090/tiles/6/52/26.png` 是否 200；`data/tiles/` 是否有对应层级文件；防火墙 8090 |
| 能出图但只有部分区域 | 只下载了局部瓦片，用 `fetch_tiles.py` 补对应 bbox/层级，或让 App 回退在线 |
| 台账列表为空 | 看 `/health` 的 `pmsLines`；确认已跑 `import_excel.py`；`SEED_DEMO=0` 且未导入时就是空 |
| 容器起不来 | `docker compose logs -f pms`；8080/8090 是否被 NAS 上其他服务占用（改 `.env` 端口） |
| 构建卡在 `load metadata` / `docker.fnnas.com` `no route to host` / `401 Unauthorized` | 飞牛 fnOS 加速源失效，与本工程无关。按「2.5 镜像拉取失败」换加速源、设 `BASE_IMAGE`/`TILES_IMAGE`、离线导入镜像或改用 `run-local.sh` |
| 构建时 `pip install` 超时 / 连不上 pypi | `.env` 里设 `PIP_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple`，或走离线 / 无 Docker 方案 |

## 九、App 端实现说明（v1.1.1 运行时配置 / v1.2.0 数据同步 / v1.3.0 原始文件 / v1.5.0 移除位置共享）

| 位置 | 做法 |
|---|---|
| `data/prefs/ServerConfig.kt` | 单例保存台账 / 瓦片两个最终生效地址，DataStore 持久化，暴露 Flow + 同步 getter |
| `di/ServerUrlInterceptor.kt` | 只挂在 PmsApi 专用 OkHttpClient 上，逐请求替换 scheme/host/port/前缀，Retrofit 无需重建；**不能加全局 client**，否则在线瓦片下载会被一起重定向 |
| `Basemap.kt` / `MapController.kt` / `MapLibreView.kt` | 瓦片地址与自定义地图源作为参数传入 style JSON，`LaunchedEffect` 监听变化自动重建底图并保留相机 |
| `ui/settings/ServerSettingsScreen.kt` | 主地址一键推导、测试连接、高级覆盖、恢复默认 |
| `ui/settings/AboutScreen.kt` | 1.5.0 关于应用 / 检查更新（GitHub Releases API） |
| `data/remote/PmsApi.kt` | v1.2.0 新增 `api/datasets` 的 list / download / upload / delete，复用同一个动态地址拦截器 |
| `data/repository/DataSyncRepository.kt` | v1.2.0 组装本机图层 + 线路台账为 `DatasetBundleDto`，上传、下载合并、本地 JSON 导入导出 |
| `ui/settings/DataSyncScreen.kt` | v1.2.0 上传 / 下载 / 删除 / 导出 / 导入三路入口 |
| `ui/settings/CustomMapSourceScreen.kt` | v1.2.0 自定义 XYZ / TMS 地图源管理，可叠加或设为主底图 |
| `data/remote/model/ServerFileModels.kt` | v1.3.0 服务器文件 DTO 与扩展名分类（纯 Kotlin，可单测） |
| `data/repository/ServerFileRepository.kt` | v1.3.0 列出 / 流式下载到缓存 / 上传 / 删除服务器原始文件 |
| `ui/settings/ServerFilesScreen.kt` | v1.3.0 浏览服务器文件，下载后按「线路」或「图层」导入 |
| `data/parser/GeoJsonExporter.kt` | v1.3.0 全量数据集 → 通用 GeoJSON FeatureCollection（纯 Kotlin） |
| `ui/settings/DataSyncScreen.kt` | v1.3.0 顶部新增一键全量导出（备份 JSON / GeoJSON）与全量导入 |

因此换 NAS IP 只需在 App 设置里改一次，历史安装包无需重发；
**数据同步与原始文件库都走同一个 8080 地址，不需要额外开端口或改 nginx**。
