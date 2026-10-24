# aeroops-java ✈️ — flight-delay prediction microservice (Spring Boot 3, Java 21)

A production-shaped Java service that serves the **AeroOps XGBoost flight-delay model** (trained with PySpark on 1.66 M US flights in [aeroops-ml](https://github.com/haresh-sai06/aeroops-ml)) over REST.
The model runs in **pure Java**: a small evaluator reads XGBoost's JSON trees (numeric and native categorical splits), with no Python sidecar or JNI.
It sits behind a **Redis** cache-aside layer, writes an audit log to **Postgres** (Flyway migrations) asynchronously, and emits **OpenTelemetry** traces and metrics.

**Measured on an 8-vCPU dev box (k6, 60 s runs, tracing on):**

| | p50 | p95 | Throughput | Errors |
|---|---|---|---|---|
| Cache miss (full 300-tree inference), 32 VUs | 3.46 ms | 7.50 ms | 7,865 req/s | 0% |
| Cache hit (Redis), 32 VUs | 1.86 ms | 5.15 ms | 13,236 req/s | 0% |
| Cache miss, 1 VU | 0.61 ms | 0.80 ms | 1,299 req/s | 0% |

**Correctness:** across 500 golden requests produced by the original Python service, Java probabilities match XGBoost within **max abs error 2.6e-7**, and unknown-category handling matches exactly (`XgbParityTest`).
For the full method, see [loadtest/REPORT.md](loadtest/REPORT.md).

## Architecture
```mermaid
flowchart LR
  C[Client / k6] -->|POST /api/v1/predict/delay| API[Spring MVC controller<br/>Bean Validation]
  API --> S[DelayPredictionService<br/>@Observed span + timer]
  S -->|GET key| R[(Redis<br/>TTL 10 min, LRU)]
  S -->|miss| F[DelayFeatureBuilder<br/>online feature snapshot]
  F --> M[XgbTreeModel<br/>300 trees, pure Java]
  S -->|SET key| R
  S -.->|@Async bounded pool| L[PredictionLogWriter] --> P[(Postgres<br/>prediction_log, Flyway)]
  S -.->|OTLP traces + metrics| O[OTel Collector] --> J[Jaeger]
  O --> PR[Prometheus :8889]
  API -.-> A[/actuator/health · /actuator/prometheus/]
```

**Design choices worth discussing**
- **Native Java inference over a Python sidecar:** no extra network hop or process. A golden-file parity test against the Python model guards correctness, and the server-side model time averages about 233 µs.
- **Cache-aside that fails open:** if Redis is down, the service logs a warning and still serves predictions (`PredictionCacheTest`). The cache key includes every input that affects the output.
- **Audit log kept off the hot path:** rows are written by a bounded `@Async` pool, so Postgres latency never adds to the response time. Under overload the oldest queued log rows are dropped instead of blocking requests. In the load test, all 1.53 M rows were persisted.
- **Same features in training and serving:** history features come from the same Spark-built snapshot (`online_features.json`, as of 2024-03-31) that the Python API uses. Missing values and unknown categories become NaN and follow XGBoost's default branch.
- **Observability:** Micrometer `@Observed` creates both a timer and an OTel span (`predict-delay`, child of the HTTP server span). Custom metrics are `aeroops.inference`, `aeroops.cache{result}` and `aeroops.predict.delay`, plus HTTP histograms. Log lines carry `traceId`/`spanId`. Export was verified with a local OTel Collector: sample span in [docs/otel-evidence](docs/otel-evidence/), 73 exported metric names.

## API
```bash
curl -s -XPOST localhost:8080/api/v1/predict/delay -H 'content-type: application/json' -d '{
  "carrier":"WN","origin":"ATL","dest":"LGA","flight_date":"2024-04-02","dep_hour":17,"distance":762,
  "prev_leg_arr_delay":55,"prev_leg_dep_delay":50,"leg_of_day":3}'
# {"delay_probability":0.7432,"predicted_delayed":true,"threshold":0.27,"history_as_of":"2024-03-31",
#  "unknown_categories":[],"cached":false,"model_version":"xgb_delay@300trees/snapshot 2024-03-31"}
```
| Endpoint | Purpose |
|---|---|
| `POST /api/v1/predict/delay` | P(arrival ≥ 15 min late). Returns 422 on invalid input and 400 on malformed JSON (RFC 7807 problem details) |
| `GET /api/v1/predictions/recent?limit=20` | Latest audit-log rows from Postgres |
| `GET /api/v1/predictions/stats` | Total predictions and cache hits |
| `GET /actuator/health`, `/actuator/prometheus` | Liveness/readiness probes and metrics |

> The model leans heavily on `prev_leg_arr_delay` (the inbound aircraft's delay), so it predicts delays **near departure**, not days ahead. See aeroops-ml for the details.

## Run it
```bash
# Full stack: app + Postgres + Redis + OTel Collector + Jaeger (UI at http://localhost:16686)
docker compose up -d --build

# Or without Docker: start Postgres (db/user/pass "aeroops") and Redis locally, then
mvn -B package && java -jar target/aeroops-java-0.1.0.jar
```
Configuration comes from environment variables (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `REDIS_HOST`, `CACHE_TTL`, `OTEL_TRACES_ENDPOINT`, `OTEL_METRICS_ENABLED`, `TRACE_SAMPLING`). The compose credentials are local-development defaults only.

## Tests
`mvn -B verify` runs **12 tests**:
- **Unit tests (10):** XGBoost parity on 500 golden requests, feature encoding (Spark day-of-week, NaN for missing values and unknown categories), Redis fail-open and TTL, and controller validation and JSON contract.
- **Integration tests (2):** real HTTP → Redis → model → Postgres (Flyway). Covers a cache miss followed by a hit, audit rows persisted, and Prometheus exposing the custom metrics. These tests use **Testcontainers** when Docker is available and otherwise fall back to local Postgres/Redis. They were run against local services on the dev box, which has no Docker.

To regenerate the golden fixtures from the Python model, run `scripts/gen_parity_fixtures.py`.

Unit tests run in [`.github/workflows/ci.yml`](.github/workflows/ci.yml) on Java 17, which is the `java.version` in `pom.xml`. The fuller draft in [`ci/github-actions.yml`](ci/github-actions.yml) also builds the image and runs integration tests; those steps are not enabled because they were not re-run in the 24 October 2026 assessment. See [TRL self-assessment](docs/TRL-ASSESSMENT.md): laboratory evidence supports TRL 4, not an operational deployment.

## Interview talking points
1. **Why port the model instead of calling Python?** Latency and operational simplicity. I walk XGBoost's JSON tree arrays directly: float32 comparisons, `default_left` for missing values, and categorical partition splits where the stored category set goes right. A 500-request golden test proves parity to 2.6e-7.
2. **Cache design:** the key covers all inputs, with a TTL and LRU eviction, and the cache fails open. Under load, hits ran at 13.2k req/s compared with 7.9k req/s on misses. Since inference only takes about 0.23 ms, the cache mostly saves CPU rather than latency here, and I would say exactly that.
3. **Back-pressure:** the audit writes go through a bounded queue with a DiscardOldest policy. This deliberately trades audit completeness under overload for a stable p95.
4. **Observability:** a single `@Observed` annotation gives both a span and a timer. The OTLP pipeline runs Collector → Jaeger and Prometheus, and traceId appears in the logs for correlation.
5. **Honest limits:** load generator and service shared one 8-vCPU VM, tracing was sampled at 100%, and nothing is cloud-deployed yet.

## Not done yet / TBD
- No cloud deployment yet (needs an AWS or GCP account). The Dockerfile and compose file are ready.
- The Docker image and `docker compose` stack were **not built or run on the dev box** (no Docker there). CI builds the image.
- The RUL (engine remaining-life) endpoint from aeroops-ml has not been ported yet, because it needs rolling-window feature engineering ported too.
- No auth or rate limiting.

## License
MIT. See [LICENSE](LICENSE).
