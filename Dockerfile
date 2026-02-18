FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/digicache-1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
# Provide sane defaults for containerized JVM memory and diagnostics.
# - Use G1 GC which is production-friendly for workloads of various sizes.
# - Use container support and limit RAM via MaxRAMPercentage (can be overridden by JAVA_OPTS at runtime).
# - Create heap dumps and GC logs on OOME to help troubleshooting.
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75 -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/heap.hprof -Xlog:gc*:file=/tmp/gc.log:time,level,tags:filecount=5,filesize=10240"

# Use shell form so the env var is expanded. Consumers can override JAVA_OPTS at deploy time.
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
