FROM gradle:5.6.2-jdk11

ADD . /build
WORKDIR /build

RUN gradle bootJar --no-daemon

FROM openjdk:11.0-jre

COPY --from=0 /build/build/libs /build/libs
RUN mkdir /tgabot && find /build/libs -type f -name 'tgabot-?.*\.jar' -exec mv '{}' /tgabot/tgabot.jar ';' && rm -rf /build

WORKDIR /tgabot
CMD ["java", "-jar", "tgabot.jar"]