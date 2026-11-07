FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    TZ=Asia/Shanghai

WORKDIR /srv
COPY requirements.txt .
# 用国内镜像可自行加 -i https://pypi.tuna.tsinghua.edu.cn/simple
RUN pip install --no-cache-dir -r requirements.txt

COPY app.py .

RUN mkdir -p /data/tiles/yn_img /data/tiles/cn_img /data/tiles/yn_vec
VOLUME ["/data"]

EXPOSE 8080
CMD ["python", "app.py"]
