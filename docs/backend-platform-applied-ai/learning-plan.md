# FinSight — Learning Plan

Skill-oriented, weekly, mapped to roadmap phases. Each week has a goal, the
skills it builds, and a concrete exercise that produces an artifact in the
FinSight repo. Schedule ~6–8 focused hours per week; adjust based on supervisor
feedback. "Done" means the exercise artifact exists and you can explain it
without notes.

---

## Week 1 — Test foundations (Phase 1)

- **Goal:** Write the first meaningful tests in the project.
- **Skills:** JUnit 5, AssertJ, Mockito, `@WebMvcTest`, `@SpringBootTest`.
- **Exercises:**
  1. Unit-test `TokenService` (access token claims, refresh token storage/revocation in Redis mock).
  2. Unit-test `AuthService.login/register` with mocked repository.
  3. Replace one `contextLoads` test with a real service test.
- **Done when:** `auth-service` has ≥ 10 passing tests; you can explain why unit
  tests are fast and integration tests are slow.
- **Evidence:** `mvn test` output screenshot per service.

## Week 2 — Testcontainers + Flyway (Phase 1)

- **Goal:** Integration tests against real Postgres/Redis via Testcontainers.
- **Skills:** Testcontainers, Flyway in tests, transactional test isolation.
- **Exercises:**
  1. Integration-test auth register → login → refresh (rotation) → logout against Testcontainers Postgres + Redis.
  2. Assert Flyway migrations apply cleanly (`V1..V3` in transaction-service).
- **Done when:** integration test starts containers, applies migrations, exercises the full auth flow, and cleans up.
- **Evidence:** a passing integration test you can demo live.

## Week 3 — Error contracts + API docs (Phase 1)

- **Goal:** Uniform structured errors across services.
- **Skills:** `@RestControllerAdvice`, `ProblemDetail`/custom error DTO, OpenAPI.
- **Exercises:**
  1. Implement the `{"detail": {"message", "code", "field"}}` contract in account-service; port to the other services.
  2. Generate an OpenAPI spec per service; commit it.
- **Done when:** every 4xx/5xx response follows the contract; spec is generated in build.
- **Evidence:** OpenAPI YAML files + error-contract test.

## Week 4 — Dockerfiles + Compose (Phase 4)

- **Goal:** Every service builds as a lean, pinned image.
- **Skills:** multi-stage builds, `.dockerignore`, image pinning, healthchecks.
- **Exercises:**
  1. Write a multi-stage `Dockerfile` for auth-service; replicate per service.
  2. Add services to a `docker-compose.dev.yml` that runs built images.
- **Done when:** `docker compose up` runs the full stack from images; `docker scan` shows no critical CVEs.
- **Evidence:** screenshot of `docker compose ps` all healthy.

## Week 5 — CI/CD with GitHub Actions (Phase 4)

- **Goal:** Every push builds, tests, and scans.
- **Skills:** GitHub Actions, caching Maven, gitleaks, Trivy/OWASP Dependency-Check.
- **Exercises:**
  1. Workflow: `mvn verify` → gitleaks scan → build images → push to GHCR.
  2. Make the pipeline fail on secrets and on dependency CVEs (gate).
- **Done when:** a green build on a feature branch PR; a red build when you intentionally commit a fake key (then revert).
- **Evidence:** workflow file + green PR screenshot.

## Week 6 — Kafka, outbox, idempotency (Phase 2)

- **Goal:** Prove and harden the eventing backbone.
- **Skills:** Kafka producer/consumer semantics, at-least-once, dedup, DLQ.
- **Exercises:**
  1. End-to-end test: create transaction → outbox → relay → consumer → dedup; restart consumer mid-stream and assert no duplicate processing.
  2. Add dead-letter topic + bounded retry to one consumer.
- **Done when:** test proves exactly-once-processing-effect under at-least-once delivery.
- **Evidence:** the e2e test + a Kafka UI screenshot showing the DLQ topic.

## Week 7 — Observability: metrics + dashboards (Phase 3)

- **Goal:** See the system, not just run it.
- **Skills:** Micrometer, PromQL, Grafana dashboards as code, SLOs.
- **Exercises:**
  1. Custom Micrometer meters: outbox depth, dedup hits, chat latency, token usage.
  2. Grafana dashboard (JSON, committed) for gateway + ai-service; add alert on outbox depth > 0 for > 5 min.
- **Done when:** dashboard JSON is in `infrastructure/grafana/` and alerts fire in a demoed failure.
- **Evidence:** dashboard screenshot + alert firing demo.

