@echo off
setlocal

set "ROOT=%~dp0.."
set "ADB="

for /f "usebackq tokens=1,* delims==" %%A in ("%ROOT%\local.properties") do (
    if /I "%%A"=="sdk.dir" set "SDK_DIR=%%B"
)

if not defined SDK_DIR (
    echo sdk.dir not found in local.properties
    exit /b 1
)

set "SDK_DIR=%SDK_DIR:\:=:%"
set "SDK_DIR=%SDK_DIR:\\=\%"
set "ADB=%SDK_DIR%\platform-tools\adb.exe"

if not exist "%ADB%" (
    echo adb not found: %ADB%
    exit /b 1
)

if not exist "%ROOT%\.tmp" mkdir "%ROOT%\.tmp"

echo Pulling app database from device...
"%ADB%" shell am force-stop com.example.trener
cmd /c ""%ADB%" exec-out run-as com.example.trener cat databases/trener_database > "%ROOT%\.tmp\trener_database""

echo Generating demo data...
python "%ROOT%\scripts\seed_demo_data.py" "%ROOT%\.tmp\trener_database" --days 10 --end-date 2026-04-13
if errorlevel 1 exit /b 1

echo Pushing demo database back to device...
"%ADB%" push "%ROOT%\.tmp\trener_database" /data/local/tmp/trener_database_demo >nul
"%ADB%" shell run-as com.example.trener rm -f databases/trener_database databases/trener_database-wal databases/trener_database-shm
"%ADB%" shell run-as com.example.trener cp /data/local/tmp/trener_database_demo databases/trener_database
"%ADB%" shell rm -f /data/local/tmp/trener_database_demo

echo Done. Restart the app on the device.
