#!/usr/bin/env sh
set -eu
cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
if [ -n "${JAVA_HOME:-}" ]; then JAVA="$JAVA_HOME/bin/java"; else JAVA=java; fi
if ! command -v "$JAVA" >/dev/null 2>&1; then
  echo "Java 17 or later is required. Install a JDK, then try again." >&2
  exit 1
fi
if [ ! -f skillbridge.jar ] || [ ! -f lib/h2-2.3.232.jar ]; then
  echo "The application JAR or database driver is missing. Extract the entire ZIP, or run build.sh." >&2
  exit 1
fi
exec "$JAVA" --add-modules jdk.httpserver -cp "skillbridge.jar:lib/h2-2.3.232.jar" skillbridge.App
