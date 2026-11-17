FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -DskipTests dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -DskipTests clean package

FROM eclipse-temurin:21-jre

RUN useradd --system --create-home --uid 10001 agroconecta
WORKDIR /app

COPY --from=build /workspace/target/citizentech-api-0.0.1-SNAPSHOT.jar app.jar

USER agroconecta
EXPOSE 10000

# Render fornece DATABASE_URL como postgresql://; o driver JDBC requer jdbc:postgresql://.
ENTRYPOINT ["sh", "-c", "if [ -n \"${DATABASE_URL:-}\" ]; then export DB_URL=\"jdbc:${DATABASE_URL}\"; fi; exec java -jar /app/app.jar"]
