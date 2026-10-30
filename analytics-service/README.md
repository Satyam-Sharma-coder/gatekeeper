# analytics-service

The Gatekeeper usage-analytics service. Spring Boot 4.1, Java 21. Port **8082**.

> **Scaffold.** Only `GET /health` is implemented. Kafka consumption, MongoDB storage and JWT validation are not built yet. MongoDB auto-configuration is excluded so the service starts without a database.

## Intended role

Consume request events published by the gateway through Kafka, store them in MongoDB, and expose usage queries for the admin UI. See [docs/08-known-issues-and-roadmap.md](../docs/08-known-issues-and-roadmap.md).

## Run

```bash
./mvnw spring-boot:run     # Windows: mvnw.cmd spring-boot:run
curl http://localhost:8082/health
```

## Build and test

```bash
./mvnw clean package
./mvnw test
```
