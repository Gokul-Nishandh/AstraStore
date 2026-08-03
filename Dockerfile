FROM eclipse-temurin:21-jre

WORKDIR /app

ARG SERVICE

COPY ${SERVICE}/build/libs/*.jar app.jar

ENTRYPOINT ["java","-jar","app.jar"]