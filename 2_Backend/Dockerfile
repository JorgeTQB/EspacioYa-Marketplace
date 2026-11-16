# Backend de EspacioYa (Spring Boot, Java 25). Contexto de build: 2_Backend/
#   docker build -t espacioya-backend 2_Backend
# Las pruebas no corren aquí: las corre ./init.sh antes de construir la imagen.

# --- Compilación -------------------------------------------------------------------------------
FROM eclipse-temurin:25-jdk AS compilacion
WORKDIR /build

# Primero solo el pom: las dependencias quedan en una capa propia y no se bajan en cada cambio de código.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q package -DskipTests \
    && find target -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' -exec cp {} app.jar \;

# --- Ejecución ---------------------------------------------------------------------------------
FROM eclipse-temurin:25-jre

# Usuario sin privilegios: el proceso no corre como root.
RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app --no-create-home app
WORKDIR /app
COPY --from=compilacion --chown=app:app /build/app.jar app.jar
USER app

EXPOSE 8080
# La memoria se ajusta al límite de la tarea (JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75 en la task definition).
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
