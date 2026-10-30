@echo off
setlocal
cd /d "%~dp0"
call build.bat
if errorlevel 1 exit /b 1
if defined JAVA_HOME (set "JAVA=%JAVA_HOME%\bin\java.exe") else (set "JAVA=java")
set "SB_ADMIN_PASSWORD="
"%JAVA%" --add-modules jdk.httpserver -cp "build\classes;lib\h2-2.3.232.jar" skillbridge.SelfTest
if errorlevel 1 exit /b 1
"%JAVA%" --add-modules jdk.httpserver -cp "build\classes;lib\h2-2.3.232.jar" skillbridge.HandlerTest
if errorlevel 1 exit /b 1
if "%~1"=="--http" (
  python tests\integration_test.py
  if errorlevel 1 exit /b 1
)
