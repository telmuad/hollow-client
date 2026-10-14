@echo off
rem Starts the launcher straight from the source code (no exe needed).
rem Run launcher\build.bat (or scripts\build_all.bat) once first, so the Python environment exists.
cd /d "%~dp0..\launcher"
if not exist ".venv\Scripts\pythonw.exe" (
    echo The Python environment doesn't exist yet. Run launcher\build.bat once first.
    pause
    exit /b 1
)
start "" ".venv\Scripts\pythonw.exe" -m hollow_launcher
