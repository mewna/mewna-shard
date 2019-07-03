FROM maven:3.5.3-jdk-11

COPY . /app
WORKDIR /app
RUN mvn -B -q clean package

FROM openjdk:11-jdk-slim
COPY --from=0 /app/target/mewna*.jar /app/mewna.jar

ENTRYPOINT ["java", "-Djavax.net.ssl.trustStorePassword=changeit", "-Xms1G", "-Xmx1G", "-jar", "/app/mewna.jar"]