@echo off
setlocal enabledelayedexpansion

echo ===================================================
echo   Smart Event Ticket Booking ^& Sentry Pass System
echo ===================================================
echo.

REM 1. Try system PATH first
where javac >nul 2>&1
if %errorlevel% equ 0 (
    set "JAVAC_CMD=javac"
    set "JAVA_CMD=java"
    goto :compile
)

REM 2. Check Android Studio / JetBrains Runtime JBR (OpenJDK 21)
if exist "C:\Program Files\Android\Android Studio\jbr\bin\javac.exe" (
    set "JAVAC_CMD=C:\Program Files\Android\Android Studio\jbr\bin\javac.exe"
    set "JAVA_CMD=C:\Program Files\Android\Android Studio\jbr\bin\java.exe"
    goto :compile
)

REM 3. Check JAVA_HOME
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\javac.exe" (
        set "JAVAC_CMD=%JAVA_HOME%\bin\javac.exe"
        set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
        goto :compile
    )
)

REM 4. Check common Eclipse Adoptium / Temurin / Oracle paths
for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-21*" "C:\Program Files\Java\jdk-21*") do (
    if exist "%%D\bin\javac.exe" (
        set "JAVAC_CMD=%%D\bin\javac.exe"
        set "JAVA_CMD=%%D\bin\java.exe"
        goto :compile
    )
)

echo [ERROR] No Java Development Kit (JDK 21) found on your system.
echo Please install JDK 21 from: https://adoptium.net
echo.
pause
exit /b 1

:compile
echo [1/2] Compiling Java backend using:
echo       "%JAVAC_CMD%"
if not exist out mkdir out

"%JAVAC_CMD%" -d out -encoding UTF-8 src\exception\*.java src\model\*.java src\repository\*.java src\util\*.java src\service\*.java src\web\*.java src\WebMain.java
if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Compilation failed. Please check errors above.
    pause
    exit /b 1
)

echo [2/2] Compilation successful!
echo.
echo ===================================================
echo   Web Server starting on: http://localhost:8080
echo   Admin credentials:     admin@events.com / admin123
echo   Press Ctrl+C to stop.
echo ===================================================
echo.

"%JAVA_CMD%" -cp out WebMain
pause
