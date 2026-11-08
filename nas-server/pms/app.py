# -*- coding: utf-8 -*-
"""
输电线路智能巡检 · NAS 内网服务端
================================================
单进程同时提供三类能力，端口 8080：

  1) PMS 台账 REST   /api/*            对应 App: PMS_BASE_URL
  2) 位置共享 WS     /ws/location      对应 App: WS_LOCATION_URL
  3) XYZ 静态瓦片    /tiles/*          对应 App: TILE_SERVER_URL（也可交给 nginx:8090）

接口契约严格对齐 App 源码：
  data/remote/PmsApi.kt
  data/remote/model/PmsModels.kt
  ui/map/Basemap.kt  (serverTileUrl：tiles/yn_img/、tiles/cn_img/、tiles/yn_vec/)
  data/location/LocationShareSocket.kt

数据落 SQLite（/data/pms.db），瓦片是纯文件目录（/data/tiles）。
无任何外部数据库依赖，适合群晖 / 威联通 / 绿联 / 极空间 Container Manager。
"""
import json
import os
import sqlite3
import threading
import time
from collections import defaultdict
from pathlib import Path

from flask import Flask, jsonify, request, send_from_directory
from flask_sock import Sock

DB_PATH = os.environ.get("PMS_DB", "/data/pms.db")
TILE_ROOT = Path(os.environ.get("TILE_ROOT", "/data/tiles"))
API_TOKEN = os.environ.get("API_TOKEN", "").strip()  # 留空 = 不校验，避免 App 端加不了 Header
SEED_DEMO = os.environ.get("SEED_DEMO", "1") == "1"
PORT = int(os.environ.get("PORT", "8080"))

app = Flask(__name__)
sock = Sock(app)


# ---------------------------------------------------------------- 数据库
SCHEMA = """
CREATE TABLE IF NOT EXISTS lines(
  id TEXT PRIMARY KEY, lineName TEXT NOT NULL, voltageLevel TEXT DEFAULT '',
  startStation TEXT, endStation TEXT, inServiceDate TEXT, unitName TEXT,
  lineLength REAL, remark TEXT
);
CREATE INDEX IF NOT EXISTS idx_lines_name ON lines(lineName);
CREATE TABLE IF NOT EXISTS towers(
  id TEXT PRIMARY KEY, lineId TEXT, lineName TEXT, towerNumber TEXT,
  longitude REAL, latitude REAL, elevation REAL, towerType TEXT,
  circuitCount INTEGER DEFAULT 1, circuitNo INTEGER DEFAULT 1
);
CREATE INDEX IF NOT EXISTS idx_towers_line ON towers(lineId);
CREATE TABLE IF NOT EXISTS crossings(
  id TEXT PRIMARY KEY, lineId TEXT, lineName TEXT, crossType TEXT,
  crossObject TEXT, distance REAL, longitude REAL, latitude REAL, towerNumber TEXT
);
CREATE TABLE IF NOT EXISTS conductors(
  id TEXT PRIMARY KEY, lineId TEXT, lineName TEXT, phase TEXT,
  model TEXT, sectionArea REAL, splitCount INTEGER
);
CREATE TABLE IF NOT EXISTS units(name TEXT PRIMARY KEY);
-- 1.2.0：App 上传 / 下载的数据集（图层 + 要素 + 线路台账）
CREATE TABLE IF NOT EXISTS datasets(
  id TEXT PRIMARY KEY, name TEXT NOT NULL, kind TEXT DEFAULT 'mixed',
  version INTEGER DEFAULT 1, updated_at TEXT,
  layer_count INTEGER DEFAULT 0, feature_count INTEGER DEFAULT 0,
  line_count INTEGER DEFAULT 0, tower_count INTEGER DEFAULT 0,
  size_bytes INTEGER DEFAULT 0, payload TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_datasets_updated ON datasets(updated_at);
"""

DEMO_LINES = [
    ("L001", "220kV 昆钢 I 回线", "220kV", "500kV 昆明变", "220kV 安宁变",
     "2012-06-01", "昆明输电管理所", 42.6, "示例数据 · 可用 tools/import_excel.py 覆盖"),
    ("L002", "110kV 昆呈线", "110kV", "110kV 螺蛳湾变", "110kV 呈贡变",
     "2016-09-15", "昆明输电管理所", 18.3, "示例数据"),
    ("L003", "500kV 昆曲 I 回线", "500kV", "500kV 昆明北变", "500kV 曲靖变",
     "2009-03-20", "昆明输电管理所", 96.8, "示例数据"),
]

