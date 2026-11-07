# 输电线路智能巡检地图系统（Android）

昆明输电管理所输电线路智能巡检 App。原生安卓（Kotlin + Jetpack Compose + MapLibre Native），
以卫星影像为底图，叠加架空线路、电缆、杆塔、交叉跨越、视频监控、护线员、危险点等业务图层，
并支持导入行政区划图等自定义专题图层（KML/KMZ/GeoJSON/SHP），支持搜索定位、绘制标注、导航、
台账校验、线路导入与后台位置共享。

- **包名**：`com.kunming.powerline`
- **当前版本**：1.0.16（versionCode 17）
- **minSdk**：26（Android 8.0） · **targetSdk / compileSdk**：34（Android 14）
- **构建**：AGP 8.5.2 / Gradle 8.7 / Kotlin 1.9.24 / JDK 17
- **架构**：MVVM + 单 Activity + Compose Navigation + Hilt
- **Excel**：自研零依赖 xlsx 读写器（`XlsxReader` / `XlsxWriter`），不引入 Apache POI

---

## 1. 功能总览

| 模块 | 能力 |
|---|---|
| 底图 | 天地图 WMTS 10 种组合（云南卫星为默认、卫星+路网、矢量、地形、全国卫星、服务端瓦片版）；切换保持相机位置；服务端失败自动回退在线 |
| 图层 | 10 个业务图层独立红/灰开关；电压分色（500kV `#D32F2F` 5dp、220kV `#1976D2` 4dp、110kV `#F57C00` 3dp、35kV `#388E3C` 2dp）；左下角图例 + 实时「N 条线路 / M 条电缆」 |
| 专题图层 | 自定义点/线/面图层，支持 KML / KMZ / GeoJSON / SHP 导入（含行政区划图）；图层面板可改名、改备注、调透明度/线宽、控制名称显示、批量操作；要素可定位、查看详情、转线路、改所属图层 |
| 区域标注 | 面要素名称以质心独立点标注渲染（规避 MapLibre 面要素排版失效）；要素少时强制全显、要素多时自动碰撞避让；名称按缩放分级出现，可设置起始层级；点击区域弹出信息卡显示 KML 备注与扩展属性 |
| 搜索 | 8 种语法：杆塔（线路名+杆塔号）、地名/POI、经纬度（自动识别度分秒）、`照片` / `起飞点` / `视频` / `护线员` 前缀、杆塔模糊、收藏点、线路名 |
| 工具栏 | 沿线杆塔漫游播放、地图截图存 `Pictures/输电巡检`、单次定位、放大/缩小、指北针（跟随旋转，点击回正北） |
| 长按菜单 | 高德/百度导航、罗盘开关、重置正北、测距连线、位置校准 |
| 绘制 | 线/多边形/圆/矩形/文字；实时球面长度与面积；撤销/删除/暂停/保存；存 Room `custom_overlay`；导出 GeoJSON |
| 设置 | 房间号位置共享（前台服务 1s 推送 + WebSocket）、标注字号 8–20sp、名称起始缩放级 6–17、真北/磁北、地图组、地图数据设置、台账校验、帮助 |
| 线路管理 | 模板下载、创建空线路、同塔多回显示优化、Excel 导入（列映射/预览/错误导出）、PMS 内网 UPSERT 增量导入、KML/KMZ/GeoJSON/GPX/SHP 导入、全量删除（输入「删除」二次确认）、统一校验规则、导入批次 5 分钟内撤销 |
| 图层导入 | 收藏点/起飞点/危险点/交叉跨越/视频监控/护线员/电缆 7 个图层全部支持 KML/KMZ/GeoJSON/SHP 导入与逐条编辑；影像图层支持从相册批量导入并按 EXIF GPS 定位 |
| 文件解析 | 自写 KML/KMZ/GPX/GeoJSON 解析器；SHP（含 DBF 属性表、GBK 解码、Gauss-Krüger/WebMercator 反算）；KMZ/SHP 支持 zip 打包上传；解析 KML `ExtendedData`/`SimpleData` 扩展属性作为区域名称与备注 |
| 原生能力 | 后台定位前台服务 + WorkManager 看护复活、开机自启、离线瓦片包（mbtiles / 区域预下载）、相机拍照写 Exif GPS、方位角传感器真北 |

