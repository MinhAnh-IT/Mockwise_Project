FROM maven:3.9.8-eclipse-temurin-21 AS build
ARG PACKAGES_TOKEN
WORKDIR /app

# Configure Maven credentials for GitHub Packages
RUN mkdir -p /root/.m2 && \
    printf '<settings><servers><server><id>github</id><username>x-token</username><password>%s</password></server></servers></settings>' "$PACKAGES_TOKEN" \
    > /root/.m2/settings.xml

COPY pom.xml .
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY mail-service/pom.xml mail-service/pom.xml
COPY user-profile-service/pom.xml user-profile-service/pom.xml
COPY iam-service/pom.xml iam-service/pom.xml
COPY question-bank-service/pom.xml question-bank-service/pom.xml
COPY storage-service/pom.xml storage-service/pom.xml
COPY tts-stt-service/pom.xml tts-stt-service/pom.xml
COPY tts-stt-service/src tts-stt-service/src
RUN mvn -B -pl tts-stt-service clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
# ffmpeg is required at runtime for audio extraction from interview videos.
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/tts-stt-service/target/*.jar app.jar
EXPOSE 8086
ENTRYPOINT ["java","-jar","/app/app.jar"]
