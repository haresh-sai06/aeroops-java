# Technology readiness self-assessment

Assessment date: 24 October 2026. This is an engineering self-assessment from repository evidence and a local test run. It is not an independent certification, a production qualification, or a claim of operational use.

## Result

**TRL 4.** The service's components have been validated together in a laboratory setting: pure-Java model scoring, feature encoding, a Redis cache that fails open, and the HTTP contract.

TRL 5 is not claimed. A developer workstation and a synthetic load generator are not a relevant airline-operations environment. TRL 6 through TRL 9 are not claimed. There is no staging deployment, no dispatcher trial, and no operational system.

## Evidence checked on 24 October 2026

- Host: Windows, JDK 17.0.18, Maven 3.9.16.
- Command: `mvn -B test` in this repository.
- Result: 10 unit tests, 0 failures. That includes `XgbParityTest` (500 golden requests against the Python model), feature encoding, Redis fail-open behavior, and the controller validation contract.
- The two Postgres/Redis integration tests were not part of `mvn test`. A follow-up `mvn -B verify` on the same machine errored on both: Testcontainers could not get a usable Docker engine, and nothing was accepting Postgres connections on localhost:5432. The published dev-box integration result was therefore not reproduced today.
- The k6 report in `loadtest/REPORT.md` (23 October 2026, 8-vCPU dev box, 0% errors in the published tables) was not repeated today. The report itself says the generator and the service shared that box.

## Why this is TRL 4 and not higher

| Level | What it requires here | Status |
|---|---|---|
| TRL 4 | Integrated components validated in a laboratory | Met by the unit tests and the recorded lab load run |
| TRL 5 | Validated in a relevant environment | Not met. No staging system and no representative operational traffic |
| TRL 6 | Prototype demonstrated in a relevant environment | Not met |
| TRL 7–9 | Operational demonstration, qualification, or proven use | Not met. Nothing is cloud-deployed |

## Remaining work for TRL 5

- Run the two integration tests against Postgres and Redis and keep that run in CI.
- Deploy the existing container to a staging environment outside the developer machine.
- Replay a held-out set of flight-feature requests from that staging environment and record latency, errors, and parity.
- Add authentication before any exposure beyond a lab.

The GitHub Actions workflow runs the same 10 unit tests on a clean Ubuntu runner. A green unit-test run does not by itself raise this assessment.
