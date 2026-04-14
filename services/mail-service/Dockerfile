FROM maven:3.9.8-eclipse-temurin-21 AS build
ARG PACKAGES_TOKEN
WORKDIR /app

# Configure Maven credentials for GitHub Packages
RUN mkdir -p /root/.m2 && \
    printf '<settings><servers><server><id>github</id><username>x-token</username><password>%s</password></server></servers></settings>' "$PACKAGES_TOKEN" \
    > /root/.m2/settings.xml

COPY pom.xml .
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY iam-service/pom.xml iam-service/pom.xml
COPY user-profile-service/pom.xml user-profile-service/pom.xml
COPY mail-service/pom.xml mail-service/pom.xml
COPY question-bank-service/pom.xml question-bank-service/pom.xml
COPY mail-service/src mail-service/src
RUN mvn -B -pl mail-service clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/mail-service/target/*.jar app.jar
EXPOSE 8087
ENTRYPOINT ["java","-jar","/app/app.jar"]
