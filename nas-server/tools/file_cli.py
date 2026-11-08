#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
服务器原始文件命令行工具（NAS / 电脑端，只用 Python 标准库）。

用法：
  python3 file_cli.py --base http://192.168.1.10:8080 list
  python3 file_cli.py download <id> -o 图层.kml
  python3 file_cli.py upload 本地.kml        # 上传原始文件到服务器 files 目录
  python3 file_cli.py delete <id>

也可以直接把 KML/KMZ/GeoJSON/GPX/SHP(zip) 拷进 NAS 的 ./data/files/ 目录，
服务端会自动扫描，App「服务器文件」页直接可见，无需走本工具上传。
"""
import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid

DEFAULT_BASE = os.environ.get("PMS_BASE", "http://127.0.0.1:8080")


def req(base, method, path, data=None, headers=None, token=""):
    url = base.rstrip("/") + path
    h = {"Accept": "application/json"}
    if token:
        h["X-Auth-Token"] = token
    if headers:
        h.update(headers)
    r = urllib.request.Request(url, data=data, headers=h, method=method)
    try:
        with urllib.request.urlopen(r, timeout=60) as resp:
            return resp.status, resp.read(), dict(resp.headers)
    except urllib.error.HTTPError as e:
        return e.code, e.read(), {}
    except Exception as e:  # noqa: BLE001
        return 0, str(e).encode("utf-8"), {}


def show(o):
    print(json.dumps(o, ensure_ascii=False, indent=2))


def main():
    p = argparse.ArgumentParser(description="输电线路巡检 NAS 服务器原始文件管理")
    p.add_argument("--base", default=DEFAULT_BASE, help="服务端地址，如 http://192.168.1.10:8080")
    p.add_argument("--token", default=os.environ.get("API_TOKEN", ""), help="API_TOKEN（若服务端开启鉴权）")
    sub = p.add_subparsers(dest="cmd", required=True)

    sub.add_parser("list", help="列出服务器 files 目录里的可导入文件")
    d = sub.add_parser("download", help="按 id 下载原始文件")
    d.add_argument("id")
    d.add_argument("-o", "--output", default="", help="输出文件名")
    u = sub.add_parser("upload", help="上传本地原始文件（KML/GeoJSON/GPX/SHP...）")
    u.add_argument("file")
    x = sub.add_parser("delete", help="删除服务器文件")
    x.add_argument("id")

    a = p.parse_args()
    base, token = a.base, a.token

    if a.cmd == "list":
        code, body, _ = req(base, "GET", "/api/files", token=token)
        if code != 200:
            print("失败 HTTP", code, body.decode("utf-8", "ignore"))
            sys.exit(1)
        files = json.loads(body)
        if not files:
            print("服务器暂无文件。把 KML 等拷进 ./data/files/ 即可自动出现。")
        for f in files:
            print("%s  %10d B  %-8s  %s  %s" % (
                f["id"], f["sizeBytes"], f["kind"], f["updatedAt"], f["name"]))
        print("共 %d 个文件" % len(files))
        return

    if a.cmd == "download":
        code, body, hdrs = req(
            base, "GET", "/api/files/%s/download" % urllib.parse.quote(a.id), token=token
        )
        if code != 200:
            print("下载失败 HTTP", code, body.decode("utf-8", "ignore"))
            sys.exit(1)
        name = a.output
        cd = hdrs.get("Content-Disposition", "")
        if not name and "filename*=" in cd:
            m = re.search(r"filename\*=UTF-8''([^;]+)", cd)
            if m:
                name = urllib.parse.unquote(m.group(1))
        name = name or (a.id + ".bin")
        with open(name, "wb") as out:
            out.write(body)
        print("已下载 %s (%d 字节)" % (name, len(body)))
        return

    if a.cmd == "upload":
        path = a.file
        if not os.path.isfile(path):
            print("找不到文件", path)
            sys.exit(1)
        name = os.path.basename(path)
        with open(path, "rb") as fh:
            data = fh.read()
        boundary = "----plfile" + uuid.uuid4().hex
        # multipart filename 用 ASCII 占位，真实（可能含中文）文件名走 X-File-Name
        ascii_name = "upload" + os.path.splitext(name)[1]
        body = (
            ("--%s\r\n" % boundary).encode()
            + ('Content-Disposition: form-data; name="file"; filename="%s"\r\n'
               % ascii_name).encode("ascii")
            + b"Content-Type: application/octet-stream\r\n\r\n"
            + data
            + ("\r\n--%s--\r\n" % boundary).encode()
        )
        code, resp, _ = req(
            base, "POST", "/api/files", data=body,
            headers={"Content-Type": "multipart/form-data; boundary=%s" % boundary,
                     "X-File-Name": urllib.parse.quote(name)},
            token=token,
        )
        try:
            show(json.loads(resp))
        except (ValueError, TypeError):
            print(resp.decode("utf-8", "ignore"))
        if code not in (200, 201):
            sys.exit(1)
        return

    if a.cmd == "delete":
        code, body, _ = req(
            base, "DELETE", "/api/files/%s" % urllib.parse.quote(a.id), token=token
        )
        print("HTTP", code, body.decode("utf-8", "ignore"))
        if code != 200:
            sys.exit(1)


if __name__ == "__main__":
    main()
