@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

REM ============================================================
REM  music-website 音乐平台 一键启动脚本
REM  后端  : Spring Boot 2.6 (Java 17)  端口 8888
REM  用户端: music-client (Vue CLI)     端口 8080
REM  管理端: music-manage (Vue CLI)     端口 8081
REM  依赖服务请先手动启动:
REM     MySQL 127.0.0.1:3306 (库 tp_music)
REM  本地自动启动: Redis 127.0.0.1:6379 + MinIO 127.0.0.1:9005
REM ============================================================

set "ROOT_DIR=%~dp0"
if "%ROOT_DIR:~-1%"=="\" set "ROOT_DIR=%ROOT_DIR:~0,-1%"
cd /d "%ROOT_DIR%"

echo ==============================================
echo   music-website 音乐平台 一键启动
echo ==============================================

REM ---------- 1. 检查 Java 环境 ----------
echo [1/5] 检查 Java 环境...
java -version >nul 2>nul
if errorlevel 1 (
    echo [ERROR] 未检测到 Java，请先安装 JDK 17+ 并配置 JAVA_HOME
    pause
    exit /b 1
)

REM ---------- 2. 用户端依赖 ----------
echo [2/5] 准备 music-client 依赖...
if not exist "%ROOT_DIR%\music-client\node_modules" (
    echo [*] 首次运行，安装 music-client 依赖...
    pushd "%ROOT_DIR%\music-client"
    call npm install
    popd
    if errorlevel 1 (
        echo [ERROR] 用户端依赖安装失败，请确认已安装 Node.js 16+
        pause
        exit /b 1
    )
)

REM ---------- 3. 管理端依赖 ----------
echo [3/5] 准备 music-manage 依赖...
if not exist "%ROOT_DIR%\music-manage\node_modules" (
    echo [*] 首次运行，安装 music-manage 依赖...
    pushd "%ROOT_DIR%\music-manage"
    call npm install
    popd
    if errorlevel 1 (
        echo [ERROR] 管理端依赖安装失败，请确认已安装 Node.js 16+
        pause
        exit /b 1
    )
)

REM ---------- 4. 启动 MinIO + Redis ----------
echo [4/5] 启动 MinIO 对象存储...
set "MINIO_DIR=C:\学习\minio\Minio"
if not exist "%MINIO_DIR%\minio.exe" (
    echo [WARN] 未找到 minio.exe，请确认路径: %MINIO_DIR%
) else (
    tasklist /FI "IMAGENAME eq minio.exe" | findstr /I "minio.exe" >nul 2>nul
    if not errorlevel 1 (
        echo [OK] MinIO 已在运行
    ) else (
        set MINIO_ROOT_USER=root
        set MINIO_ROOT_PASSWORD=12345678
        start "MinIO(9005)" cmd /k "cd /d "%MINIO_DIR%" && minio.exe server D:\develpo\minio\data --console-address "127.0.0.1:9000" --address "127.0.0.1:9005""
        echo [OK] MinIO 已启动  API http://localhost:9005  控制台 http://localhost:9000
    )
)

echo [*] 启动 Redis...
set "REDIS_DIR=C:\学习\Redis\Redis-x64-5.0.14.1"
if not exist "%REDIS_DIR%\redis-server.exe" (
    echo [WARN] 未找到 redis-server.exe，请确认路径: %REDIS_DIR%
    echo [WARN] 后端仍可启动，但排行榜/抢票功能需要 Redis
) else (
    tasklist /FI "IMAGENAME eq redis-server.exe" | findstr /I "redis-server.exe" >nul 2>nul
    if not errorlevel 1 (
        echo [OK] Redis 已在运行
    ) else (
        start "Redis(6379)" cmd /k "cd /d "%REDIS_DIR%" && redis-server.exe redis.windows.conf"
        echo [OK] Redis 已启动  127.0.0.1:6379
    )
)

REM ---------- 5. 启动服务 ----------
echo [5/5] 启动服务...
echo.

REM 优先使用系统 mvn，未安装则回退到 mvnw 包装器
where mvn >nul 2>nul
if errorlevel 1 (
    set "MVN_CMD=%ROOT_DIR%\music-server\mvnw.cmd spring-boot:run"
) else (
    set "MVN_CMD=mvn spring-boot:run"
)

REM 使用 pushd/popd 避免路径中的空格问题
pushd "%ROOT_DIR%\music-server"
start "music-server(8888)" cmd /k "!MVN_CMD!"
popd

pushd "%ROOT_DIR%\music-client"
start "music-client(8080)" cmd /k "npm run serve"
popd

pushd "%ROOT_DIR%\music-manage"
start "music-manage(8081)" cmd /k "npm run serve -- --port 8081"
popd

echo.
echo ==============================================
echo   服务已启动，请稍等几秒待就绪：
echo.
echo     后端 API:     http://localhost:8888
echo     用户端前端:   http://localhost:8080
echo     管理端前端:   http://localhost:8081
echo.
echo   前置依赖（需先手动启动）:
echo     MySQL : 127.0.0.1:3306  数据库 tp_music
echo     Redis : 127.0.0.1:6379
echo     MinIO : 127.0.0.1:9005  控制台 http://localhost:9000
echo ==============================================
echo.
pause
endlocal