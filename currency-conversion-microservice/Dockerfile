FROM maven:3.9-eclipse-temurin-25-noble AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package && cp target/*.jar /app.jar

FROM eclipse-temurin:25-jre-noble
WORKDIR /app
COPY --from=build /app.jar ./app.jar
USER 10001:10001
EXPOSE 8100
ENTRYPOINT ["java", "-jar", "/app/app.jar"]