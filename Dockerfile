FROM eclipse-temurin:25-jdk-jammy AS builder

WORKDIR /app

COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src

RUN chmod +x mvnw \
	&& ./mvnw -B package -DskipTests

RUN mkdir /out \
	&& cp "$(ls target/*.jar | grep -v '\.original$' | head -n1)" /out/app.jar

FROM eclipse-temurin:25-jre-jammy

WORKDIR /app

# UID fixo, nao sorteado. A raiz de midia e um bind mount do host, e quem manda ali
# e o numero: o dono do diretorio no host precisa casar com o usuario do container.
# Com --system o numero sai do proximo livre da imagem base, entao uma troca de base
# orfanaria o chown do servidor e o upload passaria a falhar so na primeira foto.
# 1500 fica fora da faixa de sistema do Ubuntu e nao colide com usuario do host.
RUN groupadd --gid 1500 spring && useradd --uid 1500 --gid 1500 --no-create-home spring

# Without a writable .dev the dev JWK fallback cannot persist its generated RSA key, so every
# container start signs with a new key and invalidates every access token already issued.
# Production must still set VANEP_OAUTH_JWK_PRIVATE_KEY — this only keeps dev sane.
RUN mkdir -p /app/.dev && chown -R spring:spring /app/.dev

USER spring:spring

COPY --from=builder /out/app.jar app.jar

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=docker
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
