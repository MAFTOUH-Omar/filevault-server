# --- Étape 1 : compiler le .jar ---
FROM eclipse-temurin:27-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q dependency:go-offline
COPY src src
RUN ./mvnw -q package -DskipTests
# --- Étape 2 : image finale, légère (JRE uniquement) ---
FROM eclipse-temurin:27-jre
WORKDIR /app
RUN useradd --system --uid 1001 spring
COPY --from=build /app/target/*.jar app.jar
USER spring
EXPOSE 3322
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]