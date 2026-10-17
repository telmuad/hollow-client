@echo off
rem Builds everything:
rem   1. the Hollow Client mod for 26.2 (Gradle, main branch) -> mod\build\libs\hollowclient-<version>.jar
rem   2. the frozen 1.21.11 mod from the legacy/1.21.11 branch, in a git worktree under build\legacy
rem                                                             -> build\legacy\mod\build\libs\hollowclient-<version>.jar
rem   3. copies both jars into launcher\bundled (the launcher matches them to profiles by their Minecraft version)
rem   4. the launcher (launcher\build.bat)                       -> launcher\dist\HollowClient\HollowClient.exe
setlocal
cd /d "%~dp0.."

echo.
echo [1/4] Building the Hollow Client mod (26.2)...
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
echo [2/4] Building the legacy Hollow Client mod (1.21.11)...
if not exist "build\legacy\mod\gradlew.bat" (
    git worktree prune
    git worktree add build\legacy legacy/1.21.11
    if errorlevel 1 goto :fail
)
del /q "build\legacy\mod\build\libs\hollowclient-*.jar" 2>nul
pushd build\legacy\mod
call .\gradlew.bat build
if errorlevel 1 (
    popd
    goto :fail
)
popd

echo.
echo [3/4] Copying the mod jars into the launcher...
if not exist "launcher\bundled" mkdir "launcher\bundled"
del /q "launcher\bundled\hollowclient-*.jar" 2>nul
for %%f in ("mod\build\libs\hollowclient-*.jar" "build\legacy\mod\build\libs\hollowclient-*.jar") do (
    echo %%~nxf | findstr /i /c:"-sources" >nul || copy /y "%%f" "launcher\bundled\" >nul
)
if not exist "launcher\bundled\hollowclient-*.jar" (
    echo No mod jar was found.
    goto :fail
)
dir /b "launcher\bundled\hollowclient-*.jar"

echo.
echo [4/4] Building the launcher...
call launcher\build.bat
if errorlevel 1 goto :fail

echo.
echo All done.
echo   Mod jars: mod\build\libs\ and build\legacy\mod\build\libs\
echo   Launcher: launcher\dist\HollowClient\HollowClient.exe
exit /b 0

:fail
echo.
echo Build failed. See the messages above.
exit /b 1
