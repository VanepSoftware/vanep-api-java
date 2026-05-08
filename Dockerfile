FROM eclipse-temurin:25-jdk-jammy AS builder

WORKDIR /app

COPY gradlew gradlew.bat ./
COPY gradle/wrapper gradle/wrapper
COPY build.gradle settings.gradle ./
COPY src src

RUN chmod +x gradlew \
	&& ./gradlew bootJar --no-daemon -x test

RUN mkdir /out \
	&& cp "$(ls build/libs/*.jar | grep -v -- '-plain\.jar' | head -n1)" /out/app.jar

FROM eclipse-temurin:25-jre-jammy

WORKDIR /app

RUN groupadd --system spring && useradd --system --gid spring spring
USER spring:spring

COPY --from=builder /out/app.jar app.jar

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=docker
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
