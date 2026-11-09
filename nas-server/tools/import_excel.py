# -*- coding: utf-8 -*-
"""
Excel / CSV 台账 -> SQLite（PMS 服务端数据库）

用法：
  python3 tools/import_excel.py 台账.xlsx
  python3 tools/import_excel.py 台账.xlsx --db data/pms.db --replace
  python3 tools/import_excel.py --demo          # 生成一份可编辑的示例 Excel

Excel 需要两个 Sheet（列名见下方映射，顺序不限、可多列）：
  「线路」：线路名称, 电压等级, 起始站, 终止站, 投运日期, 运维单位, 线路长度, 备注
  「杆塔」：线路名称, 杆塔号, 经度, 纬度, 高程, 塔型, 回路数, 回路序号

可选 Sheet（没有就跳过）：
  「交叉跨越」：线路名称, 交叉类型, 交叉物, 距离, 经度, 纬度, 杆塔号
  「导地线」  ：线路名称, 相别, 型号, 截面, 分裂数

导入后重启容器（或立即生效：SQLite WAL 模式，服务端每次请求重开连接）。
"""
import argparse
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_DB = ROOT / "data" / "pms.db"

LINE_COLS = ["线路名称", "电压等级", "起始站", "终止站", "投运日期", "运维单位", "线路长度", "备注"]
TOWER_COLS = ["线路名称", "杆塔号", "经度", "纬度", "高程", "塔型", "回路数", "回路序号"]

# 与 pms/app.py 的 SCHEMA 保持一致
SCHEMA_LOCAL = """
CREATE TABLE IF NOT EXISTS lines(
  id TEXT PRIMARY KEY, lineName TEXT NOT NULL, voltageLevel TEXT DEFAULT '',
  startStation TEXT, endStation TEXT, inServiceDate TEXT, unitName TEXT,
  lineLength REAL, remark TEXT);
CREATE INDEX IF NOT EXISTS idx_lines_name ON lines(lineName);
CREATE TABLE IF NOT EXISTS towers(
  id TEXT PRIMARY KEY, lineId TEXT, lineName TEXT, towerNumber TEXT,
  longitude REAL, latitude REAL, elevation REAL, towerType TEXT,
  circuitCount INTEGER DEFAULT 1, circuitNo INTEGER DEFAULT 1);
CREATE INDEX IF NOT EXISTS idx_towers_line ON towers(lineId);
CREATE TABLE IF NOT EXISTS crossings(
  id TEXT PRIMARY KEY, lineId TEXT, lineName TEXT, crossType TEXT,
  crossObject TEXT, distance REAL, longitude REAL, latitude REAL, towerNumber TEXT);
CREATE TABLE IF NOT EXISTS conductors(
  id TEXT PRIMARY KEY, lineId TEXT, lineName TEXT, phase TEXT,
  model TEXT, sectionArea REAL, splitCount INTEGER);
CREATE TABLE IF NOT EXISTS units(name TEXT PRIMARY KEY);
"""


def read_sheet(path, sheet):
    import openpyxl
    wb = openpyxl.load_workbook(path, data_only=True, read_only=True)
    if sheet not in wb.sheetnames:
        return []
    ws = wb[sheet]
    rows = list(ws.iter_rows(values_only=True))
    if not rows:
        return []
    head = [str(c).strip() if c is not None else "" for c in rows[0]]
    out = []
    for r in rows[1:]:
        if all(c is None or str(c).strip() == "" for c in r):
            continue
        out.append({head[i]: r[i] for i in range(min(len(head), len(r))) if head[i]})
    return out


def g(row, *keys, default=None):
    for k in keys:
        for rk, rv in row.items():
            if rk.strip() == k and rv is not None and str(rv).strip() != "":
                return rv
    return default


def num(v):
    try:
        return float(v)
    except (TypeError, ValueError):
        return None


