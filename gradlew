#!/bin/sh
#
# Gradle start up script for UN*X
#
GRADLE_OPTS="${GRADLE_OPTS:-"-Dfile.encoding=UTF-8"}"
APP_HOME="$(cd "$(dirname "$0")" && pwd)"
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
exec java $GRADLE_OPTS -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