---

## 1.1 构建验证结果

| 项目 | 结果 |
|---|---|
| `:app:assembleDebug` | ✅ 通过 |
| `:app:assembleRelease`（R8 混淆 + 签名） | ✅ 通过，通用包 46 MB / arm64-v8a 单包 26 MB |
| `:app:testDebugUnitTest` | ✅ 89 项全部通过 |
| 签名校验 `apksigner verify` | ✅ 通过（v1/v2/v3） |
| ABI 覆盖 | arm64-v8a / armeabi-v7a / x86 / x86_64（含夜神、雷电模拟器） |
| 最低系统 | Android 8.0（API 26） |

单元测试覆盖：

- `GeoUtilsTest`：Haversine 距离、折线长、球面面积、点到折线距离、WGS84↔GCJ02 往返（误差 < 1 m）、度分秒解析、中国范围判定
- `ImportValidatorTest`：线路名/电压/坐标/杆塔号重复/空线路等 8 类校验规则
- `TemplateTest` + `XlsxRoundTripTest`：模板包结构、表头一致性、xlsx 写入后读回精度不丢失、空行行号对齐
- `KmlParseTest` / `ThematicParseTest`：KML `Placemark`、`MultiGeometry`、`ExtendedData`/`SimpleData` 扩展属性、名称兜底优先级
- `ShpThematicParseTest`：大端文件头、记录长度、DBF 32 字节字段描述区、点/线/面/MultiPoint 与 Z/M 坐标
- `ThematicLabelTest` / `ThematicAreaLabelTest` / `ThematicNameTest`：标注文案组装、质心点要素生成、备注仅在单要素图层补位、无名图层兜底
- `HeadingIconTest`：指北针图标按方位角旋转的角度映射

---

## 2. 环境搭建

### 2.1 必备工具

| 工具 | 版本 |
|---|---|
| Android Studio | Hedgehog（2023.1.1）或更高 |
| JDK | 17（AGP 8.5 要求） |
| Android SDK | Platform 34、Build-Tools 34.0.0 |
| Gradle | 通过 Wrapper 自动下载 8.7，无需单独安装 |

### 2.2 导入工程

```bash
# 1. 用 Android Studio 打开工程根目录（含 settings.gradle.kts 的目录）
# 2. 首次打开会自动下载 Gradle 8.7 与依赖（约 800MB，建议配置国内镜像）

# 或使用命令行（Linux/macOS）
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleDebug
```

Gradle 镜像：默认已使用腾讯云 Gradle 发行包（`gradle/wrapper/gradle-wrapper.properties`），
依赖仓库已配置 Google / Maven Central；国内网络可在 `settings.gradle.kts` 中打开阿里云镜像注释。

### 2.3 配置 local.properties

`local.properties` 位于工程根目录（**不要提交到版本库**），首次使用可从模板复制：

```bash
cp local.properties.template local.properties
```

| 键 | 说明 | 示例 |
|---|---|---|
| `sdk.dir` | Android SDK 路径 | `/Users/xxx/Library/Android/sdk` |
| `TIANDITU_KEY` | 天地图密钥（全国底板） | `abcd1234...` |
| `TIANDITU_KEY_YUNNAN` | 云南天地图密钥（可选，留空复用全国 Key） | `efgh5678...` |
| `AMAP_KEY` | 高德开放平台 Key（定位） | `1a2b3c...` |
| `PMS_BASE_URL` | PMS 内网服务地址，**必须以 `/` 结尾** | `http://10.0.0.1:8080/` |
| `WS_LOCATION_URL` | 位置共享 WebSocket 地址 | `wss://server/ws/location` |
| `TILE_SERVER_URL` | 自建瓦片服务模板 | `http://10.0.0.1:8090/tiles/{z}/{x}/{y}.png` |
| `ROADNET_TILE_URL` | 路网注记瓦片地址（可选，默认用云南天地图注记） | `https://t{s}.tianditu.gov.cn/cva_w/wmts?...` |
| `HELP_DOC_URL` | 帮助页跳转地址（可选） | `https://lbs.tianditu.gov.cn/` |
| `RELEASE_STORE_FILE` | 签名文件相对路径 | `keystore/powerline-release.jks` |
| `RELEASE_STORE_PASSWORD` | 密钥库口令 | — |
| `RELEASE_KEY_ALIAS` | 密钥别名 | `powerline` |
| `RELEASE_KEY_PASSWORD` | 密钥口令 | — |

