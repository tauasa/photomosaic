@echo off
rem ============================================================
rem  Start Photomosaic.
rem    run.bat              build (if needed) and run
rem    run.bat --rebuild    force a clean rebuild first
rem
rem  Override JVM options (e.g. more heap) before launching:
rem    set PHOTOMOSAIC_JAVA_OPTS=-Xmx4g
rem    run.bat
rem ============================================================
setlocal

rem Work from this script's directory.
cd /d "%~dp0"

set "JAR=target\photomosaic.jar"
if not defined PHOTOMOSAIC_JAVA_OPTS set "PHOTOMOSAIC_JAVA_OPTS="

rem Prefer JAVA_HOME if set, else fall back to PATH.
if defined JAVA_HOME (
    set "JAVA=%JAVA_HOME%\bin\java.exe"
) else (
    set "JAVA=java"
)
"%JAVA%" -version >nul 2>nul
if errorlevel 1 (
    echo Error: Java not found. Install a JDK 17+ ^(https://adoptium.net^) and try again.
    exit /b 1
)

rem Optional forced rebuild.
if /I "%~1"=="--rebuild" (
    if exist "%JAR%" del /q "%JAR%"
)

rem Build the fat jar on first run (or after --rebuild).
if not exist "%JAR%" (
    echo Building %JAR% ^(first run, this may take a minute^)...
    where mvn >nul 2>nul
    if errorlevel 1 (
        echo Error: Maven not found and %JAR% isn't built.
        echo Install Maven ^(https://maven.apache.org^) or run "mvn clean package" once.
        exit /b 1
    )
    call mvn -q clean package
    if errorlevel 1 (
        echo Build failed.
        exit /b 1
    )
)

echo Starting Photomosaic...
"%JAVA%" %PHOTOMOSAIC_JAVA_OPTS% -jar "%JAR%"

endlocal
