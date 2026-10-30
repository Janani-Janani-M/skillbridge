@echo off
setlocal
cd /d "%~dp0"
if defined JAVA_HOME (set "JAVA=%JAVA_HOME%\bin\java.exe") else (set "JAVA=java")
"%JAVA%" -version >nul 2>&1
if errorlevel 1 (
  echo Java 17 or later is required. Install a JDK, then try again.
  pause
  exit /b 1
)
if not exist skillbridge.jar (
  echo Application JAR not found. Extract the entire ZIP or run build.bat.
  pause
  exit /b 1
)
if not exist lib\h2-2.3.232.jar (
  echo Database driver missing. Extract the entire ZIP.
  pause
  exit /b 1
)
"%JAVA%" --add-modules jdk.httpserver -cp "skillbridge.jar;lib\h2-2.3.232.jar" skillbridge.App
if errorlevel 1 pause
