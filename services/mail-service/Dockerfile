FROM maven:3.9.8-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY mail-service/pom.xml mail-service/pom.xml
COPY mail-service/src mail-service/src
RUN mvn -B -pl mail-service clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/mail-service/target/*.jar app.jar
EXPOSE 8087
ENTRYPOINT ["java","-jar","/app/app.jar"]
