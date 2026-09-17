#!/usr/bin/env bash
# ============================================================
#  music-website 前端启动脚本（不含后端）
#  用户端: music-client (Vue CLI)  端口 8080
#  管理端: music-manage (Vue CLI)  端口 8081
#  说明: 只启动两个前端，需要后端请运行 start-all.sh
#  停止: Ctrl+C 会同时停止全部服务
# ============================================================

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

echo "=============================================="
echo "  music-website 前端启动（不含后端）"
echo "=============================================="

if [[ ! -d "$ROOT_DIR/music-client/node_modules" ]]; then
    echo "[*] 首次运行，安装 music-client 依赖..."
    (cd "$ROOT_DIR/music-client" && npm install) || { echo "[ERROR] 用户端依赖安装失败"; exit 1; }
fi

if [[ ! -d "$ROOT_DIR/music-manage/node_modules" ]]; then
    echo "[*] 首次运行，安装 music-manage 依赖..."
    (cd "$ROOT_DIR/music-manage" && npm install) || { echo "[ERROR] 管理端依赖安装失败"; exit 1; }
fi

trap 'echo "[+] 正在停止服务..."; kill 0 2>/dev/null; exit 0' INT TERM

(cd "$ROOT_DIR/music-client" && npm run serve) &
(cd "$ROOT_DIR/music-manage" && npm run serve -- --port 8081) &

echo "用户端前端: http://localhost:8080"
echo "管理端前端: http://localhost:8081"
echo "后端需单独启动，见 start-all.sh"
echo "Ctrl+C 停止全部服务"

wait