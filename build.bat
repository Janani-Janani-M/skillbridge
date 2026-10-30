@echo off
setlocal
cd /d "%~dp0"
if defined JAVA_HOME (
  set "JAVAC=%JAVA_HOME%\bin\javac.exe"
  set "JAR=%JAVA_HOME%\bin\jar.exe"
) else (
  set "JAVAC=javac"
  set "JAR=jar"
)
"%JAVAC%" -version >nul 2>&1
if errorlevel 1 (
  echo Install a full JDK 17 or later to build.
  exit /b 1
)
if not exist build\classes mkdir build\classes
(for /r src %%f in (*.java) do @echo "%%f") > build\sources.txt
"%JAVAC%" --release 17 --add-modules jdk.httpserver -encoding UTF-8 -d build\classes @build\sources.txt
if errorlevel 1 exit /b 1
"%JAR%" --create --file skillbridge.jar --main-class skillbridge.App -C build\classes skillbridge
if errorlevel 1 exit /b 1
echo Build complete. Run run.bat.
