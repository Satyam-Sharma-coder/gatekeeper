# smart-gateway-service

The Gatekeeper data plane. Spring Boot 4.1, Java 21. Port **8080**.

> **Scaffold.** Only `GET /health` is implemented. Routing, rate limiting, idempotency (Redis), Kafka events and JWT validation are not built yet, although the dependencies are declared.

## Intended role

Sign in to `api-management-service` as the `gateway-service` account, poll `GET /api/gateway/routes`, build a routing table, and forward client traffic to each route's target with its header rules, rate limit and timeout applied. See [docs/07-end-to-end-flows.md](../docs/07-end-to-end-flows.md#75-how-the-gateway-gets-its-configuration) and [docs/08-known-issues-and-roadmap.md](../docs/08-known-issues-and-roadmap.md).

## Run

```bash
./mvnw spring-boot:run     # Windows: mvnw.cmd spring-boot:run
curl http://localhost:8080/health
```

No database or external service is needed to start it.

## Build and test

```bash
./mvnw clean package
./mvnw test
```
