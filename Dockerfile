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

# Monta a URL JDBC sem incluir credenciais nela. O Render injeta cada campo
# do banco separadamente e o Spring usa usuario e senha em propriedades próprias.
ENTRYPOINT ["sh", "-c", "if [ -n \"${DB_HOST:-}\" ]; then export DB_URL=\"jdbc:postgresql://${DB_HOST}:${DB_PORT:-5432}/${DB_NAME}\"; fi; exec java -jar /app/app.jar"]
