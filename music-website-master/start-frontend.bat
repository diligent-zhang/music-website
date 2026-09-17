@echo off
chcp 65001 >nul
setlocal

REM ============================================================
REM  music-website 前端启动脚本（不含后端）
REM  用户端: music-client (Vue CLI)  端口 8080
REM  管理端: music-manage (Vue CLI)  端口 8081
REM  说明: 本脚本只启动两个前端，需要后端请运行 start-all.bat
REM ============================================================

set "ROOT_DIR=%~dp0"
if "%ROOT_DIR:~-1%"=="\" set "ROOT_DIR=%ROOT_DIR:~0,-1%"
cd /d "%ROOT_DIR%"

echo ==============================================
echo   music-website 前端启动（不含后端）
echo ==============================================

REM ---------- 1. 用户端依赖 ----------
echo [1/3] 准备 music-client 依赖...
if not exist "%ROOT_DIR%\music-client\node_modules" (
    echo [*] 首次运行，安装 music-client 依赖...
    pushd "%ROOT_DIR%\music-client"
    call npm install
    popd
    if errorlevel 1 (
        echo [ERROR] 用户端依赖安装失败
        pause
        exit /b 1
    )
)

REM ---------- 2. 管理端依赖 ----------
echo [2/3] 准备 music-manage 依赖...
if not exist "%ROOT_DIR%\music-manage\node_modules" (
    echo [*] 首次运行，安装 music-manage 依赖...
    pushd "%ROOT_DIR%\music-manage"
    call npm install
    popd
    if errorlevel 1 (
        echo [ERROR] 管理端依赖安装失败
        pause
        exit /b 1
    )
)

REM ---------- 3. 启动前端 ----------
echo [3/3] 启动前端...
echo.

REM 使用 pushd/popd 避免路径中的空格问题
pushd "%ROOT_DIR%\music-client"
start "music-client(8080)" cmd /k "npm run serve"
popd

pushd "%ROOT_DIR%\music-manage"
start "music-manage(8081)" cmd /k "npm run serve -- --port 8081"
popd

echo.
echo ==============================================
echo   前端已启动，请稍等几秒待就绪：
echo.
echo     用户端前端:   http://localhost:8080
echo     管理端前端:   http://localhost:8081
echo.
echo   后端需单独启动，见 start-all.bat
echo ==============================================
echo.
pause
endlocal