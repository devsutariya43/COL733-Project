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

CLASSPATH="$TARGET_CLASSES:$HOME/.m2/repository/org/springframework/boot/spring-boot-starter/3.3.5/spring-boot-starter-3.3.5.jar:\
$HOME/.m2/repository/org/springframework/boot/spring-boot/3.3.5/spring-boot-3.3.5.jar:\
$HOME/.m2/repository/org/springframework/spring-context/6.1.14/spring-context-6.1.14.jar:\
$HOME/.m2/repository/org/springframework/spring-aop/6.1.14/spring-aop-6.1.14.jar:\
$HOME/.m2/repository/org/springframework/spring-beans/6.1.14/spring-beans-6.1.14.jar:\
$HOME/.m2/repository/org/springframework/spring-expression/6.1.14/spring-expression-6.1.14.jar:\
$HOME/.m2/repository/io/micrometer/micrometer-observation/1.13.6/micrometer-observation-1.13.6.jar:\
$HOME/.m2/repository/io/micrometer/micrometer-commons/1.13.6/micrometer-commons-1.13.6.jar:\
$HOME/.m2/repository/org/springframework/boot/spring-boot-autoconfigure/3.3.5/spring-boot-autoconfigure-3.3.5.jar:\
$HOME/.m2/repository/org/springframework/boot/spring-boot-starter-logging/3.3.5/spring-boot-starter-logging-3.3.5.jar:\
$HOME/.m2/repository/ch/qos/logback/logback-classic/1.5.11/logback-classic-1.5.11.jar:\
$HOME/.m2/repository/ch/qos/logback/logback-core/1.5.11/logback-core-1.5.11.jar:\
$HOME/.m2/repository/org/apache/logging/log4j/log4j-to-slf4j/2.23.1/log4j-to-slf4j-2.23.1.jar:\
$HOME/.m2/repository/org/apache/logging/log4j/log4j-api/2.23.1/log4j-api-2.23.1.jar:\
$HOME/.m2/repository/org/slf4j/jul-to-slf4j/2.0.16/jul-to-slf4j-2.0.16.jar:\
$HOME/.m2/repository/jakarta/annotation/jakarta.annotation-api/2.1.1/jakarta.annotation-api-2.1.1.jar:\
$HOME/.m2/repository/org/springframework/spring-core/6.1.14/spring-core-6.1.14.jar:\
$HOME/.m2/repository/org/springframework/spring-jcl/6.1.14/spring-jcl-6.1.14.jar:\
$HOME/.m2/repository/org/yaml/snakeyaml/2.2/snakeyaml-2.2.jar:\
$HOME/.m2/repository/org/slf4j/slf4j-api/2.0.16/slf4j-api-2.0.16.jar:\
$HOME/.m2/repository/org/apache/ignite/ignite-core/2.16.0/ignite-core-2.16.0.jar:\
$HOME/.m2/repository/javax/cache/cache-api/1.1.1/cache-api-1.1.1.jar:"

# JVM options
JVM_OPTS=(
  "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED"
  "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED"
  "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED"
  "--add-opens=java.base/sun.util.calendar=ALL-UNNAMED"
  "--add-opens=java.management/com.sun.jmx.mbeanserver=ALL-UNNAMED"
  "--add-opens=jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED"
  "--add-opens=java.base/sun.reflect.generics.reflectiveObjects=ALL-UNNAMED"
  "--add-opens=jdk.management/com.sun.management.internal=ALL-UNNAMED"
  "--add-opens=java.base/java.io=ALL-UNNAMED"
  "--add-opens=java.base/java.nio=ALL-UNNAMED"
  "--add-opens=java.base/java.net=ALL-UNNAMED"
  "--add-opens=java.base/java.util=ALL-UNNAMED"
  "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED"
  "--add-opens=java.base/java.util.concurrent.locks=ALL-UNNAMED"
  "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED"
  "--add-opens=java.base/java.lang=ALL-UNNAMED"
  "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED"
  "--add-opens=java.base/java.math=ALL-UNNAMED"
  "--add-opens=java.sql/java.sql=ALL-UNNAMED"
  "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED"
  "--add-opens=java.base/java.time=ALL-UNNAMED"
  "--add-opens=java.base/java.text=ALL-UNNAMED"
  "--add-opens=java.management/sun.management=ALL-UNNAMED"
  "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED"
  "-Dfile.encoding=UTF-8"
)

# Main class
MAIN_CLASS="com.ignite.CAPTester.CAPTester.CapTesterApplication"

mvn clean compile

# Execute the Java application
$JAVA_BIN "${JVM_OPTS[@]}" -classpath "$CLASSPATH" "$MAIN_CLASS"
