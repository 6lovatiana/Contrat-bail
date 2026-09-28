FROM maven:3.9.11-eclipse-temurin-17 AS build

WORKDIR /app
COPY pom.xml .

COPY src ./src
COPY template ./template
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
COPY --from=build /app/template ./template

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]