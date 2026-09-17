#!/usr/bin/env bash
# ============================================================
#  music-website 音乐平台 一键启动脚本
#  后端  : Spring Boot 2.6 (Java 17)  端口 8888
#  用户端: music-client (Vue CLI)     端口 8080
#  管理端: music-manage (Vue CLI)     端口 8081
#  依赖服务:
#     MySQL 127.0.0.1:3306 (库 tp_music)
#     Redis 127.0.0.1:6379 + MinIO 127.0.0.1:9005 (本地自动启动)
#  停止: Ctrl+C 会同时停止全部服务
# ============================================================

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

echo "=============================================="
echo "  music-website 音乐平台 一键启动"
echo "=============================================="

# ---------- 1. 检查 Java ----------
if ! command -v java >/dev/null 2>&1; then
    echo "[ERROR] 未检测到 Java，请先安装 JDK 17+"
    exit 1
fi

# ---------- 2. 用户端依赖 ----------
if [[ ! -d "$ROOT_DIR/music-client/node_modules" ]]; then
    echo "[*] 首次运行，安装 music-client 依赖..."
    (cd "$ROOT_DIR/music-client" && npm install) || { echo "[ERROR] 用户端依赖安装失败"; exit 1; }
fi

# ---------- 3. 管理端依赖 ----------
if [[ ! -d "$ROOT_DIR/music-manage/node_modules" ]]; then
    echo "[*] 首次运行，安装 music-manage 依赖..."
    (cd "$ROOT_DIR/music-manage" && npm install) || { echo "[ERROR] 管理端依赖安装失败"; exit 1; }
fi

# ---------- 4. 启动 MinIO + Redis ----------
echo "[4/5] 启动 MinIO 对象存储..."
MINIO_DIR="/c/学习/minio/Minio"
if [[ ! -f "$MINIO_DIR/minio.exe" ]]; then
    echo "[WARN] 未找到 minio.exe，请确认路径: $MINIO_DIR"
elif pgrep -x minio >/dev/null 2>&1; then
    echo "[OK] MinIO 已在运行"
else
    export MINIO_ROOT_USER=root
    export MINIO_ROOT_PASSWORD=12345678
    (cd "$MINIO_DIR" && ./minio.exe server /d/develpo/minio/data \
        --console-address "127.0.0.1:9000" --address "127.0.0.1:9005") &
    echo "[OK] MinIO 已启动  API http://localhost:9005  控制台 http://localhost:9000"
fi

echo "[*] 启动 Redis..."
REDIS_DIR="/c/学习/Redis/Redis-x64-5.0.14.1"
if [[ ! -f "$REDIS_DIR/redis-server.exe" ]]; then
    echo "[WARN] 未找到 redis-server.exe，请确认路径: $REDIS_DIR"
    echo "[WARN] 后端仍可启动，但排行榜/抢票功能需要 Redis"
elif pgrep -x redis-server >/dev/null 2>&1; then
    echo "[OK] Redis 已在运行"
else
    (cd "$REDIS_DIR" && ./redis-server.exe redis.windows.conf) &
    echo "[OK] Redis 已启动  127.0.0.1:6379"
fi

# ---------- 5. 启动服务 (Ctrl+C 停止全部) ----------
trap 'echo "[+] 正在停止服务..."; kill 0 2>/dev/null; exit 0' INT TERM

# 优先使用系统 mvn，未安装则回退到 mvnw 包装器
if command -v mvn >/dev/null 2>&1; then
    MVN_CMD="mvn spring-boot:run"
else
    MVN_CMD="./mvnw spring-boot:run"
fi
(cd "$ROOT_DIR/music-server" && eval "$MVN_CMD") &
(cd "$ROOT_DIR/music-client" && npm run serve) &
(cd "$ROOT_DIR/music-manage" && npm run serve -- --port 8081) &

echo "后端 API:   http://localhost:8888"
echo "用户端前端: http://localhost:8080"
echo "管理端前端: http://localhost:8081"
echo "Ctrl+C 停止全部服务"

wait