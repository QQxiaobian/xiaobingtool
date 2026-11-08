# 基础镜像可用 --build-arg BASE_IMAGE=... 或 .env 里的 BASE_IMAGE 覆盖。
# 飞牛 fnOS 自带的镜像加速源 docker.fnnas.com 常出现 "no route to host" / 401，
# 导致 compose 构建卡在第一步「load metadata」，连下面这行都执行不到。
# 此时把 BASE_IMAGE 换成可用源即可，例如：
#   BASE_IMAGE=docker.1ms.run/library/python:3.11-slim
ARG BASE_IMAGE=python:3.11-slim
FROM ${BASE_IMAGE}

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    TZ=Asia/Shanghai

WORKDIR /srv
COPY requirements.txt .
# 构建期 pip 源；国内可设 PIP_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple
ARG PIP_INDEX_URL=
RUN if [ -n "$PIP_INDEX_URL" ]; then \
        pip install --no-cache-dir -i "$PIP_INDEX_URL" -r requirements.txt; \
    else \
        pip install --no-cache-dir -r requirements.txt; \
    fi

COPY app.py .

RUN mkdir -p /data/tiles/yn_img /data/tiles/cn_img /data/tiles/yn_vec /data/files
VOLUME ["/data"]

EXPOSE 8080
CMD ["python", "app.py"]
