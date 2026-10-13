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
ENTRYPOINT ["sh","-c","if [ -n \"$DB_HOST\" ]; then export SPRING_DATASOURCE_URL=\"jdbc:postgresql://$DB_HOST:$DB_PORT/$DB_NAME\"; export SPRING_DATASOURCE_DRIVER=org.postgresql.Driver; fi; exec java -jar /app/collabnotes.jar"]