## Week 8 — Distributed tracing + logs (Phase 3)

- **Goal:** End-to-end visibility of one request.
- **Skills:** Micrometer Tracing, Zipkin/Jaeger, correlation IDs, structured JSON logs, Loki.
- **Exercises:**
  1. Verify gateway → auth/transaction → Kafka → consumer trace in Zipkin.
  2. Add `X-Correlation-ID` to structured logs; ship service logs to Loki via Promtail.
- **Done when:** a single request is traceable from gateway to consumer, and logs for that request are queryable in Grafana by correlation ID.
- **Evidence:** Zipkin trace screenshot + Loki query result.

## Week 9 — Security hardening (Phase 0 + 8)

- **Goal:** RBAC, refresh flow, secrets hygiene.
- **Skills:** Spring Security, JWT best practices, secret management, CORS.
- **Exercises:**
  1. Enforce RBAC (gateway route metadata and/or `@PreAuthorize`); test role denied vs allowed.
  2. Fix refresh/logout public paths and align access-token TTL (15 min) with the claim.
  3. Move all secrets to env vars; add gitleaks to CI (from Week 5).
- **Done when:** a low-privilege token is provably denied on an admin route; a leaked-key commit fails CI.
- **Evidence:** RBAC test + CI red-on-secret screenshot.

## Week 10 — Kubernetes + GitOps (Phase 5)

- **Goal:** Deploy the platform on k8s reproducibly.
- **Skills:** kind/k3d, manifests/Helm, probes, ArgoCD, CoreDNS vs Eureka.
- **Exercises:**
  1. k8s manifests or Helm charts for 3 services + probes; deploy to kind.
  2. ArgoCD app pointing at the repo; verify auto-sync and drift detection.
- **Done when:** `kubectl get pods` shows all ready; changing the repo syncs to the cluster.
- **Evidence:** kind deployment + ArgoCD UI screenshot.

## Week 11 — Production RAG + guardrails (Phase 6)

- **Goal:** Make the AI claim defensible.
- **Skills:** pgvector schema management, embedding provider abstraction, prompt-injection defenses, tenant isolation.
- **Exercises:**
  1. Integration-test RAG against pgvector Testcontainer: index → retrieve → assert only this user's docs.
  2. Resolve the schema divergence (migration as single source of truth).
  3. Add input guardrail + tenant isolation test.
- **Done when:** RAG integration test passes; a cross-tenant query returns nothing.
- **Evidence:** isolation test + retrieval latency measurement.

## Week 12 — Evaluation / LLMOps (Phase 7)

- **Goal:** Measure answer and retrieval quality.
- **Skills:** golden sets, recall@k/MRR, LLM-as-judge, eval-in-CI.
- **Exercises:**
  1. Golden retrieval set (20 queries × expected docs); compute recall@k.
  2. Golden QA set; run LLM-as-judge pass/fail; commit results.
  3. Wire eval into CI for RAG changes.
- **Done when:** you can state retrieval recall and answer pass-rate with numbers, and a RAG regression fails CI.
- **Evidence:** eval report (numbers) + CI eval job.

## Week 13 — Capacity, load, FinOps (Phase 9, OPTIONAL)

- **Goal:** Numbers behind "production-ready".
- **Skills:** k6 load testing, resource requests/limits, cost tagging, capacity planning.
- **Exercises:**
  1. k6 script for login + transaction-create; record baselines against Phase 3 SLOs.
  2. Resource requests/limits on k8s manifests; cost dashboard via labels.
- **Done when:** you can say "login handles X RPS at p95 Y ms; this many pods handle peak".
- **Evidence:** k6 report + capacity planning doc.

## Week 14 — Wrapping portfolio evidence (all phases)

- **Goal:** Turn artifacts into interview/CV material.
- **Skills:** technical writing, demo scripting, architecture storytelling.
- **Exercises:**
  1. Write a 1-page platform narrative (architecture → what you built → numbers).
  2. Record a 3-minute demo: full stack up, one request traced end-to-end, dashboard showing metrics, eval passing.
- **Done when:** the demo runs from a clean clone of the repo with one command.
- **Evidence:** the demo video + narrative doc.

---

## Anti-cargo-cult reminders

- Do not add a technology just because it is trendy — `technology-map.md` tells
  you who actually uses it and what the alternatives are.
- Every week's artifact must be explainable: "why this, why now, what breaks if
  it's wrong".
- If a week feels too large, split the exercise — the milestone checklist in
  `implementation-milestones.md` is the authority on scope.