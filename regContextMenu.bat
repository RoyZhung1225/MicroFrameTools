@echo off
setlocal

REM 右鍵背景會傳入目錄路徑到 %1（我們在 registry 用 "%V"）
set "WORKDIR=%~1"
if "%WORKDIR%"=="" set "WORKDIR=%CD%"

REM 切到工作目錄（可選，但通常符合直覺）
cd /d "%WORKDIR%"

REM 呼叫 PowerShell 執行 launcher.ps1（ps1 路徑用 bat 所在目錄）
powershell.exe -NoProfile -ExecutionPolicy Bypass -NoExit -File "%~dp0launcher.ps1" -WorkDir "%WORKDIR%"


endlocal
