@echo off
setlocal

for %%I in ("%~dp0..\SJMC_geyser-test-server") do set "SERVER_ROOT=%%~fI"
if not exist "%SERVER_ROOT%\start.bat" (
    echo Test server launcher not found:
    echo %SERVER_ROOT%\start.bat
    pause
    exit /b 1
)

call "%SERVER_ROOT%\start.bat"
endlocal
