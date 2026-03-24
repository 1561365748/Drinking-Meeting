@echo off
chcp 65001 >nul
title 奶茶君 - 一键启动

echo ========================================
echo       奶茶君 - 一键启动脚本
echo ========================================
echo.

REM 检查Java是否存在
java -version >nul 2>&1
if %errorlevel% equ 0 (
    echo [√] Java环境检测成功
    goto :start_app
)

echo [×] 未检测到Java环境
echo.
echo 正在尝试使用内嵌Java启动...
echo.

REM 检查Eclipse Adoptium安装
if exist "C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot"
    set "PATH=C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot\bin;%PATH%"
    echo [√] 使用已安装的Java 17环境
    goto :start_app
)

REM 检查是否有内嵌的Java
if exist "%~dp0jdk\bin\java.exe" (
    set "JAVA_HOME=%~dp0jdk"
    set "PATH=%~dp0jdk\bin;%PATH%"
    echo [√] 使用内嵌Java环境
    goto :start_app
)

echo.
echo ========================================
echo   需要安装Java才能运行本项目
echo ========================================
echo.
echo 请选择：
echo   1. 自动下载Java 17 (推荐)
echo   2. 手动下载安装
echo   3. 退出
echo.
set /p choice="请输入选项 (1/2/3): "

if "%choice%"=="1" goto :download_java
if "%choice%"=="2" goto :manual_download
goto :end

:download_java
echo.
echo [1/2] 正在下载Java 17...
powershell -Command "& {Invoke-WebRequest -Uri 'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.9%2B9/OpenJDK17U-jdk_x64_windows_hotspot_17.0.9_9.zip' -OutFile '%~dp0java.zip'}"

if not exist "%~dp0java.zip" (
    echo [×] 下载失败，请手动下载
    goto :manual_download
)

echo [2/2] 正在解压...
powershell -Command "& {Expand-Archive -Path '%~dp0java.zip' -DestinationPath '%~dp0' -Force}"
ren "%~dp0jdk-17.0.9+9" jdk
del "%~dp0java.zip"

set "JAVA_HOME=%~dp0jdk"
set "PATH=%~dp0jdk\bin;%PATH%"
echo [√] Java安装完成
goto :start_app

:manual_download
echo.
echo 请手动下载并安装Java 17:
echo 下载地址: https://adoptium.net/temurin/releases/?version=17
echo.
echo 安装完成后，重新运行此脚本。
pause
goto :end

:start_app
echo.
echo [*] 正在启动奶茶君...
echo [*] 请稍候，首次启动需要下载依赖...
echo.

cd /d "%~dp0"
call mvn spring-boot:run -DskipTests

echo.
echo 应用已停止运行
pause
goto :end

:end
