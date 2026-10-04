@echo off
cd /d "%~dp0"
if exist "%~dp0release\MicroGest\MicroGest.exe" (
    start "" "%~dp0release\MicroGest\MicroGest.exe"
    exit /b 0
)
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0microgest.ps1" run
if errorlevel 1 pause
