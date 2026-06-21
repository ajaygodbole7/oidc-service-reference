# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:26-jdk-jammy AS build

ARG MAVEN_VERSION=3.9.11
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates && \
    curl -fsSL "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
      | tar -xz -C /opt && \
    ln -s /opt/apache-maven-${MAVEN_VERSION}/bin/mvn /usr/local/bin/mvn && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

COPY pom.xml ./
# The root reactor lists every module, so all module POMs must be present for Maven
# to parse it — even though `-pl catalog-service -am` only builds catalog + its deps.
COPY commerce-security-common/pom.xml commerce-security-common/pom.xml
COPY commerce-web-starter/pom.xml commerce-web-starter/pom.xml
COPY cart-service/pom.xml cart-service/pom.xml
COPY order-service/pom.xml order-service/pom.xml
COPY payment-service/pom.xml payment-service/pom.xml
COPY catalog-service/pom.xml catalog-service/pom.xml
RUN mvn -B -q -f pom.xml -pl catalog-service -am -DskipTests dependency:go-offline || true

COPY commerce-security-common/src commerce-security-common/src
COPY commerce-web-starter/src commerce-web-starter/src
COPY catalog-service/src catalog-service/src
RUN mvn -B -q -f pom.xml -pl catalog-service -am -DskipTests package

FROM eclipse-temurin:26-jre-jammy

RUN apt-get update && apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/* && \
    groupadd --system spring && useradd --system --gid spring spring

USER spring:spring
WORKDIR /app

COPY --from=build --chown=spring:spring /workspace/catalog-service/target/*-SNAPSHOT.jar /app/app.jar

EXPOSE 8084 9084

ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseG1GC", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "/app/app.jar"]
