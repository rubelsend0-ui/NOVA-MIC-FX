#!/bin/sh
# Minimal Gradle wrapper launcher. The CI workflow replaces this file (and adds
# gradle/wrapper/gradle-wrapper.jar) with the official Gradle 8.7 wrapper if the jar is missing.
app_path=$0
while [ -h "$app_path" ]; do
    ls=$(ls -ld "$app_path"); link=${ls#*' -> '}
    case $link in /*) app_path=$link ;; *) app_path=$(dirname "$app_path")/$link ;; esac
done
APP_HOME=$(cd "$(dirname "$app_path")" > /dev/null && pwd -P) || exit
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then JAVACMD=$JAVA_HOME/bin/java; else JAVACMD=java; fi
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
if [ ! -f "$CLASSPATH" ]; then
    echo "ERROR: gradle/wrapper/gradle-wrapper.jar is missing." >&2
    echo "Run 'gradle wrapper --gradle-version 8.7' once (or open the project in Android Studio)." >&2
    exit 1
fi
exec "$JAVACMD" -Xmx64m -Xms64m -Dorg.gradle.appname=gradlew -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
