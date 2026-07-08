# ---- Asama 1: Derleme (Maven + JDK 21 imajin icinde, lokalde Java gerekmez) ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Once sadece pom'u kopyala -> bagimliliklar cache'lenir, kod degisince tekrar inmez
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

# ---- Asama 2: Calistirma (sadece JRE, kucuk imaj) ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]