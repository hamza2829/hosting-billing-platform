FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY common common
COPY billing-service billing-service
COPY payment-service payment-service
COPY audit-search-service audit-search-service
ARG SERVICE
RUN mvn -B -ntp -pl ${SERVICE} -am package -DskipTests
RUN cp ${SERVICE}/target/${SERVICE}-1.0.0.jar /service.jar
FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && useradd --uid 10001 worker
WORKDIR /app
COPY --from=build /service.jar app.jar
USER worker
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65"
ENTRYPOINT ["java","-jar","app.jar"]