DEMO_TOWERS = [
    ("T001", "L001", "220kV 昆钢 I 回线", "1", 102.71832, 25.03871, 1891.2, "猫头塔", 2, 1),
    ("T002", "L001", "220kV 昆钢 I 回线", "2", 102.72654, 25.03118, 1903.5, "酒杯塔", 2, 1),
    ("T003", "L001", "220kV 昆钢 I 回线", "3", 102.73511, 25.02486, 1918.0, "酒杯塔", 2, 1),
    ("T004", "L002", "110kV 昆呈线", "1", 102.76210, 24.98230, 1896.4, "上字塔", 1, 1),
    ("T005", "L002", "110kV 昆呈线", "2", 102.77984, 24.97012, 1902.1, "上字塔", 1, 1),
    ("T006", "L003", "500kV 昆曲 I 回线", "1", 102.74560, 25.09240, 1932.7, "酒杯塔", 1, 1),
    ("T007", "L003", "500kV 昆曲 I 回线", "2", 102.78120, 25.11890, 1955.3, "酒杯塔", 1, 1),
]


def db():
    conn = sqlite3.connect(DB_PATH, timeout=15)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    return conn


def init_db():
    Path(DB_PATH).parent.mkdir(parents=True, exist_ok=True)
    TILE_ROOT.mkdir(parents=True, exist_ok=True)
    for sub in ("yn_img", "cn_img", "yn_vec"):
        (TILE_ROOT / sub).mkdir(parents=True, exist_ok=True)
    with db() as c:
        c.executescript(SCHEMA)
        if SEED_DEMO and c.execute("SELECT COUNT(*) FROM lines").fetchone()[0] == 0:
            c.executemany(
                "INSERT OR IGNORE INTO lines VALUES(?,?,?,?,?,?,?,?,?)", DEMO_LINES
            )
            c.executemany(
                "INSERT OR IGNORE INTO towers VALUES(?,?,?,?,?,?,?,?,?,?)", DEMO_TOWERS
            )
            c.executemany(
                "INSERT OR IGNORE INTO units VALUES(?)",
                [("昆明输电管理所",), ("曲靖输电管理所",)],
            )
            print("[init] 已写入示例台账：3 条线路 / 7 基杆塔")


def rows(sql, args=()):
    with db() as c:
        return [dict(r) for r in c.execute(sql, args).fetchall()]


def clean(row):
    """SQLite Row -> App DTO：去掉 App 不认识的字段，None 保留为 null"""
    return {k: v for k, v in row.items() if k != "rowid"}


# ---------------------------------------------------------------- 鉴权 / CORS
@app.before_request
def guard():
    if not API_TOKEN:
        return None
    if request.path.startswith(("/tiles/", "/health", "/ws/")):
        return None
    if request.headers.get("X-Auth-Token", "") != API_TOKEN:
        return jsonify({"error": "unauthorized"}), 401
    return None


@app.after_request
def cors(resp):
    resp.headers["Access-Control-Allow-Origin"] = "*"
    resp.headers["Access-Control-Allow-Headers"] = "*"
    resp.headers["Access-Control-Allow-Methods"] = "GET,POST,DELETE,OPTIONS"
    return resp


@app.route("/api/<path:_p>", methods=["OPTIONS"])
@app.route("/<path:_p>", methods=["OPTIONS"])
def options(_p):
    return "", 204


# ---------------------------------------------------------------- 健康检查
@app.get("/")
@app.get("/health")
def health():
    """健康检查，同时向 App 返回推荐的三件套地址（App 1.1.1+ 会自动采用）。

    request.host_url 用的是「手机实际访问的地址」，因此 App 填什么 IP / 域名 /
    反代前缀，这里就返回什么，容器内部不需要预先知道 NAS 的局域网 IP。
    """
    with db() as c:
        nl = c.execute("SELECT COUNT(*) FROM lines").fetchone()[0]
        nt = c.execute("SELECT COUNT(*) FROM towers").fetchone()[0]
        nd = c.execute("SELECT COUNT(*) FROM datasets").fetchone()[0]
    tiles = sum(1 for _ in TILE_ROOT.rglob("*.png")) if TILE_ROOT.exists() else 0

    prefix = request.headers.get("X-Forwarded-Prefix", "").rstrip("/")
    root = request.host_url.rstrip("/") + prefix
    host_part = request.host_url.split("://", 1)[-1].rstrip("/") + prefix
    ws_scheme = "wss" if request.host_url.startswith("https://") else "ws"
    urls = {
        "pmsBase": root + "/",
        "wsLocation": f"{ws_scheme}://{host_part}/ws/location",
        "tileServer": root + "/tiles/{z}/{x}/{y}.png",
    }
    # 可选：若另起了 nginx 8090 承担瓦片，用 TILE_PUBLIC_URL 告知 App
    nginx = os.environ.get("TILE_PUBLIC_URL", "").strip()
    if nginx:
        urls["tileServerNginx"] = nginx.rstrip("/") + "/tiles/{z}/{x}/{y}.png"

    return jsonify({
        "service": "powerline-nas-server",
        "version": "1.2",
        "pmsLines": nl,
        "pmsTowers": nt,
        "datasets": nd,
        "tiles": tiles,
        "tileRoot": str(TILE_ROOT),
        "auth": "on" if API_TOKEN else "off",
        "urls": urls,
        "hint": "App 只需填 pmsBase 一个地址；1.2.0 起可在「数据同步」里上传/下载图层与线路数据",
        "endpoints": ["/api/lines", "/api/towers", "/api/units",
                      "/api/import/pms-ledger", "/api/datasets (GET/POST)",
                      "/api/datasets/{id} (GET/DELETE)", "/api/datasets/{id}/download",
                      "/api/datasets/import", "/ws/location", "/tiles/{z}/{x}/{y}.png"],
    })


