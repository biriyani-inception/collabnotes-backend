# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q -e -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -q -ntp -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/collabnotes.jar /app/collabnotes.jar
EXPOSE 10000
ENV PORT=10000

# Memory-constrained hosts (free container tiers are commonly capped at
# 256-512MB total) need explicit heap/metaspace/thread-stack caps, or the
# JVM's default ergonomics (heap sized off the *host's* total RAM, which a
# container can't see accurately) will try to grow past the cgroup limit
# and get OOM-killed. Tuned for a ~256MB container with headroom for the
# OS/container overhead; override via JAVA_OPTS on a host with more RAM.
ENV JAVA_OPTS="-Xmx160m -Xss256k -XX:MaxMetaspaceSize=80m -XX:MaxDirectMemorySize=16m -XX:ReservedCodeCacheSize=32m -XX:+UseSerialGC"

ENTRYPOINT ["sh","-c","if [ -n \"$DB_HOST\" ]; then export SPRING_DATASOURCE_URL=\"jdbc:postgresql://$DB_HOST:$DB_PORT/$DB_NAME\"; export SPRING_DATASOURCE_DRIVER=org.postgresql.Driver; fi; exec java $JAVA_OPTS -jar /app/collabnotes.jar"]