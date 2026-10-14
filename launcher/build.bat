@echo off
rem Builds launcher\dist\HollowClient\HollowClient.exe
rem   1. creates a Python virtual environment in launcher\.venv (first run only)
rem   2. installs the pinned packages from requirements-dev.txt
rem   3. runs the tests
rem   4. packages the app with PyInstaller (the mod jar in launcher\bundled is included)
rem   5. starts the packaged app with --selftest to check it works
setlocal
cd /d "%~dp0"

set "PY=python"
where py >nul 2>nul && set "PY=py -3"

if not exist ".venv\Scripts\python.exe" (
    echo Creating the Python virtual environment...
    %PY% -m venv .venv || goto :fail
)

echo Installing packages...
".venv\Scripts\python.exe" -m pip install --disable-pip-version-check -q -r requirements-dev.txt || goto :fail

echo Running tests...
".venv\Scripts\python.exe" -m pytest -q || goto :fail

if not exist "bundled\hollowclient-*.jar" (
    echo NOTE: there is no mod jar in launcher\bundled. Use scripts\build_all.bat to build and bundle the mod.
)

echo Packaging with PyInstaller...
".venv\Scripts\python.exe" -m PyInstaller --noconfirm --clean hollow.spec || goto :fail

copy /y config.example.json "dist\HollowClient\config.example.json" >nul
if exist config.json copy /y config.json "dist\HollowClient\config.json" >nul

echo Checking the packaged app...
start "" /wait "dist\HollowClient\HollowClient.exe" --selftest
if errorlevel 1 (
    echo The packaged app failed its self-test.
    goto :fail
)

echo.
echo Done: launcher\dist\HollowClient\HollowClient.exe
exit /b 0

:fail
echo.
echo Build failed. See the messages above.
exit /b 1
