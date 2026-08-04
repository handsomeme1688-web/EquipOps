# 第一阶段 builder：只装依赖，体积隔离
FROM  python:3.12-slim AS builder
WORKDIR /build
COPY requirements.txt .
RUN pip install \
    --no-cache-dir \
    --default-timeout=180 \
    --retries=5 \
    --prefix=/install \
    -r requirements.txt

# 第二阶段 runtime
FROM  python:3.12-slim AS runtime
LABEL authors="handsomeme1688@gmail.com"
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app

COPY --from=builder /install /usr/local
# 应用代码
COPY app ./app
# 检索数据：向量库 + 分块语料 + 设备样例（容器内检索必需）
COPY data ./data

EXPOSE 8001
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8001/health')"
CMD ["uvicorn","app.main:app","--host","0.0.0.0","--port","8001"]
