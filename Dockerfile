FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY . .
RUN ./mvnw -B -q -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /src/connector-bootstrap/target/connector-bootstrap-*.jar app.jar
EXPOSE 8080
# Let the heap use most of the container memory (the JVM default is only 25%)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
