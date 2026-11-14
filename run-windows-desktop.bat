@echo off
chcp 65001 >nul
title PhonAcq Windows 桌面版启动与打包工具
echo ============================================================================
echo   PhonAcq - 实验语音学与方言调查采录系统 (Windows 桌面端构建工具)
echo   支持原生 Excel (.xlsx) 汇总、Praat (.TextGrid / .Collection) 与 PhonTracer (.teproj)
echo ============================================================================
echo.
echo   [1] 直接运行 Windows 桌面版 (run)
echo   [2] 生成免安装便携版 .exe 文件夹 (createDistributable)
echo   [3] 生成 Windows .msi 安装包 (packageMsi)
echo.
set /p choice="请输入选项序号 (默认 1): "
if "%choice%"=="" set choice=1

where gradle >nul 2>nul
if %errorlevel% neq 0 (
    echo [提示] 未检测到全局 gradle 命令，请确保已安装 Gradle 8.7+ 及 JDK 17+，
    echo        或者直接将本项目推送到 GitHub，通过 GitHub Actions 云端自动生成 .exe 和 .msi！
    pause
    exit /b 1
)

if "%choice%"=="1" (
    echo 正在启动 PhonAcq Windows 桌面版...
    gradle -p desktop-windows run
) else if "%choice%"=="2" (
    echo 正在打包免安装便携版 PhonAcq.exe ...
    gradle -p desktop-windows createDistributable
    echo.
    echo [完成] 免安装版已输出至: desktop-windows\build\compose\binaries\main\app\PhonAcq\PhonAcq.exe
    explorer.exe "desktop-windows\build\compose\binaries\main\app"
) else if "%choice%"=="3" (
    echo 正在生成 Windows .msi 安装程序...
    gradle -p desktop-windows packageMsi
    echo.
    echo [完成] MSI 安装包已输出至: desktop-windows\build\compose\binaries\main\msi\
    explorer.exe "desktop-windows\build\compose\binaries\main\msi"
)
pause