> Key 全部通过 `buildConfigField` 注入，**代码中无硬编码**；未配置时 App 仍可启动，
> 底图会显示空白瓦片，定位需在地图页点击定位按钮后等待。

---

## 3. Key 申请流程

### 3.1 天地图（底图瓦片）

1. 打开 <https://console.tianditu.gov.cn/>，注册并完成个人/单位实名认证。
2. 「应用管理 → 创建新应用」，应用类型选 **浏览器端（Web端）** 或 **Android 端**。
3. 创建后获得一串 32 位 `tk` 密钥，填入 `TIANDITU_KEY`。
4. 云南本地服务需在 <https://yunnan.tianditu.gov.cn/> 单独申请，填入 `TIANDITU_KEY_YUNNAN`。
5. 免费额度约 **每日 5 万次**瓦片请求；巡检队按 10 人规模、浏览半径控制，需在服务端做瓦片缓存
   （配置 `TILE_SERVER_URL` 指向自建缓存服务）。

### 3.2 高德定位（AMap Location）

1. 打开 <https://console.amap.com/>，创建应用（应用名称如「输电智能巡检」）。
2. 「添加 Key」，**服务平台选择 Android 平台**。
3. `PackageName` 填 `com.kunming.powerline`。
4. `SHA1` 取签名证书指纹：

   ```bash
   keytool -list -v -keystore keystore/powerline-release.jks -alias powerline
   # 取「SHA1:」后的 20 位十六进制（不含空格）
   ```

   调试包与发布包 SHA1 不同，建议在控制台 **同一个 Key 下添加两个 SHA1**，
   或为 debug/release 各建一个 Key。
5. 复制 Key 填入 `AMAP_KEY`。

> 定位 SDK 已本地化为 `app/libs/amapsdk-location.aar`（含 arm64-v8a / armeabi-v7a / x86 / x86_64），
> 模拟器（夜神/雷电）与真机均可运行，无需再联网拉取高德仓库。

### 3.3 PMS 内网（可选）

需运维在内网部署台账服务，实现 `GET /api/lines`、`GET /api/towers?lineId=`、`GET /api/units`、
`GET/POST /api/import/pms-ledger`，字段结构见 `data/remote/model/PmsModels.kt`。

---

## 4. 打包与签名

### 4.1 调试包

```bash
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

### 4.2 发布包（已签名，可直接安装）

```bash
./gradlew :app:assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

Release 使用 `local.properties` 中的签名配置；若未配置则回退 debug 签名（便于调试）。
本工程已内置一套演示密钥 `keystore/powerline-release.jks`（口令 `powerline2024`，别名 `powerline`），
**正式上线请替换为贵单位自有密钥并妥善保管**，否则后续升级会因签名不一致无法覆盖安装。

### 4.3 分 ABI 打包（减小体积，可选）

```bash
./gradlew :app:assembleRelease -PabiSplit=true
```

默认打成一个包含 4 个 ABI 的通用包（约 47MB），便于给夜神/雷电与真机统一分发。

---

## 5. 模拟器与真机调试

| 场景 | 说明 |
|---|---|
| 夜神模拟器 | 系统设置 → 关于平板 → 连点版本号开启开发者选项 → 勾选 USB 调试；`adb connect 127.0.0.1:62001` |
| 雷电模拟器 | 设置 → 其他设置 → 开启 ROOT 与 ADB 调试；`adb connect 127.0.0.1:5555` |
| 安装 | `adb install -r app-release.apk` |
| 授权 | 首次启动会依次申请定位、后台定位、通知权限；Android 12+ 需在系统弹窗中选择「始终允许」 |
| 定位 | 模拟器无 GPS，可在模拟器面板「虚拟定位」设置为昆明（102.7123, 25.0401） |
| 看日志 | `adb logcat -s PowerLine MapLibre AMapLocation` |

