FROM eclipse-temurin:21-jdk-jammy

WORKDIR /app

ENV TZ=Asia/Seoul
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} app.jar

ENTRYPOINT ["java", "-jar", "-Duser.timezone=Asia/Seoul", "app.jar"]