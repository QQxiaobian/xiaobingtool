#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
数据集命令行工具（NAS 端 / 电脑端均可运行，只用标准库）。

用法：
  python3 dataset_cli.py --base http://192.168.1.10:8080 list
  python3 dataset_cli.py download DS-xxx -o 备份.json
  python3 dataset_cli.py upload 备份.json          # 从本地 JSON 上传成数据集
  python3 dataset_cli.py delete DS-xxx

JSON 文件与 App「导出本地 JSON」的格式完全一致（powerline-dataset/1），
因此可以在 NAS、电脑、手机之间离线流转。
"""
import argparse
import json
import os
import sys
import urllib.error
import urllib.request

DEFAULT_BASE = os.environ.get("PMS_BASE", "http://127.0.0.1:8080")


def call(base, method, path, body=None, token=""):
    url = base.rstrip("/") + path
    data = None
    headers = {"Accept": "application/json"}
    if token:
        headers["X-Auth-Token"] = token
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            raw = resp.read().decode("utf-8")
            return json.loads(raw) if raw.strip().startswith(("{", "[")) else raw
    except urllib.error.HTTPError as e:
        return {"error": "HTTP %s" % e.code, "detail": e.read().decode("utf-8", "ignore")}
    except Exception as e:  # noqa: BLE001
        return {"error": str(e)}


def main():
    p = argparse.ArgumentParser(description="输电线路巡检 NAS 数据集管理")
    p.add_argument("--base", default=DEFAULT_BASE, help="服务端地址，如 http://192.168.1.10:8080")
    p.add_argument("--token", default=os.environ.get("API_TOKEN", ""), help="API_TOKEN（若服务端开启鉴权）")
    sub = p.add_subparsers(dest="cmd", required=True)

    sub.add_parser("list", help="列出全部数据集")
    d = sub.add_parser("download", help="下载数据集为 JSON 文件")
    d.add_argument("id")
    d.add_argument("-o", "--output", default="")
    u = sub.add_parser("upload", help="上传本地 JSON 文件为数据集")
    u.add_argument("file")
    u2 = sub.add_parser("import", help="同 upload（兼容旧叫法）")
    u2.add_argument("file")
    x = sub.add_parser("delete", help="删除数据集")
    x.add_argument("id")

    a = p.parse_args()

    if a.cmd == "list":
        rows = call(a.base, "GET", "/api/datasets", token=a.token)
        if isinstance(rows, dict):
            print(json.dumps(rows, ensure_ascii=False, indent=2))
            return 1
        if not rows:
            print("（服务器上暂无数据集）")
        for r in rows:
            print(
                "%-22s %-22s v%-2s %s  图层%s/要素%s/线路%s/杆塔%s  %s" % (
                    r.get("id", ""), r.get("name", ""), r.get("version", 1),
                    r.get("updatedAt", ""), r.get("layerCount", 0), r.get("featureCount", 0),
                    r.get("lineCount", 0), r.get("towerCount", 0),
                    r.get("sizeBytes", 0),
                )
            )
        return 0

    if a.cmd == "download":
        data = call(a.base, "GET", "/api/datasets/" + a.id, token=a.token)
        if isinstance(data, dict) and data.get("error"):
            print("下载失败：%s" % data)
            return 1
        out = a.output or ("%s.json" % a.id)
        with open(out, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        print("已保存 %s" % out)
        return 0

    if a.cmd in ("upload", "import"):
        with open(a.file, "r", encoding="utf-8") as f:
            body = json.load(f)
        res = call(a.base, "POST", "/api/datasets", body, token=a.token)
        print(json.dumps(res, ensure_ascii=False, indent=2))
        return 0 if res.get("success") else 1

    if a.cmd == "delete":
        res = call(a.base, "DELETE", "/api/datasets/" + a.id, token=a.token)
        print(json.dumps(res, ensure_ascii=False, indent=2))
        return 0 if res.get("success") else 1

    return 0


if __name__ == "__main__":
    sys.exit(main())