---

## 6. 目录结构

```
app/src/main/java/com/kunming/powerline/
├── MainActivity.kt                 单 Activity 入口 + 权限申请
├── PowerLineApp.kt                 @HiltAndroidApp，MapLibre 初始化
├── data/
│   ├── local/                      Room：18 张表 / 10 个 DAO（v3）
│   │   ├── entity/Entities.kt      业务实体（线路/杆塔/点位/影像等）
│   │   ├── entity/ThematicEntities.kt  专题图层、专题要素
│   │   ├── dao/Daos.kt             业务 DAO
│   │   ├── dao/ThematicDao.kt      专题 DAO（批量透明度/名称开关/移动要素）
│   │   └── AppDatabase.kt          含 MIGRATION_2_3
│   ├── repository/                 LineRepository（含校验/几何重建）、PointRepository、BatchRepository、ThematicRepository
│   ├── prefs/SettingsStore.kt      DataStore 偏好（含名称显示起始缩放级）
│   ├── location/                   高德定位封装（GCJ02→WGS84）、位置共享 WebSocket
│   ├── offline/                    mbtiles 导入、区域瓦片预下载
│   ├── parser/                     Excel / KML / KMZ / GPX / GeoJSON / SHP 解析、模板生成
│   │   ├── ThematicParser.kt       KML/GeoJSON 专题解析（含 ExtendedData/SimpleData）
│   │   ├── ShapefileThematicReader.kt  SHP+DBF 二进制解析与坐标系反算
│   │   └── GeoFileParser.kt        导入入口（含 zip 解包与格式嗅探）
│   ├── remote/                     PMS Retrofit 接口与 DTO
│   └── sensor/CompassSensor.kt     方位角 + 磁偏角（真北）
├── service/                        位置共享前台服务、WorkManager 看护、开机自启
├── di/AppModule.kt                 Hilt 提供 Database / DAO / OkHttp / Retrofit
├── ui/
│   ├── MapScreen.kt                主地图页
│   ├── MapViewModel.kt             全部状态与业务动作（导入/转线路/编辑）
│   ├── map/                        MapController、LayerRenderer、底图与图标
│   │   ├── ThematicLabels.kt       专题标注文案组装与兜底规则
│   │   ├── ThematicInfoDialog.kt   要素信息卡（KML 备注/扩展属性）
│   │   └── ThematicLineConverter.kt 专题线 → 线路（顶点映射杆塔号）
│   ├── layer/                      图层枚举、图层面板、电压图例
│   ├── search/                     搜索栏与帮助弹窗
│   ├── draw/                       绘制工具栏与状态机
│   ├── importui/                   创建/导入线路全流程
│   ├── layermanage/                各图层管理页
│   │   ├── LayerImportEdit.kt      通用导入条与编辑弹窗（7 个业务图层复用）
│   │   ├── ThematicLayerScreen.kt  专题图层管理（名称/透明度/线宽/批量/转线路）
│   │   └── LineEditScreen.kt       线路编辑
│   ├── settings/                   设置面板、地图数据设置
│   ├── common/                     通用 Compose 组件
│   └── theme/                      配色与排版（主色 #B71C1C）
├── domain/                         领域模型
└── util/                           坐标计算、GeoJSON 构造、文件保存、拍照 Exif
```

---

## 7. 关键实现说明

### 7.1 坐标系

- 内部统一使用 **WGS84**（CGCS2000 可视为等同）。
- 高德 SDK 返回 GCJ02，已用迭代法反算为 WGS84 后再入库与绘制。
- 调用高德/百度导航 Intent 时，再把 WGS84 正向转换为 GCJ02/BD09。

### 7.2 同塔多回显示