# ---------------------------------------------------------------- PMS REST
def voltage_match(level, q):
    """voltage 是 Int?，线路表存的是 '110kV' 这类字符串，做兼容匹配"""
    if not q:
        return True
    digits = "".join(ch for ch in str(level or "") if ch.isdigit())
    return digits == str(q)


@app.get("/api/lines")
def api_lines():
    unit = request.args.get("unit")
    voltage = request.args.get("voltage", type=int)
    keyword = request.args.get("keyword")
    all_rows = rows("SELECT * FROM lines ORDER BY lineName")
    out = []
    for r in all_rows:
        if unit and unit not in (r.get("unitName") or ""):
            continue
        if keyword and keyword.lower() not in (r.get("lineName") or "").lower():
            continue
        if not voltage_match(r.get("voltageLevel"), voltage):
            continue
        out.append(clean(r))
    return jsonify(out)


@app.get("/api/towers")
def api_towers():
    line_id = request.args.get("lineId", "")
    if not line_id:
        return jsonify([])
    return jsonify([clean(r) for r in rows(
        "SELECT * FROM towers WHERE lineId=? ORDER BY CAST(towerNumber AS INTEGER)", (line_id,)
    )])


@app.get("/api/units")
def api_units():
    names = [r["name"] for r in rows("SELECT name FROM units ORDER BY name")]
    names += [r["unitName"] for r in rows(
        "SELECT DISTINCT unitName FROM lines WHERE unitName IS NOT NULL AND unitName<>''"
    )]
    return jsonify({"units": sorted(set(n for n in names if n))})


def bundle_for(line_ids):
    if line_ids:
        marks = ",".join("?" for _ in line_ids)
        lines = rows(f"SELECT * FROM lines WHERE id IN ({marks}) OR lineName IN ({marks})",
                     tuple(line_ids) * 2)
    else:
        lines = rows("SELECT * FROM lines")
    ids = [r["id"] for r in lines]
    if not ids:
        return {"lines": [], "towers": [], "crossings": [], "conductors": []}
    marks = ",".join("?" for _ in ids)
    q = lambda table: [clean(r) for r in rows(
        f"SELECT * FROM {table} WHERE lineId IN ({marks})", tuple(ids)
    )]
    return {
        "lines": [clean(r) for r in lines],
        "towers": q("towers"),
        "crossings": q("crossings"),
        "conductors": q("conductors"),
    }


@app.get("/api/import/pms-ledger")
def pull_ledger():
    raw = request.args.get("lineIds", "").strip()
    ids = [x for x in raw.split(",") if x]
    bundle = bundle_for(ids)
    # App 的三个 include* 参数默认 true，这里保留兼容，false 时裁掉对应数组
    if request.args.get("includeTowers", "true").lower() == "false":
        bundle["towers"] = []
    if request.args.get("includeCrossings", "true").lower() == "false":
        bundle["crossings"] = []
    if request.args.get("includeConductors", "true").lower() == "false":
        bundle["conductors"] = []
    return jsonify(bundle)


