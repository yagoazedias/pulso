FROM clojure:lein-jammy AS builder
WORKDIR /app
COPY project.clj .
RUN lein deps
COPY . .
RUN lein uberjar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/pulso-*-standalone.jar app.jar
ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]
