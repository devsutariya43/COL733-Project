#!/bin/bash

# Function to find Java installation path
find_java_home() {
    if [ -n "$JAVA_HOME" ]; then
        echo "$JAVA_HOME"
    else
        java_path=$(which java)
        if [ -n "$java_path" ]; then
            # Extract the path by following symbolic links
            real_path=$(readlink -f "$java_path")
            echo "$(dirname $(dirname "$real_path"))"
        else
            echo "Java not found. Please install Java or set JAVA_HOME."
            exit 1
        fi
    fi
}

# Detect Java installation
JAVA_HOME=$(find_java_home)
JAVA_BIN="$JAVA_HOME/bin/java"

# Verify Java installation
if [ ! -x "$JAVA_BIN" ]; then
    echo "Java executable not found at $JAVA_BIN. Please check your Java installation."
    exit 1
fi

# Set path to the compiled classes
PROJECT_DIR="."
TARGET_CLASSES="$PROJECT_DIR/target/classes"

# Set the classpath (add dependencies as needed)
CLASSPATH="$TARGET_CLASSES:$HOME/.m2/repository/org/apache/ignite/ignite-core/2.16.0/ignite-core-2.16.0.jar:\
$HOME/.m2/repository/javax/cache/cache-api/1.0.0/cache-api-1.0.0.jar:\
$HOME/.m2/repository/org/jetbrains/annotations/16.0.3/annotations-16.0.3.jar"

MAIN_CLASS="com.ignite.scalabilityTest.ScalabilityTestApplication"

# Java options for necessary module access
JAVA_OPTS="--add-opens=java.base/jdk.internal.access=ALL-UNNAMED \
--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED \
--add-opens=java.base/sun.nio.ch=ALL-UNNAMED \
--add-opens=java.base/sun.util.calendar=ALL-UNNAMED \
--add-opens=java.management/com.sun.jmx.mbeanserver=ALL-UNNAMED \
--add-opens=jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED \
--add-opens=java.base/sun.reflect.generics.reflectiveObjects=ALL-UNNAMED \
--add-opens=jdk.management/com.sun.management.internal=ALL-UNNAMED \
--add-opens=java.base/java.io=ALL-UNNAMED \
--add-opens=java.base/java.nio=ALL-UNNAMED \
--add-opens=java.base/java.net=ALL-UNNAMED \
--add-opens=java.base/java.util=ALL-UNNAMED \
--add-opens=java.base/java.util.concurrent=ALL-UNNAMED \
--add-opens=java.base/java.util.concurrent.locks=ALL-UNNAMED \
--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED \
--add-opens=java.base/java.lang=ALL-UNNAMED \
--add-opens=java.base/java.lang.invoke=ALL-UNNAMED \
--add-opens=java.base/java.math=ALL-UNNAMED \
--add-opens=java.sql/java.sql=ALL-UNNAMED \
--add-opens=java.base/java.lang.reflect=ALL-UNNAMED \
--add-opens=java.base/java.time=ALL-UNNAMED \
--add-opens=java.base/java.text=ALL-UNNAMED \
--add-opens=java.management/sun.management=ALL-UNNAMED \
--add-opens=java.desktop/java.awt.font=ALL-UNNAMED"

# Run the Java application
echo "Running Java application using Java at $JAVA_BIN"

mvn clean compile
$JAVA_BIN $JAVA_OPTS -Dfile.encoding=UTF-8 -classpath $CLASSPATH $MAIN_CLASS