同一坐标 5m 内的多基杆塔视为同址，按回路顺号做 3m 递进（上限 25m）的**绘制偏移**，
杆塔号后追加 Ⅰ/Ⅱ/Ⅲ。真实坐标不变，测距与台账仍用原始经纬度。

### 7.3 离线瓦片

- `mbtiles` 导入后解包为 `tiles/<包名>/{z}/{x}/{y}.png`（自动把 TMS 的 y 轴翻转成 XYZ）。
- 区域预下载按矩形范围 + 层级区间拉取天地图瓦片，可断点续传（已存在文件跳过）。
- 存储位置：`/Android/data/com.kunming.powerline/files/tiles/`，卸载即清理，无需存储权限。

### 7.4 后台定位与进程复活

`LocationSharingService` 以 `foregroundServiceType="location"` 常驻，1 秒定位一次并推送到 WebSocket；
`LocationWatchdogWorker` 每 15 分钟检查服务存活，`BootReceiver` 开机自启。
Android 12+ 后台定位需用户选择「始终允许」，未授权时 App 会引导到系统设置页。

### 7.5 导入校验规则

| 规则 | 处理 |
|---|---|
| 线路名为空 / 电压等级不在 500·220·110·35 | 整行错误，不导入 |
| 杆塔号为空，或同一线路内重复 | 该行错误 |
| 经纬度缺失、为 0,0、越出中国范围（73~136E / 3~54N） | 该行错误 |
| 杆塔间距 > 1500m 或 < 5m | 警告（不阻断） |
| 导入后可撤销 | 5 分钟内按 `import_batch_id` 回滚 |

错误行可导出为 CSV（UTF-8 BOM，Excel 直接打开不乱码）。

### 7.6 专题图层与名称标注

专题图层是独立的点/线/面数据容器（`thematic_layer` + `thematic_feature`），与业务图层互不影响。

**名称渲染策略**（这是本项目踩坑最多的地方）：

- MapLibre Native 在 `Polygon` 几何上排版文字极不稳定，面要素名称常常整层不显示。
  因此面名称**不挂在面图层上**，而是在 `LayerRenderer.polygonLabelFeatures()` 中为每个面计算质心，
  生成一个独立的 `Point` 要素写入 `SRC_AREA_LABEL`，由 `LYR_AREA_LABEL` 单独渲染。
- 图层要素数 ≤ `DENSE_LABEL_THRESHOLD`（120）时走普通名称层，`textAllowOverlap(true)`，
  名称**强制全显不参与碰撞剔除**；超过阈值切换到 `LYR_THEMATIC_LABEL_DENSE`，
  开启碰撞避让，避免满屏重叠。
- 名称出现层级：默认 11 级，可在设置中调 6–17 级。区域层实际取 `labelZoom - 1`（最低 6 级），
  杆塔号再多抬 2.5 级；低缩放不渲染任何文字，避免数据量大时掉帧。
- 名称兜底顺序：**要素名 → 备注首段（≤12 字）→ 图层名 → 图层备注**。
  行政区划数据经常只有 `<description>` 没有 `<name>`，1.0.17 起直接用备注首段当地图标注
  （按逗号/顿号切句，超出截断），保证每个区域都有字可看；备注与名称重复时第二行只留后半句。
  备注只在**单要素图层**时整段补位为第二行，多要素图层不重复挂同一段备注。

**KML 备注**：解析 `Placemark` 下的 `description`，以及 `ExtendedData/Data/value`、
`SchemaData/SimpleData`。行政区划图的名称通常只存在于扩展属性中（`name`、`NAME`、`名称` 等键），
若缺失会导致入库名称为空、地图上无标注——**v1.0.15 之前导入的图层需删除后重新导入**。

### 7.7 专题要素转线路

`convertThematicFeatureToLine()` 把专题线/面边界转为正式线路：

- 至少 2 个顶点（`MIN_POINTS`），闭合环自动去掉重复的首尾点；
- 顶点按顺序映射为 `1..N` 号杆塔，间距与走向保持原始坐标；
- 线路名取要素名，重名时追加 `(2)`、`(3)` 保证唯一；
- 支持单条转换与整层批量转换，批量转换前会提示影响条数。

