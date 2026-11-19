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
# CVE-2026-114938 ("Java4Shell") workaround: -XX:-UseBASE64Intrinsics turns off HotSpot's
# vectorised Base64 decoder, whose heap overflow is reachable pre-auth here through the LTI launch
# JWT. eclipse-temurin:21-jre is still 21.0.13+9 (vulnerable); the fix is 21.0.13.1 or later.
# REMOVE the flag once the FROM line above is pinned to a fixed version tag —
# scripts/check-java4shell-fixed.sh (run daily by .github/workflows/java4shell-image-check.yml)
# opens an issue when that image is published.
ENTRYPOINT ["java", "-XX:-UseBASE64Intrinsics", "-jar", "server.jar"]