def write_demo(path):
    import openpyxl
    wb = openpyxl.Workbook()
    ws = wb.active
    ws.title = "线路"
    ws.append(LINE_COLS)
    ws.append(["220kV 昆钢 I 回线", "220kV", "500kV 昆明变", "220kV 安宁变",
               "2012-06-01", "示例运维单位", 42.6, "从 PMS 导出"])
    ws.append(["110kV 昆呈线", "110kV", "110kV 螺蛳湾变", "110kV 呈贡变",
               "2016-09-15", "示例运维单位", 18.3, ""])
    ws2 = wb.create_sheet("杆塔")
    ws2.append(TOWER_COLS)
    for t in [("220kV 昆钢 I 回线", 1, 102.71832, 25.03871, 1891.2, "猫头塔", 2, 1),
              ("220kV 昆钢 I 回线", 2, 102.72654, 25.03118, 1903.5, "酒杯塔", 2, 1),
              ("110kV 昆呈线", 1, 102.76210, 24.98230, 1896.4, "上字塔", 1, 1)]:
        ws2.append(list(t))
    wb.save(path)
    print(f"已生成示例 Excel：{path}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("xlsx", nargs="?", help="Excel 台账路径")
    ap.add_argument("--db", default=str(DEFAULT_DB))
    ap.add_argument("--replace", action="store_true", help="先清空原台账再导入")
    ap.add_argument("--demo", action="store_true", help="生成示例 Excel 后退出")
    a = ap.parse_args()

    if a.demo:
        write_demo(ROOT / "data" / "台账模板.xlsx")
        return
    if not a.xlsx:
        ap.error("请给出 Excel 路径，或使用 --demo 生成模板")

    lines = read_sheet(a.xlsx, "线路")
    towers = read_sheet(a.xlsx, "杆塔")
    if not lines:
        sys.exit("未读到「线路」Sheet 数据")

    Path(a.db).parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(a.db)
    c = conn.cursor()
    # 表结构与 pms/app.py 的 SCHEMA 保持一致；这里内联一份，
    # 让本脚本在只装了 openpyxl 的 NAS 主机上也能独立运行（无需 flask）
    c.executescript(SCHEMA_LOCAL)
    if a.replace:
        for t in ("lines", "towers", "crossings", "conductors"):
            c.execute(f"DELETE FROM {t}")

    name2id = {}
    for r in lines:
        name = str(g(r, "线路名称", "线路名", "lineName", default="")).strip()
        if not name:
            continue
        lid = "L-" + name
        name2id[name] = lid
        c.execute("""INSERT INTO lines(id,lineName,voltageLevel,startStation,endStation,
                     inServiceDate,unitName,lineLength,remark) VALUES(?,?,?,?,?,?,?,?,?)
                     ON CONFLICT(id) DO UPDATE SET
                     lineName=excluded.lineName, voltageLevel=excluded.voltageLevel,
                     startStation=excluded.startStation, endStation=excluded.endStation,
                     inServiceDate=excluded.inServiceDate, unitName=excluded.unitName,
                     lineLength=excluded.lineLength, remark=excluded.remark""",
                  (lid, name, str(g(r, "电压等级", "voltageLevel", default="")),
                   g(r, "起始站"), g(r, "终止站"), str(g(r, "投运日期", default="") or ""),
                   g(r, "运维单位", "单位", "unitName"), num(g(r, "线路长度")),
                   g(r, "备注")))
        unit = g(r, "运维单位", "单位", "unitName")
        if unit:
            c.execute("INSERT OR IGNORE INTO units VALUES(?)", (str(unit),))

    for r in towers:
        name = str(g(r, "线路名称", "线路名", default="")).strip()
        no = str(g(r, "杆塔号", "塔号", "towerNumber", default="")).strip()
        if not name or not no:
            continue
        lid = name2id.get(name, "L-" + name)
        tid = f"T-{lid}-{no}"
        c.execute("""INSERT OR REPLACE INTO towers
                     (id,lineId,lineName,towerNumber,longitude,latitude,elevation,
                      towerType,circuitCount,circuitNo) VALUES(?,?,?,?,?,?,?,?,?,?)""",
                  (tid, lid, name, no, num(g(r, "经度", "lon", "longitude")),
                   num(g(r, "纬度", "lat", "latitude")), num(g(r, "高程", "elevation")),
                   g(r, "塔型", "towerType"), int(num(g(r, "回路数", default=1)) or 1),
                   int(num(g(r, "回路序号", default=1)) or 1)))

    conn.commit()
    print(f"导入完成：{len(lines)} 条线路 / {len(towers)} 基杆塔 -> {a.db}")
    conn.close()


if __name__ == "__main__":
    main()
