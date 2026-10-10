# syntax=docker/dockerfile:1
# Two-stage build: Maven resolves deps in stage 1, then we ship a slim JRE-only runtime.
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
CMD ["java", "-jar", "/app/collabnotes.jar"]