### 7.8 数据库迁移

`AppDatabase` 当前 `version = 3`。`MIGRATION_2_3` 为 `thematic_layer` 增加三个字段：

| 字段 | 类型 | 默认值 | 用途 |
|---|---|---|---|
| `labelVisible` | INTEGER | 1 | 是否显示该图层名称 |
| `fillOpacity` | REAL | 0.22 | 面填充透明度 |
| `lineWidth` | REAL | 2.2 | 线宽（dp） |

均为 `NOT NULL DEFAULT`，升级时旧库直接补默认值，**不需要清库或重导数据**。
但 v1.0.15 之前入库的专题要素若名称为空，仍需重新导入才能显示标注（见 7.6）。

---

## 8. 常见问题

**Q: 地图白屏 / 只显示网格？**
检查 `TIANDITU_KEY` 是否有效，以及是否在天地图控制台把应用的「服务配额」用尽。抓包可见 `tk` 无效返回 403。

**Q: 定位一直提示失败？**
1) 确认 `AMAP_KEY` 的 PackageName 与 SHA1 匹配当前安装包；2) 模拟器需开启虚拟定位；3) 首次需在隐私弹窗点「同意」。

**Q: Release 包安装后闪退，Debug 正常？**
R8 已保留 MapLibre / 高德 / Moshi / Room 实体 / POI 相关类（见 `app/proguard-rules.pro`）。
若新增反射调用类，需补充 `-keep` 规则。

**Q: 导入 Excel 提示「未能识别杆塔号/经度/纬度列」？**
表头需与模板一致或使用别名（见 `ExcelParser.TemplateColumns.aliases`），也可在预览页手动指定列。

**Q: 后台服务被杀？**
国产 ROM 需手动允许「自启动」「后台运行」「电池不优化」。App 已申请相关权限并注册 WorkManager 看护，
但系统级限制仍需用户手动放行。

**Q: 导入了行政区划图，但区域一个名称都不显示？**
按以下顺序排查：

1. 进入「图层 → 专题图层」，确认该图层的**名称开关**为开启，卡片上会显示「名称 n/m」——
   `n` 是能取到名称的要素数，`n = 0` 说明名称根本没解析入库；
2. 缩放是否已达到设置的起始层级（默认 11 级，区域层取 10 级）；
3. **1.0.17 起**名称缺失会自动用备注首段兜底（截 12 字），旧图层不重导也能看到文字；
   若连备注都没有，说明 KML 里既没有 `<name>` 也没有 `<description>`，
   需检查源文件是否只有几何、属性放在外部 DBF / 表格里。

**Q: 面要素名称在 MapLibre 上不显示，是不是数据问题？**
不是。MapLibre Native 对 `Polygon` 的文字排版支持不稳定，本项目已改为在面质心生成独立点要素
（`SRC_AREA_LABEL`）来渲染名称，不依赖面要素自带的 label。

---

## 9. 版本

| 版本 | 日期 | 说明 |
|---|---|---|
| 1.0.17 | 2026-11 | 无名称的专题区域改用备注首段作标注（行政区划不再空白）、点击区域信息卡标题回退备注、图层面板与电压图例改为浅米色 |
| 1.0.16 | 2026-11 | 行政区划名称解析修复（`ExtendedData`/`SimpleData`）、点击区域查看 KML 备注与扩展属性、图层导入条改为列表顶部全宽、影像图层支持相册批量导入 |
| 1.0.15 | 2026-11 | 7 个业务图层开放导入与编辑、新增 SHP 格式、区域名称不显示问题修复、专题线批量转线路、要素支持修改所属图层 |
| 1.0.14 | 2026-11 | 区域名称/备注可开关显示、批量调整区域透明度、线路上显示线路名称、名称按缩放分级显示以降低卡顿 |
| 1.0.1 – 1.0.13 | 2026-11 | 过程迭代版本：图层渲染、导入流程、搜索与绘制细节修正，改动并入上述条目 |
| 1.0.0 | 2026-11 | 首个完整版本：底图/图层/搜索/绘制/导入/位置共享全链路 |
