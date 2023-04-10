FROM maven:3.8.7-eclipse-temurin-17-alpine AS maven
COPY ./diploma-ipc/src /home/app/src
COPY ./diploma-ipc/pom.xml /home/app
RUN mvn -f /home/app/pom.xml clean package -DskipTests=true

FROM eclipse-temurin:17.0.5_8-jdk AS jdk
COPY --from=maven /home/app/target/diploma-ipc-0.0.1-SNAPSHOT.jar /usr/local/lib/app.jar
EXPOSE 8080 8082
ENTRYPOINT ["java","-jar","/usr/local/lib/app.jar"]
