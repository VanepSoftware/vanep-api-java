FROM eclipse-temurin:25-jdk-jammy AS builder

WORKDIR /app

COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src

# Extract Spring Boot layers so the dependency tree is its own image layer.
# A commit that does not change pom.xml then only republishes the thin
# application jar. Directory mtimes are pinned: BuildKit hashes them into the
# layer, and a fresh extract would otherwise bust the dependency cache.
RUN chmod +x mvnw \
	&& ./mvnw -B package -DskipTests \
	&& jar="$(find target -maxdepth 1 -type f -name '*.jar' ! -name '*.original' -print -quit)" \
	&& java -Djarmode=tools -jar "$jar" extract --layers --destination /out \
	&& appjar="$(find /out/application -maxdepth 1 -type f -name '*.jar' -print -quit)" \
	&& if [ "$appjar" != /out/application/app.jar ]; then mv "$appjar" /out/application/app.jar; fi \
	&& for dir in /out/snapshot-dependencies /out/spring-boot-loader; do \
		if [ -z "$(find "$dir" -mindepth 1 -print -quit)" ]; then \
			touch "$dir/.keep"; \
		fi; \
	done \
	&& find /out -exec touch -d "1980-02-01T00:00:00Z" {} +

FROM eclipse-temurin:25-jre-jammy

WORKDIR /app

# CVE-2026-91403: revoga as CAs da eMudhra cruzando CN=emSign, O=eMudhra e SHA-256 oficial.
RUN keytool -list -v -cacerts -storepass changeit 2>/dev/null \
	| awk ' \
		/^Alias name:/ { sub(/^Alias name: /, ""); alias=$0; ok=0 } \
		/^Owner:.*CN=emSign.*O=eMudhra/ { ok=1 } \
		/SHA256: (86:A1:EC:BA:08:9C:4A:8D:3B:BE:27:34:C6:12:BA:34:1D:81:3E:04:3C:F9:E8:A8:62:CD:5C:57:A3:6B:BE:6B|BC:4D:80:9B:15:18:9D:78:DB:3E:1D:8C:F4:F9:72:6A:79:5D:A1:64:3C:A5:F1:35:8E:1D:DB:0E:DC:0D:7E:B3|40:F6:AF:03:46:A9:9A:A1:CD:1D:55:5A:4E:9C:CE:62:C7:F9:63:46:03:EE:40:66:15:83:3D:C8:C8:D0:03:67|12:56:09:AA:30:1D:A0:A2:49:B9:7A:82:39:CB:6A:34:21:6F:44:DC:AC:9F:39:54:B1:42:92:F2:E8:C8:60:8F)/ { if (ok) print alias } \
	' \
	| while IFS= read -r alias; do \
		keytool -delete -cacerts -storepass changeit -noprompt -alias "$alias"; \
	done

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

# Application log directory (logging.file.name in application-docker.properties). Created
# and owned by spring so the container can write even when no host directory is mounted.
RUN mkdir -p /app/logs && chown -R spring:spring /app/logs

USER spring:spring

# Least volatile first. Only the last layer changes on an application-only commit.
COPY --from=builder /out/dependencies/ ./
COPY --from=builder /out/spring-boot-loader/ ./
COPY --from=builder /out/snapshot-dependencies/ ./
COPY --from=builder /out/application/ ./

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=docker
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Shell form so JAVA_OPTS from the environment is expanded. Compose sets a
# smaller heap on vanep-worker than on the API; an exec-form entrypoint would
# ignore that override.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
