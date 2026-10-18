# syntax=docker/dockerfile:1
# ---------- Etape 1 : build Maven (aucun Maven local requis) ----------
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

# Dependances d'abord : couche mise en cache tant que le pom ne change pas.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Les tests metier tournent sur H2 pendant le build ; SKIP_TESTS=true pour les sauter.
ARG SKIP_TESTS=false
RUN mvn -B -q package -DskipTests=${SKIP_TESTS}

# ---------- Etape 2 : image d'execution (JRE seul) ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 notarius
COPY --from=build /app/target/notarius-backend-*.jar app.jar
USER notarius
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
