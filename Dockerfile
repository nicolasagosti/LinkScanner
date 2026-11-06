# Vercel construye este archivo y dirige el tráfico al puerto de PORT.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY backend/src src
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/linkscanner-backend-*.jar app.jar
# Vercel espera el puerto 80 si no se configura otro en el proyecto.
ENV PORT=80
EXPOSE 80
ENTRYPOINT ["java", "-jar", "app.jar"]
