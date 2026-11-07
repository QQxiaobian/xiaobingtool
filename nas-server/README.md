# NAS 自建地图服务器 / 线路服务器

给「输电线路智能巡检」App 用的内网服务端。部署在群晖、威联通、绿联、极空间或任意 Docker 主机上，
手机连同一局域网（或 VPN）即可访问，不再依赖公网天地图额度与 PMS 外网。

## 一、要建的是哪三个服务

App 里已经预留了三个地址（`app/build.gradle.kts` 的 `buildConfigField`），一一对应：

| 服务 | 端口 | App 配置项 | 作用 |
|---|---|---|---|
| **瓦片服务**（nginx 静态） | 8090 | `TILE_SERVER_URL` | 卫星影像 / 矢量 / 路网 XYZ 瓦片 |
| **线路台账服务**（Flask REST） | 8080 | `PMS_BASE_URL` | 线路、杆塔、交叉跨越、导地线、运维单位 |
| **位置共享**（WebSocket） | 8080 | `WS_LOCATION_URL` | 同房间成员实时位置广播 |

两个容器：`powerline-tiles`（纯静态，性能好）、`powerline-pms`（REST + WS + 瓦片兜底）。
台账存 SQLite `data/pms.db`，瓦片就是 `data/tiles/` 下的普通文件，**备份等于打包 `data/` 目录**。

> 接口不是随便定的，全部对齐 App 源码：`data/remote/PmsApi.kt`、`PmsModels.kt`、
> `ui/map/Basemap.kt`（`serverTileUrl` 会拼出 `tiles/yn_img/`、`tiles/cn_img/`、`tiles/yn_vec/`）、
> `data/location/LocationShareSocket.kt`。按本工程部署即可直接连通，无需改 App 代码。

### REST 接口清单

| 方法 | 路径 | 返回 |
|---|---|---|
| GET | `/api/lines?unit=&voltage=&keyword=` | `PmsLineDto[]` |
| GET | `/api/towers?lineId=` | `PmsTowerDto[]` |
| GET | `/api/units` | `{"units":[...]}` |
| GET | `/api/import/pms-ledger?lineIds=a,b&includeTowers=true...` | `PmsLedgerBundle`（4 个数组） |
| POST | `/api/import/pms-ledger` | `ImportAckDto`（按主键 UPSERT） |
| GET | `/health` | 服务状态与统计 |

WebSocket：`ws://<NAS>:8080/ws/location?room=xxx`，App 会自动在地址后补 `?room=`。
客户端上报 `{"userId","lon","lat","speed","ts"}`，服务端广播给同房间其他人（不发回自己）。

## 二、部署（3 步）

**前置条件**

1. NAS 已装 Docker / Container Manager，且**给 NAS 绑定固定局域网 IP**（路由器 DHCP 保留），
   例如 `192.168.1.10`；防火墙放行 8080、8090。
2. 手机与 NAS 同一内网；Android 端已配置 `usesCleartextTraffic="true"`，HTTP/WS 明文可用，不必上证书。
3. 若是群晖：把 `nas-server` 整个目录放进 `/docker/powerline/`，用「项目 → 新增 → 从 docker-compose.yml 创建」，
   或 SSH 后执行 `bash deploy.sh`。

```bash
cd nas-server
cp .env.example .env      # 改 NAS_LAN_IP，可选 TIANDITU_KEY
bash deploy.sh            # 生成占位瓦片、构建、启动、健康检查
docker compose logs -f pms
```

部署脚本最后会**直接打印三行 App 配置**，照抄即可。

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

## 五、App 端配置（关键）

这三个地址是 **编译期 `buildConfigField`**，不是运行时设置——**改完必须重新打包 APK**：

```properties
PMS_BASE_URL=http://192.168.1.10:8080/
WS_LOCATION_URL=ws://192.168.1.10:8080/ws/location
TILE_SERVER_URL=http://192.168.1.10:8090/tiles/{z}/{x}/{y}.png
```

写入 `local.properties` 后执行 `./gradlew :app:assembleRelease`。
`WS_LOCATION_URL` 不要带 `?room=`，App 会自动追加房间号。

> 若希望一台 NAS 只开一个端口，把 `TILE_SERVER_URL` 也指向 `http://<NAS>:8080/tiles/{z}/{x}/{y}.png`
> 即可（Flask 内置静态路由），此时可以不跑 tiles 容器。

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
3. 「设置 → 位置共享」填同一房间号，两台手机能互相看到蓝点；
4. 关闭 NAS 后 App 会自动回退在线天地图（原有逻辑），不影响离线巡检。

## 七、外网访问与安全

- **推荐**：NAS 与手机都装 Tailscale / WireGuard 组虚拟内网，`local.properties` 里填虚拟 IP，
  不开公网端口。位置共享用 `wss://` + 反代证书。
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
| 位置共享连不上 | `WS_LOCATION_URL` 用 `ws://` 不是 `http://`；地址别带 `?room=`；两台手机房间号一致 |
| 台账列表为空 | 看 `/health` 的 `pmsLines`；确认已跑 `import_excel.py`；`SEED_DEMO=0` 且未导入时就是空 |
| 容器起不来 | `docker compose logs -f pms`；8080/8090 是否被 NAS 上其他服务占用（改 `.env` 端口） |

## 九、可选的下一步

当前地址写死在 `local.properties` + 编译期注入，换 NAS IP 就要重新打包。若需要现场灵活切换，
可以在 App「设置」里增加「服务器地址」输入框，用 `SettingsStore` 存运行时地址，
让 `PmsRepository` / `Basemap` / `LocationShareSocket` 优先读运行时值、编译期值作兜底。
需要的话告诉我，我直接改并出新版本。
