#!/bin/sh
# UniBoard Gradle wrapper（GPL-3.0-or-later）
APP_HOME=$(cd "$(dirname "$0")" && pwd)
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ -n "$JAVA_HOME" ]; then
  JAVACMD="$JAVA_HOME/bin/java"
else
  JAVACMD=java
fi
if ! command -v "$JAVACMD" >/dev/null 2>&1; then
  echo "错误：找不到 java，请先安装 JDK 17。" >&2
  exit 1
fi
exec "$JAVACMD" $DEFAULT_JVM_OPTS $JAVA_OPTS \
  -Dorg.gradle.appname=gradlew \
  -classpath "$CLASSPATH" \
  org.gradle.wrapper.GradleWrapperMain "$@"
