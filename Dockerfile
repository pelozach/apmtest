# syntax = docker/dockerfile:experimental
FROM gradle:jdk15 as builder


# move files
WORKDIR /home/gradle/apmtest

COPY --chown=gradle:gradle build.gradle gradle.properties settings.gradle .
COPY --chown=gradle:gradle proto proto
COPY --chown=gradle:gradle apmtest-server apmtest-server

# build
RUN --mount=type=cache,id=gradle,target=/home/gradle/.gradle gradle :apmtest-server:assemble


FROM adoptopenjdk/openjdk15
ARG DD_AGENT_VERSION=0.85.0

ADD --chown=1000:1000 https://github.com/DataDog/dd-trace-java/releases/download/v${DD_AGENT_VERSION}/dd-java-agent-${DD_AGENT_VERSION}.jar dd-java-agent.jar
COPY --from=builder /home/gradle/apmtest/apmtest-server/build/libs/apmtest-server-0.1-all.jar  /app/app.jar

CMD exec java -javaagent:dd-java-agent.jar -jar app/app.jar
