@echo off
rem Builds everything:
rem   1. the Hollow Client mod (Gradle)            -> mod\build\libs\hollowclient-<version>.jar
rem   2. copies that jar into launcher\bundled
rem   3. the launcher (launcher\build.bat)          -> launcher\dist\HollowClient\HollowClient.exe
setlocal
cd /d "%~dp0.."

echo.
echo [1/3] Building the Hollow Client mod...
rem remove jars from earlier versions so only the new one gets bundled
del /q "mod\build\libs\hollowclient-*.jar" 2>nul
pushd mod
call .\gradlew.bat build
if errorlevel 1 (
    popd
    goto :fail
)
popd

echo.
echo [2/3] Copying the mod jar into the launcher...
if not exist "launcher\bundled" mkdir "launcher\bundled"
del /q "launcher\bundled\hollowclient-*.jar" 2>nul
for %%f in ("mod\build\libs\hollowclient-*.jar") do (
    echo %%~nxf | findstr /i /c:"-sources" >nul || copy /y "%%f" "launcher\bundled\" >nul
)
if not exist "launcher\bundled\hollowclient-*.jar" (
    echo No mod jar was found in mod\build\libs.
    goto :fail
)

echo.
echo [3/3] Building the launcher...
call launcher\build.bat
if errorlevel 1 goto :fail

echo.
echo All done.
echo   Mod jar:  mod\build\libs\
echo   Launcher: launcher\dist\HollowClient\HollowClient.exe
exit /b 0

:fail
echo.
echo Build failed. See the messages above.
exit /b 1
