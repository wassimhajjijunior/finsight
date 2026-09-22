# FinSight — Implementation Milestones

Checklist with **definition of done (DoD)** and **evidence artifacts** per
milestone. Evidence artifacts are what you show a supervisor, put on a CV, or
pull out in an interview. Order follows `roadmap.md`; each milestone must meet
its DoD before the next starts.

Legend: 🔴 MUST · 🟡 SHOULD · 🟢 OPTIONAL

---

## M1 — Trust and stability baseline *(roadmap Phase 0; ~2 weeks)*

**Recommended first milestone. See `README.md`.**

- [ ] 🔴 Rotate and delete the local Groq/HuggingFace fallback keys in
      `ai-service/src/main/resources/local-overrides.yml`; secrets only via env
      vars, fail-fast when unset.
- [ ] 🔴 Replace shared placeholder JWT secret in `api-gateway` and
      `auth-service`; per-environment secrets.
- [ ] 🔴 Fix `account-service` `GlobalExceptionHandler` (currently
      `extends RuntimeException`) → `@RestControllerAdvice` with structured
      `{"detail": {"message", "code", "field"}}` responses.
- [ ] 🔴 Add `eureka-server` to parent `pom.xml` `<modules>`.
- [ ] 🟡 Pre-commit hook / gitleaks so secrets cannot be re-committed.

**DoD:** no secret strings in `git grep`; all services build via one `mvn
package` from the parent; every 4xx/5xx from account-service returns the
structured contract.

**Evidence:** `gitleaks detect` clean run (screenshot), parent `mvn package`
green log, error-contract example response (curl output), one ADR noting the
rotation.

---

## M2 — Test spine *(Phase 1; ~3 weeks)*

- [ ] 🔴 Unit tests: `AuthService`, `TokenService`, `AccountService`,
      `BudgetService`, `RagService` (mocked LLM/vectorstore).
- [ ] 🔴 Testcontainers integration tests: full auth flow; transaction → outbox →
      relay → consumer with dedup; Flyway migration application per service.
- [ ] 🔴 Error-contract tests for every service's `GlobalExceptionHandler`.
- [ ] 🟡 Contract tests (Spring Cloud Contract/Pact) for `AccountClient` and
      `TransactionClient` Feign contracts.
- [ ] 🟡 OpenAPI specs generated + committed per service.
- [ ] 🟢 Pitest mutation coverage on domain services.

**DoD:** `mvn verify` green with > 60% line coverage on domain services (a
number you can quote); a CI-friendly single command runs the whole suite.

**Evidence:** coverage report (JaCoCo), integration test run, contract test
report, OpenAPI YAMLs.

---

## M3 — Reliable eventing *(Phase 2; ~2 weeks)*

- [ ] 🔴 E2E Kafka test: outbox → topic → consumer → dedup, with consumer
      restart mid-stream proving at-least-once + no duplicate side effects.
- [ ] 🔴 Dead-letter topic + bounded retry per consumer (transaction, ai,
      notification).
- [ ] 🟡 Confluent Schema Registry (Avro or JSON Schema) for event payloads;
      explicit event versions.
- [ ] 🟡 AsyncAPI document: topics, keys, schemas, producers, consumers.
- [ ] 🟡 ADR: outbox polling vs Debezium CDC (promote the code comment to a real
      decision record).
- [ ] 🟢 Partitioning strategy review for per-user ordering.

**DoD:** duplicate-delivery test passes; a poisoned event lands in DLQ without
killing the consumer; every event has a versioned schema.

**Evidence:** e2e test, Kafka UI showing DLQ topic + schemas, AsyncAPI YAML, ADR.

---

## M4 — Observability with SLOs *(Phase 3; ~2 weeks)*

- [ ] 🔴 Grafana dashboards (JSON, committed): gateway RPS/latency/5xx,
      Kafka consumer lag, per-service JVM + request latency, outbox depth.
- [ ] 🔴 SLOs for login, transaction-create, chat with error-budget alerts.
- [ ] 🟡 Structured JSON logs with `X-Correlation-ID` shipped to Loki via
      Promtail; end-to-end trace (gateway → service → Kafka → consumer) in
      Zipkin.
- [ ] 🟡 Custom Micrometer meters: outbox depth, dedup hits, chat latency,
      token usage, per-request cost.
- [ ] 🟢 Red metrics on AI quality/cost.

**DoD:** one request is traceable end-to-end with a single correlation ID; an
alert fires when outbox depth stays > 0 for 5+ minutes; dashboards render from
committed JSON on a fresh Grafana.

**Evidence:** dashboard screenshots, Zipkin trace, Loki query by correlation ID,
alert-firing demo.

---

## M5 — Containerization + CI *(Phase 4; ~2 weeks)*

- [ ] 🔴 Multi-stage `Dockerfile` per service (pinned base images,
      `.dockerignore`).
- [ ] 🔴 GitHub Actions: `mvn verify` → gitleaks → image build → push GHCR.
- [ ] 🔴 Dependency CVE gate (Trivy / OWASP Dependency-Check).
- [ ] 🟡 `docker-compose.dev.yml` runs services from images.
- [ ] 🟢 SBOM (Syft) + signing (Cosign) on images.

**DoD:** a PR to any service triggers the full pipeline; the pipeline fails on a
fake committed key and on a known-CVE dependency; fresh clone + one command
starts the entire stack from images.

**Evidence:** workflow YAML, green PR, red-on-secret PR, image registry listing,
`docker scan` report.

