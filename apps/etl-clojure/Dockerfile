FROM clojure:lein-2.11.2-jammy AS builder
WORKDIR /app
COPY project.clj .
RUN lein deps
COPY . .
RUN lein uberjar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/uberjar/pulso-*-standalone.jar app.jar
ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]