@app.post("/api/import/pms-ledger")
def push_ledger():
    """从 App 回传台账，按 id / 线路名+杆塔号 UPSERT"""
    body = request.get_json(silent=True) or {}
    count = 0
    with db() as c:
        for r in body.get("lines", []):
            rid = r.get("id") or ("L-" + (r.get("lineName") or str(time.time())))
            c.execute("""INSERT INTO lines(id,lineName,voltageLevel,startStation,endStation,
                         inServiceDate,unitName,lineLength,remark) VALUES(?,?,?,?,?,?,?,?,?)
                         ON CONFLICT(id) DO UPDATE SET
                         lineName=excluded.lineName, voltageLevel=excluded.voltageLevel,
                         startStation=excluded.startStation, endStation=excluded.endStation,
                         inServiceDate=excluded.inServiceDate, unitName=excluded.unitName,
                         lineLength=excluded.lineLength, remark=excluded.remark""",
                      (rid, r.get("lineName", ""), r.get("voltageLevel", ""),
                       r.get("startStation"), r.get("endStation"), r.get("inServiceDate"),
                       r.get("unitName"), r.get("lineLength"), r.get("remark")))
            count += 1
        for table, key in (("towers", "towerNumber"), ("crossings", "crossType"),
                           ("conductors", "phase")):
            for r in body.get(table, []):
                rid = r.get("id") or "-".join(str(r.get(k) or "") for k in
                                              ("lineId", key)) or str(time.time_ns())
                cols = {"towers": ("lineId", "lineName", "towerNumber", "longitude",
                                   "latitude", "elevation", "towerType", "circuitCount",
                                   "circuitNo"),
                        "crossings": ("lineId", "lineName", "crossType", "crossObject",
                                      "distance", "longitude", "latitude", "towerNumber"),
                        "conductors": ("lineId", "lineName", "phase", "model",
                                       "sectionArea", "splitCount")}[table]
                values = [rid] + [r.get(k) for k in cols]
                placeholders = ",".join("?" for _ in values)
                c.execute(f"INSERT OR REPLACE INTO {table}(id,{','.join(cols)}) "
                          f"VALUES({placeholders})", values)
                count += 1
    return jsonify({"success": True, "message": "台账已同步", "count": count})


# ---------------------------------------------------------------- 数据集（1.2.0）
# App「数据同步」页用它上传 / 下载图层与线路数据，也可用 tools/dataset_cli.py 离线管理。
# 格式契约：powerline-dataset/1，见 App 端 data/remote/model/DatasetModels.kt
def dataset_counts(body):
    layers = body.get("layers") or []
    ledger = body.get("ledger") or {}
    return (
        len(layers),
        sum(len(x.get("features") or []) for x in layers),
        len(ledger.get("lines") or []),
        len(ledger.get("towers") or []),
    )


def save_dataset(body):
    dsid = (body.get("id") or "").strip() or ("DS-" + str(time.time_ns()))
    name = (body.get("name") or "未命名数据集").strip()
    if "/" in name or "\\" in name:
        return {"success": False, "message": "名称不能包含路径分隔符", "count": 0}
    payload = json.dumps(body, ensure_ascii=False, separators=(",", ":"))
    nl, nf, nlines, ntowers = dataset_counts(body)
    now = time.strftime("%Y-%m-%d %H:%M:%S")
    with db() as c:
        old = c.execute("SELECT version FROM datasets WHERE id=?", (dsid,)).fetchone()
        version = (old["version"] + 1) if old else 1
        c.execute("""INSERT INTO datasets(id,name,kind,version,updated_at,
                     layer_count,feature_count,line_count,tower_count,size_bytes,payload)
                     VALUES(?,?,?,?,?,?,?,?,?,?,?)
                     ON CONFLICT(id) DO UPDATE SET
                     name=excluded.name, kind=excluded.kind, version=excluded.version,
                     updated_at=excluded.updated_at, layer_count=excluded.layer_count,
                     feature_count=excluded.feature_count, line_count=excluded.line_count,
                     tower_count=excluded.tower_count, size_bytes=excluded.size_bytes,
                     payload=excluded.payload""",
                  (dsid, name, body.get("kind") or "mixed", version, now,
                   nl, nf, nlines, ntowers, len(payload.encode("utf-8")), payload))
    return {
        "success": True, "message": "数据集已同步", "count": nl + nlines,
        "id": dsid, "version": version, "name": name,
        "layers": nl, "features": nf, "lines": nlines, "towers": ntowers,
    }


def dataset_row(r):
    try:
        note = json.loads(r["payload"]).get("note")
    except Exception:
        note = None
    return {
        "id": r["id"], "name": r["name"], "kind": r["kind"],
        "version": r["version"], "updatedAt": r["updated_at"],
        "layerCount": r["layer_count"], "featureCount": r["feature_count"],
        "lineCount": r["line_count"], "towerCount": r["tower_count"],
        "sizeBytes": r["size_bytes"], "note": note,
    }


