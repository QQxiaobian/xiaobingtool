# -*- coding: utf-8 -*-
"""
把天地图 WMTS 瓦片预下载到 NAS，供 App 走内网读取（不再每台手机都去公网请求）。

XYZ / Web Mercator，与 App 的 Basemap.kt 一致。输出目录结构：
    data/tiles/yn_img/{z}/{x}/{y}.png

用法：
  python3 tools/fetch_tiles.py --tk 你的天地图key \
      --bbox 102.5,24.8,103.2,25.6 --zmin 6 --zmax 14 \
      --out data/tiles/yn_img --layer img_w

参数：
  --layer   img_w(影像) / vec_w(矢量) / cva_w(注记) / ter_w(地形) / cia_w(影像注记)
  --bbox    左下右上经纬度 lon1,lat1,lon2,lat2
  --zmin/--zmax  层级区间（建议 6~15，级别每 +1 瓦片数 ×4）
  --workers 并发数，默认 8，别开太大以免触发限流
  --tk      天地图 key；也可用环境变量 TIANDITU_KEY

特性：已存在的瓦片跳过（可断点续传）、失败自动重试 2 次、限速友好。

合规提醒：天地图瓦片有日请求额度与版权要求，本脚本只用于**单位内网缓存**，
          请勿公开分发、转售或对外提供瓦片服务，并遵守天地图服务条款。
"""
import argparse
import math
import os
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
UA = "Mozilla/5.0 (compatible; PowerLineInspection-OfflineCache/1.0)"

TMTS = ("https://t{s}.tianditu.gov.cn/{layer}/wmts?SERVICE=WMTS&REQUEST=GetTile"
        "&VERSION=1.0.0&LAYER={lay}&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles"
        "&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&tk={tk}")


def lon2x(lon, z):
    return int((lon + 180.0) / 360.0 * (1 << z))


def lat2y(lat, z):
    lat = max(-85.05112878, min(85.05112878, lat))
    r = math.radians(lat)
    return int((1.0 - math.asinh(math.tan(r)) / math.pi) / 2.0 * (1 << z))


def jobs(args):
    for z in range(args.zmin, args.zmax + 1):
        xa, xb = lon2x(args.bbox[0], z), lon2x(args.bbox[2], z)
        ya, yb = lat2y(args.bbox[3], z), lat2y(args.bbox[1], z)
        for x in range(xa, xb + 1):
            for y in range(ya, yb + 1):
                yield z, x, y


def fetch(job, args, out):
    z, x, y = job
    dest = out / str(z) / str(x) / f"{y}.png"
    if dest.exists() and dest.stat().st_size > 100:
        return "skip"
    dest.parent.mkdir(parents=True, exist_ok=True)
    lay = args.layer.split("_")[0]
    url = TMTS.format(s=(x + y) % 8, layer=args.layer, lay=lay, z=z, y=y, x=x, tk=args.tk)
    for attempt in range(3):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=20) as r:
                data = r.read()
            if len(data) < 100 or data[:4] == b"<htm":
                raise ValueError("返回的不是瓦片（可能 key 无效或超额）")
            dest.write_bytes(data)
            return "ok"
        except Exception:
            if attempt == 2:
                return "fail"
            time.sleep(0.6 * (attempt + 1))
    return "fail"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--tk", default=os.environ.get("TIANDITU_KEY", ""))
    ap.add_argument("--layer", default="img_w")
    ap.add_argument("--bbox", default="102.5,24.8,103.2,25.6",
                    help="lon1,lat1,lon2,lat2，默认昆明市区")
    ap.add_argument("--zmin", type=int, default=6)
    ap.add_argument("--zmax", type=int, default=13)
    ap.add_argument("--out", default=str(ROOT / "data" / "tiles" / "yn_img"))
    ap.add_argument("--workers", type=int, default=8)
    a = ap.parse_args()
    a.bbox = [float(v) for v in a.bbox.split(",")]
    if len(a.bbox) != 4:
        ap.error("--bbox 需要 4 个数字")
    if not a.tk:
        ap.error("请提供 --tk 或环境变量 TIANDITU_KEY")

    out = Path(a.out)
    todo = list(jobs(a))
    print(f"范围 {a.bbox} | 层级 {a.zmin}-{a.zmax} | 共 {len(todo)} 张 | 输出 {out}")
    if len(todo) > 60000:
        print("!! 瓦片数量过大，建议缩小 bbox 或降低 --zmax（每 +1 级数量 ×4）")
        if input("仍要继续？(y/N) ").strip().lower() != "y":
            return

    stat = {"ok": 0, "skip": 0, "fail": 0}
    with ThreadPoolExecutor(max_workers=a.workers) as pool:
        futs = [pool.submit(fetch, j, a, out) for j in todo]
        for i, f in enumerate(as_completed(futs), 1):
            stat[f.result()] += 1
            if i % 500 == 0:
                print(f"  {i}/{len(todo)}  新增 {stat['ok']} 跳过 {stat['skip']} 失败 {stat['fail']}")
    print(f"完成：新增 {stat['ok']}，已存在 {stat['skip']}，失败 {stat['fail']}")
    print(f"目录：{out}")
    print("把 App 的 TILE_SERVER_URL 指向 /tiles/ 即可；yn_img / cn_img / yn_vec 子目录对应不同底图。")


if __name__ == "__main__":
    main()
