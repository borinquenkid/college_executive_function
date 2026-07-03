# Build from the repo root: docker build -f server/Dockerfile -t cef-server .
# (needs the whole monorepo as context — :server depends on :composeApp and :shared)

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew :server:shadowJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/server/build/libs/server-all.jar ./server.jar

# Mount a volume here in production — see docs/adr/0002-multi-tenant-docker-path-partitioned-storage.md
ENV CEF_TENANT_BASE_DIR=/data/tenants
RUN mkdir -p /data/tenants

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "server.jar"]
