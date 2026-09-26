#!/bin/sh
# Generated-style Gradle wrapper launcher.
# If gradle-wrapper.jar is missing, open this project in Android Studio
# or run: gradle wrapper --gradle-version 8.7

dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
jar="$dir/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$jar" ]; then
  echo "Missing $jar"
  echo "Open in Android Studio (it will generate the wrapper), or run:"
  echo "  gradle wrapper --gradle-version 8.7"
  exit 1
fi

# Resolve Java
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVACMD="$JAVA_HOME/bin/java"
else
  JAVACMD=java
fi

exec "$JAVACMD" \
  -classpath "$jar" \
  org.gradle.wrapper.GradleWrapperMain \
  "$@"
