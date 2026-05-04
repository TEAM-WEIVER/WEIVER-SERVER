FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY build/libs/*SNAPSHOT.jar app.jar

ENV TZ=Asia/Seoul

ENTRYPOINT ["java", "-jar", "app.jar"]