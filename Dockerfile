FROM eclipse-temurin:17.0.5_8-jdk AS jdk
COPY ./diploma-ipc/target/diploma-ipc-0.0.1-SNAPSHOT.jar /usr/local/lib/app.jar
EXPOSE 8080 8082
ENTRYPOINT ["java","-jar","/usr/local/lib/app.jar"]
