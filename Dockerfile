FROM openjdk:8-slim
MAINTAINER Calin <calinfraser@gmail.com>

ADD target/uberjar/dice10k-0.1.0-standalone.jar dice10k.jar

# 3000: Service Port
EXPOSE 3000

ENTRYPOINT ["/usr/bin/java", "-server", "-jar", "dice10k.jar"]