@app.get("/api/datasets")
def list_datasets():
    """只返回元数据，不含要素，列表传输量很小"""
    return jsonify([dataset_row(r) for r in rows(
        "SELECT * FROM datasets ORDER BY updated_at DESC"
    )])


@app.get("/api/datasets/<dsid>")
def get_dataset(dsid):
    rs = rows("SELECT payload FROM datasets WHERE id=?", (dsid,))
    if not rs:
        return jsonify({"success": False, "message": "数据集不存在"}), 404
    return app.response_class(rs[0]["payload"], mimetype="application/json")


@app.get("/api/datasets/<dsid>/download")
def download_dataset(dsid):
    """浏览器 / 命令行下载成 .json 文件，可直接用 App「导入本地 JSON」读回"""
    rs = rows("SELECT name, payload FROM datasets WHERE id=?", (dsid,))
    if not rs:
        return jsonify({"success": False, "message": "数据集不存在"}), 404
    safe = "".join(c for c in rs[0]["name"] if c not in '/\\\r\n')[:60] or dsid
    resp = app.response_class(rs[0]["payload"], mimetype="application/json")
    resp.headers["Content-Disposition"] = f'attachment; filename="{safe}.json"'
    return resp


@app.post("/api/datasets")
def upload_dataset():
    """App 上传数据集：同 id 覆盖并递增 version"""
    result = save_dataset(request.get_json(silent=True) or {})
    if not result.get("success"):
        return jsonify(result), 400
    return jsonify(result)


@app.post("/api/datasets/import")
def import_dataset_file():
    """从本地 JSON 文件导入（multipart 的 file 字段或 raw body）"""
    f = request.files.get("file") if request.files else None
    raw = f.read().decode("utf-8") if f else (request.get_data(as_text=True) or "")
    try:
        body = json.loads(raw)
    except (ValueError, TypeError):
        return jsonify({"success": False, "message": "JSON 解析失败"}), 400
    result = save_dataset(body)
    if not result.get("success"):
        return jsonify(result), 400
    return jsonify(result)


@app.delete("/api/datasets/<dsid>")
def delete_dataset(dsid):
    with db() as c:
        n = c.execute("DELETE FROM datasets WHERE id=?", (dsid,)).rowcount
    return jsonify({"success": True, "message": "已删除" if n else "数据集不存在", "count": n})


# ---------------------------------------------------------------- XYZ 瓦片
@app.get("/tiles/<path:rel>")
def tiles(rel):
    """单容器模式下也能供瓦片；多容器时由 nginx:8090 承担，性能更好"""
    f = TILE_ROOT / rel
    if not f.is_file():
        blank = TILE_ROOT / "blank.png"
        f = blank if blank.is_file() else None
    if f is None:
        return "", 404
    resp = send_from_directory(TILE_ROOT, str(f.relative_to(TILE_ROOT)))
    resp.headers["Cache-Control"] = "public, max-age=604800"
    return resp


# ---------------------------------------------------------------- 位置共享 WS
# 协议（LocationShareSocket.kt）：
#   App -> {"userId","lon","lat","speed","ts"}
#   服务端按 room 广播给房间内其他连接，不发回自己
_clients = defaultdict(list)
_lock = threading.Lock()


@sock.route("/ws/location")
def ws_location(ws):
    room = request.args.get("room") or "default"
    with _lock:
        _clients[room].append(ws)
    print(f"[ws] {room} 接入，当前 {len(_clients[room])} 人", flush=True)
    try:
        while True:
            raw = ws.receive()
            if raw is None:
                break
            try:
                o = json.loads(raw)
            except (ValueError, TypeError):
                continue
            if not o.get("userId") or o.get("lon") is None or o.get("lat") is None:
                continue
            payload = json.dumps({
                "userId": o.get("userId"),
                "lon": o.get("lon"),
                "lat": o.get("lat"),
                "speed": o.get("speed", 0),
                "ts": o.get("ts", int(time.time() * 1000)),
            }, ensure_ascii=False)
            with _lock:
                peers = [c for c in _clients[room] if c is not ws]
            for c in peers:
                try:
                    c.send(payload)
                except Exception:
                    with _lock:
                        try:
                            _clients[room].remove(c)
                        except ValueError:
                            pass
    finally:
        with _lock:
            try:
                _clients[room].remove(ws)
            except ValueError:
                pass
        print(f"[ws] {room} 断开，剩余 {len(_clients[room])} 人", flush=True)


if __name__ == "__main__":
    init_db()
    print(f"[boot] 台账库 {DB_PATH} | 瓦片根 {TILE_ROOT} | 端口 {PORT}", flush=True)
    app.run(host="0.0.0.0", port=PORT, threaded=True)
