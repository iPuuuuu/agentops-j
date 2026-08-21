FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY src ./src
RUN mkdir -p out && javac -d out $(find src/main/java -name '*.java')

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/out ./out
EXPOSE 8080
ENTRYPOINT ["java", "-cp", "out", "com.ipuuuuu.agentops.AgentOpsApplication"]
