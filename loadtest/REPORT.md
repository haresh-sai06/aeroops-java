# Load test report: POST /api/v1/predict/delay

**Run date:** 2026-10-23 · **Tool:** k6 v0.54.0, closed model (N virtual users in a tight loop), 60 s per run after a 15 s warm-up.
Raw k6 summaries are in [`results/`](results/).

## Environment (be explicit: this is a dev box, not a tuned server)
- 8 vCPU Intel Xeon VM, 15 GB RAM, Linux. **k6, the app, Postgres and Redis all shared the same 8 vCPUs.**
- App: `java -Xmx1g -jar target/aeroops-java-0.1.0.jar` (JDK 21, Spring Boot 3.3.5, Tomcat defaults: 200 threads).
- Postgres 17 and Redis 8 ran natively (no Docker on that box), on localhost.
- Observability **on** during the test: 100% trace sampling exported via OTLP/HTTP to a local OpenTelemetry Collector, plus OTLP metrics export and Prometheus endpoint. So these numbers include tracing overhead.
- Every request also writes an audit row to Postgres asynchronously; all ~1.53 M requests (incl. warm-up) were persisted (`/api/v1/predictions/stats` total = 1,529,881), none dropped.

## Results
| Scenario | VUs | Requests (60 s) | Throughput (req/s) | p50 (ms) | p95 (ms) | p99 (ms) | Errors |
|---|---|---|---|---|---|---|---|
| cold (cache miss, full inference) | 1 | 77,962 | 1,299 | 0.61 | 0.80 | 1.23 | 0.00% |
| cold (cache miss, full inference) | 32 | 472,013 | 7,865 | 3.46 | 7.50 | 11.26 | 0.00% |
| hot (500-flight pool, Redis hits) | 1 | 130,748 | 2,179 | 0.34 | 0.45 | 0.67 | 0.00% |
| hot (500-flight pool, Redis hits) | 32 | 794,179 | 13,236 | 1.86 | 5.15 | 8.49 | 0.00% |

- **cold**: each request has a random distance jitter, so it is always a Redis miss → feature lookup + 300-tree XGBoost evaluation + Redis SET.
- **hot**: requests drawn from 500 realistic flights, so after warm-up almost all are Redis hits (deserialise cached JSON). Over all runs the cache counters were 924,428 hits / 605,477 misses.
- Server-side model time (Micrometer timer `aeroops.inference`): **mean 233 µs per prediction** (141.3 s / 605,477 inferences, measured under load).

## Context, not a head-to-head
The original Python FastAPI service in [aeroops-ml](https://github.com/haresh-sai06/aeroops-ml) measured p50 10.5 ms / 106.6 req/s at concurrency 16 with a different client (`reports/api_benchmark.json`).
The Java port is much faster on the same box, but the tools and settings differ, so treat that as indicative, not a controlled comparison.

## Reproduce
```bash
docker compose up -d --build        # or run Postgres/Redis locally + java -jar
k6 run -e SCENARIO=cold -e VUS=32 -e DURATION=60s --summary-export loadtest/results/cold_vus32.json loadtest/predict.js
```
