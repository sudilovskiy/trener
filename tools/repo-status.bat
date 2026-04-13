@echo off
setlocal
powershell -ExecutionPolicy Bypass -File "%~dp0..\scripts\workspace-maintenance.ps1" -Action Status