---

## M6 — Kubernetes + GitOps *(Phase 5; ~2–3 weeks)*

- [ ] 🔴 Manifests/Helm for all services + infra with liveness/readiness/startup
      probes from actuator.
- [ ] 🔴 ArgoCD (or Flux) GitOps: repo is source of truth, auto-sync + drift
      detection.
- [ ] 🟡 CoreDNS-based discovery; ADR on whether Eureka is retired or kept
      (see `technology-map.md`).
- [ ] 🟡 Local cluster parity with kind/k3d.
- [ ] 🟢 Service mesh (Istio/Linkerd) for mTLS + traffic splitting.

**DoD:** `kubectl get pods` all Ready on kind; a change pushed to the repo syncs
to the cluster automatically; probes demonstrably restart a dead pod.

**Evidence:** kind deployment, ArgoCD UI screenshot, pod-restart demo,
discovery ADR.

---

## M7 — Production AI/RAG *(Phase 6; ~2 weeks)*

- [ ] 🔴 Resolve pgvector schema divergence — single source of truth
      (migration), verified by Testcontainers pgvector integration test.
- [ ] 🔴 Remove the local HF fallback key; make the embedding provider configurable (hosted vs
      self-hosted) with documented fallback.
- [ ] 🔴 Guardrails: input/output moderation, prompt-injection resistance,
      tenant-isolation test (user A cannot retrieve user B's data via chat).
- [ ] 🔴 AI observability: token usage, retrieval latency, sources used, cost.
- [ ] 🟡 ADR: embedding model, chunking, HNSW index with measured retrieval
      latency.
- [ ] 🟢 Redis query cache + per-user chat rate limiting.

**DoD:** RAG integration test green on pgvector container; cross-tenant query
returns zero results; chat metrics appear on the Grafana dashboard; no keys in
repo.

**Evidence:** isolation test, retrieval-latency measurement, AI metrics on
dashboard, ADR.

---

## M8 — Evaluation / LLMOps *(Phase 7; ~2 weeks)*

- [ ] 🔴 Retrieval eval: golden set (20+ queries × expected docs), recall@k / MRR
      numbers.
- [ ] 🔴 Answer eval: golden QA set with LLM-as-judge pass/fail, versioned
      results.
- [ ] 🟡 Eval harness in CI on RAG changes.
- [ ] 🟡 System prompt promoted to versioned resource file.
- [ ] 🟢 A/B embedding model / chunking against the eval set; cost+latency per
      run.

**DoD:** you can quote recall@k and answer pass-rate; a RAG regression fails CI;
eval results are committed and versioned.

**Evidence:** eval report (numbers), CI eval job screenshot, before/after
retrieval comparison.

---

## M9 — Security / supply chain *(Phase 8; ~2 weeks)*

- [ ] 🔴 RBAC end-to-end: gateway route rules and/or `@PreAuthorize`; tests
      proving role enforcement.
- [ ] 🔴 Fix refresh/logout route exposure; align access-token TTL (15 min) with
      `expiresIn` claim.
- [ ] 🔴 CORS restricted per environment (no wildcard in prod).
- [ ] 🟡 SBOM + signing in CI; Trivy gate on CVEs; Vault (or cloud secret store)
      for secrets.
- [ ] 🟡 OWASP ZAP scan on the running gateway.
- [ ] 🟢 Adaptive red-team baseline against the AI endpoint (prompt-injection,
      exfiltration attempts) — see `external-concepts.md`.

**DoD:** a low-privilege token is denied on an admin route by test; expired
access token can still refresh; security scan is green in CI.

**Evidence:** RBAC tests, security scan reports, ZAP report, secrets-handling
ADR.

---

## M10 — Platform maturity / FinOps *(Phase 9; OPTIONAL, ~2 weeks)*

- [ ] 🟡 Resource requests/limits on k8s manifests; cost tagging via labels.
- [ ] 🟡 Capacity plan: expected QPS per service, pod sizing, Kafka partition
      sizing rationale.
- [ ] 🟢 Argo Rollouts canary with metrics-driven promotion.
- [ ] 🟢 k6 load tests recording baselines against M4 SLOs.

**DoD:** you can state per-service capacity numbers and the load test backs the
SLOs; cost is attributable per namespace.

**Evidence:** k6 report, capacity planning doc, cost dashboard.

---

## Interview/CV narrative cheat-sheet

For each completed milestone, one sentence that states **what you built, how
you proved it, and the number**:

- M1: "Hardened a 9-module microservices platform: removed committed secrets,
  unified the error contract, made the build reproducible from the parent POM."
- M2: "Took the platform from zero tests to 60%+ domain coverage with
  Testcontainers integration tests and contract-tested Feign clients."
- M3: "Proved at-least-once Kafka delivery with idempotent consumers under
  duplicate delivery, and added versioned schemas + DLQs."
- M4: "Built SLO-driven observability: dashboards as code, error-budget alerts,
  end-to-end tracing and correlation-ID log correlation."
- M5: "Shipped reproducible multi-stage images with a CI pipeline that fails on
  secrets and CVEs."
- M6: "Deployed the platform on Kubernetes via GitOps with probes and
  self-healing."
- M7/M8: "Made the RAG pipeline measurable: tenant-isolated retrieval, guardrails,
  and a CI eval harness with quoted recall and pass-rate numbers."
- M9: "Enforced RBAC end-to-end and fixed auth flows (refresh, TTL, CORS,
  secrets)."
