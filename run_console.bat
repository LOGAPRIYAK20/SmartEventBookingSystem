@echo off
setlocal enabledelayedexpansion

echo ===================================================
echo   Smart Event Ticket Booking - Console Mode
echo ===================================================
echo.

where javac >nul 2>&1
if %errorlevel% equ 0 (
    set "JAVA_CMD=java"
    goto :run
)

if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" (
    set "JAVA_CMD=C:\Program Files\Android\Android Studio\jbr\bin\java.exe"
    goto :run
)

if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
        goto :run
    )
)

echo [ERROR] Java not found.
pause
exit /b 1

:run
"%JAVA_CMD%" -cp out Main
pause
