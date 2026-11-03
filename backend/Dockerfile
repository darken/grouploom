# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
# Resolve dependencies in their own layer so source changes don't re-download them.
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar \
	&& find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} app.jar \;

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --no-create-home app
COPY --from=build /workspace/app.jar app.jar
USER app
# Sized for a 512 MB instance (Render free tier): a 256 MB heap leaves room for
# metaspace, code cache and threads (~400 MB resident after startup).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=48m -Xss512k"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
