# Imagen de api-sunat en dos pasos: se compila con el JDK y solo el .jar pasa a una imagen con JRE (sin Maven ni código fuente).

# ---- 1. Compilar ----
FROM eclipse-temurin:21-jdk-alpine AS compilar
WORKDIR /app
# Primero solo lo necesario para bajar las dependencias, así Docker las guarda en caché mientras el pom no cambie
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src/ src/
# Los tests se corren en el CI, no al armar la imagen
RUN ./mvnw -B -q package -DskipTests && mv target/api-sunat-*.jar app.jar

# ---- 2. Correr ----
FROM eclipse-temurin:21-jre-alpine
# Sin permisos de administrador dentro del contenedor
RUN addgroup -S apisunat && adduser -S -G apisunat apisunat
WORKDIR /app
COPY --from=compilar /app/app.jar app.jar
USER apisunat
EXPOSE 8081
# El perfil prod hace que no arranque con la configuración del beta; los secretos y el certificado llegan por variables de entorno y volumen
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=America/Lima"
ENTRYPOINT ["java", "-jar", "app.jar"]
