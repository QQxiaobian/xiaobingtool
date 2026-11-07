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

## 五、App 端配置（v1.1.1 起可在 App 里直接改）

### 方式 A：现场填写，不用重新打包（推荐）

**设置 → 服务器（自建 NAS）→ 服务器地址**，只填一个主地址：

```
192.168.1.10:8080
```

点「**测试连接**」→ App 会请求 `http://192.168.1.10:8080/health`，成功后自动把
服务端 `urls` 里返回的三个推荐地址填入高级输入框 → 点「**保存**」立即生效。

- 台账接口、瓦片地址改完立即生效，不用重启 App
- 瓦片地址变化会自动重建底图，镜头位置保持不变
- 若位置共享正在运行，需停止后重新开启才会连到新地址
- 高级里可单独覆盖三个地址（例如把瓦片改到 nginx 的 8090）
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
  "version": "1.1",
  "pmsLines": 3,
  "urls": {
    "pmsBase": "http://192.168.1.10:8080/",
    "wsLocation": "ws://192.168.1.10:8080/ws/location",
    "tileServer": "http://192.168.1.10:8080/tiles/{z}/{x}/{y}.png"
  }
}
```

若希望 App 自动改用 nginx 8090 的高性能瓦片地址，在 `.env` 里设
`TILE_PUBLIC_URL=http://192.168.1.10:8090`，`/health` 会多返回 `tileServerNginx`，
App 会优先采用它。留空则 8080 单端口即可，tiles 容器可以不跑。

### 方式 B：编译期写死（批量分发、不希望现场改）

把三行写进 `local.properties` 后 `./gradlew :app:assembleRelease`：

```properties
PMS_BASE_URL=http://192.168.1.10:8080/
WS_LOCATION_URL=ws://192.168.1.10:8080/ws/location
TILE_SERVER_URL=http://192.168.1.10:8090/tiles/{z}/{x}/{y}.png
```

`WS_LOCATION_URL` 不要带 `?room=`，App 会自动追加房间号。
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

## 九、App 端实现说明（v1.1.1 已实现运行时配置）

| 位置 | 做法 |
|---|---|
| `data/prefs/ServerConfig.kt` | 单例保存三个最终生效地址，DataStore 持久化，暴露 Flow + 同步 getter |
| `di/ServerUrlInterceptor.kt` | 只挂在 PmsApi 专用 OkHttpClient 上，逐请求替换 scheme/host/port/前缀，Retrofit 无需重建；**不能加全局 client**，否则在线瓦片下载会被一起重定向 |
| `LocationShareSocket.kt` | 连接时读运行时 WS 地址 |
| `Basemap.kt` / `MapController.kt` / `MapLibreView.kt` | 瓦片地址作为参数传入 style JSON，`LaunchedEffect` 监听变化自动重建底图并保留相机 |
| `ui/settings/ServerSettingsScreen.kt` | 主地址一键推导、测试连接、高级覆盖、恢复默认 |

因此换 NAS IP 只需在 App 设置里改一次，历史安装包无需重发。
