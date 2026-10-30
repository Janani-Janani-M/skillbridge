#!/usr/bin/env sh
set -eu
cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
sh build.sh
if [ -n "${JAVA_HOME:-}" ]; then JAVA="$JAVA_HOME/bin/java"; else JAVA=java; fi
export JAVA
unset SB_ADMIN_PASSWORD
"$JAVA" --add-modules jdk.httpserver -cp "build/classes:lib/h2-2.3.232.jar" skillbridge.SelfTest
"$JAVA" --add-modules jdk.httpserver -cp "build/classes:lib/h2-2.3.232.jar" skillbridge.HandlerTest
if [ "${1:-}" = "--http" ]; then
  command -v python3 >/dev/null 2>&1 || { echo 'Python 3.9+ is required for the optional HTTP tests.' >&2; exit 1; }
  python3 tests/integration_test.py
fi
