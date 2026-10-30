#!/usr/bin/env sh
set -eu
cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
if [ -n "${JAVA_HOME:-}" ]; then JAVAC="$JAVA_HOME/bin/javac"; JAR="$JAVA_HOME/bin/jar"; else JAVAC=javac; JAR=jar; fi
command -v "$JAVAC" >/dev/null 2>&1 || { echo 'Install a full JDK 17+ to build.' >&2; exit 1; }
mkdir -p build/classes
find src/main/java src/test/java -name '*.java' | sort > build/sources.txt
"$JAVAC" --release 17 --add-modules jdk.httpserver -encoding UTF-8 -d build/classes @build/sources.txt
"$JAR" --create --file skillbridge.jar --main-class skillbridge.App -C build/classes skillbridge
printf '\nBuild complete. Run sh run.sh.\n'
