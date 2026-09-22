# FinSight — Roadmap (Phased)

Ordered by dependency: you cannot test what does not compile cleanly, you cannot
reliably test what is not secured, you cannot observe what is not packaged, and
you cannot deploy what is not packaged. Each phase has rationale and explicit
dependencies on earlier phases.

Every item is tagged **MUST** (blocking, do before advancing), **SHOULD**
(high value, do when the phase's MUST items are green), or **OPTIONAL**
(nice-to-have, only if time and supervisor agree).

---

## Phase 0 — Stabilize and secure (MUST)

**Rationale:** The local working tree contains real API-key fallback values and a shared placeholder
JWT secret; exception handlers are stubbed; the multi-module build excludes
Eureka. Nothing else is trustworthy until this is fixed. This phase is mostly
small, high-impact changes that unblock everything after.

- MUST: Rotate Groq + HuggingFace keys; delete fallback keys from
  `ai-service/src/main/resources/local-overrides.yml`; move every secret to
  environment variables (fail-fast when unset, no placeholder defaults).
- MUST: Replace placeholder JWT secret in `api-gateway` and `auth-service` with
  env-provided secrets; require distinct secrets per environment.
- MUST: Fix `GlobalExceptionHandler` in account-service (it currently
  `extends RuntimeException`); define one structured error contract
  (`{"detail": {"message", "code", "field"}}`) used by all services.
- MUST: Declare `eureka-server` in the parent `pom.xml` modules so the
  multi-module build is complete.
- SHOULD: Add `.gitignore`/`.dockerignore` guards so secrets can never be
  committed again (pre-commit hooks, secret-scan in CI once CI exists).
- OPTIONAL: Add a `local-dev` profile that reads secrets from a git-ignored
  `.env` file instead of committed YAML.

**Dependency:** none — this is the entry point.

---

## Phase 1 — Tests, contracts, error handling (MUST)

**Rationale:** The project has zero meaningful tests (only `contextLoads`).
Before touching eventing or AI, lock down the domain behavior with tests so
later changes are provably non-regressive. Contract tests also fix the
"routes live only in an external config repo" problem by making the API surface
explicit and versioned.

- MUST: Unit tests for `AuthService` (register/login/refresh rotation/logout)
  and `TokenService` (Redis-backed refresh validation, revocation).
- MUST: Integration tests with Testcontainers (Postgres + Redis + Kafka) for:
  auth flow, transaction creation → outbox → relay → consumer, dedup
  (`processed_events`), budget fallback behavior.
- MUST: Service-level tests asserting the structured error contract
  (`GlobalExceptionHandler` behavior) across all services.
- SHOULD: Contract tests (Spring Cloud Contract or Pact) for the
  `account-service` ↔ `transaction-service` Feign client and the
  `transaction-service` ↔ `budget-service` client.
- SHOULD: OpenAPI specs generated and committed per service.
- OPTIONAL: Mutation testing (Pitest) on the domain services to prove test
  quality.

**Dependency:** Phase 0 (tests need a build that includes all modules and no
leaked secrets).

---

## Phase 2 — Reliable eventing (MUST)

**Rationale:** The outbox and idempotent consumers are implemented but untested,
payloads are unversioned JSON strings, and there is no dead-letter handling.
Eventing is the backbone of transaction → AI → notification flows; it must be
provably reliable before production traffic.

- MUST: End-to-end Kafka test proving outbox → topic → consumer → dedup, with
  consumer restart mid-stream (at-least-once semantics).
- MUST: Dead-letter topic + retry policy per consumer (bounded retries, then DLQ).
- SHOULD: Confluent Schema Registry + Avro (or JSON Schema) for event payloads;
  version events explicitly.
- SHOULD: AsyncAPI document describing every event (topic, key, schema,
  producers, consumers).
- SHOULD: Replace `OutboxRelayService` polling with Debezium CDC or keep polling
  but document the tradeoff as an ADR (currently documented in code comments —
  promote to a real ADR).
- OPTIONAL: Kafka partitioning strategy review for per-user ordering guarantees.

**Dependency:** Phase 1 (eventing tests need the test infrastructure and
contract conventions from Phase 1).

---

## Phase 3 — Observability and SLOs (SHOULD)

**Rationale:** Prometheus/Grafana/Zipkin/Loki are wired in Compose but there are
no dashboards, no alerts, and service logs are not shipped to Loki. Observability
is what makes every later phase (deployments, on-call) possible to reason about.

- MUST: Grafana dashboards for gateway (RPS, latency, 5xx, rate-limit hits),
  Kafka (consumer lag), and each service (JVM, request latency, outbox depth).
- MUST: SLOs for the top user journeys (e.g., login p95 < 300ms, transaction
  create p95 < 500ms, chat p95 < 10s) with alert rules on error budget burn.
- SHOULD: Ship structured JSON logs (with `X-Correlation-ID`) to Loki via
  Promtail; propagate correlation ID end-to-end (gateway → service → Kafka →
  consumer), verified in Zipkin traces.
- SHOULD: Define SLIs in code (`management.metrics.tags`, Micrometer custom
  meters for outbox depth, dedup hits, RAG retrieval latency).
- OPTIONAL: Red metrics on chat quality (token usage, cost per request).

**Dependency:** Phase 1 (metrics only make sense on tested code); partial
overlap with Phase 2 (consumer lag metrics need reliable consumers).

---

## Phase 4 — Containerization and CI (MUST)

**Rationale:** There are no Dockerfiles and no CI. The platform cannot be built
reproducibly, scanned, or deployed anywhere except a developer laptop.

- MUST: Multi-stage `Dockerfile` per service (Java 21 base, `mvn` build stage,
  slim JRE run stage), pinned base image versions, `.dockerignore` each.
- MUST: GitHub Actions workflow: build all modules → run tests → build images →
  push to a registry (GHCR).
- MUST: Secret-scan step (gitleaks/trufflehog) failing the build on leaked keys.
- MUST: Dependency scanning (OWASP Dependency-Check or Trivy) on images.
- SHOULD: `docker-compose.yml` updated so services run as built images
  (mirrors `docker-compose.prod.yml` vs `docker-compose.dev.yml`).
- OPTIONAL: SBOM generation (Syft) and attestation (Cosign) on images.

**Dependency:** Phases 0–3 (CI runs the test suite; images must not embed
secrets; eventing tests need Kafka in CI).

---

## Phase 5 — Kubernetes / GitOps / platform (SHOULD)

**Rationale:** Docker Compose is fine for demos but not production. Kubernetes +
GitOps gives reproducible environments, self-healing, and a platform story that
is the actual industry expectation for a microservices platform.

- MUST: k8s manifests or Helm charts for all services + infra
  (StatefulSets for Postgres/Kafka or external managed DBs; Deployments for
  stateless services).
- MUST: Probes (liveness/readiness/startup) wired from actuator health.
- MUST: GitOps with ArgoCD (or Flux): repo is the source of truth, sync
  automated, drift shown.
- SHOULD: Kubernetes-native service discovery (CoreDNS) — re-evaluate whether
  Eureka is still needed or is cargo cult (see `technology-map.md`).
- SHOULD: Local cluster setup (kind/k3d) for dev parity.
- OPTIONAL: Istio or Linkerd for mTLS + traffic splitting (enables Phase 9
  canaries).

**Dependency:** Phase 4 (images and CI must exist before k8s).

---

## Phase 6 — Production AI/RAG (MUST for the AI claim)

**Rationale:** The RAG pipeline has a schema/config divergence risk, depends on a
hosted HuggingFace API with a committed key, and has no guardrails or evaluation.
"AI works on my machine" is not a platform claim.

- MUST: Resolve the pgvector schema divergence: single source of truth for the
  `transaction_embeddings` schema (migration or Spring AI-managed), verified
  by integration test against pgvector Testcontainer.
- MUST: Remove the local HF fallback key; make embedding provider configurable
  (hosted API vs self-hosted model) with a documented fallback.
- MUST: Add guardrails: input/output moderation, prompt-injection resistance
  for the RAG context, tenant isolation test (user A cannot retrieve user B's
  transactions via chat).
- MUST: Observability for AI: log token usage, retrieval latency, sources used,
  per-request cost.
- SHOULD: ADR documenting embedding model choice, chunking strategy, and
  vector index (HNSW) with measured retrieval latency.
- OPTIONAL: Caching layer for repeated queries (Redis) and rate limiting
  per-user on chat.

**Dependency:** Phases 0, 1 (security and tests first), 3 (observability).

---

## Phase 7 — Evaluation / LLMOps (SHOULD)

**Rationale:** RAG without evaluation is vibes. An eval suite turns "the bot
answers well" into a measured, regression-guarded claim — the strongest CV
artifact in this project.

- MUST: Retrieval eval: golden set of queries with expected transaction sets;
  measure recall@k / MRR of `EmbeddingService.retrieveRelevantTransactions`.
- MUST: Answer eval: golden set of QA pairs; judge correctness with an LLM-as-
  judge (or rubric) — at minimum track pass/fail on a fixed set.
- SHOULD: Eval harness runs in CI on every RAG change; results versioned.
- SHOULD: Prompt and system-prompt stored as versioned resources (already
  partially done — system prompt is in `RagService`, promote to a constant file).
- OPTIONAL: A/B the embedding model / chunking strategy against the eval set.
- OPTIONAL: Track cost + latency per eval run.

**Dependency:** Phase 6 (eval needs the resolved, secure RAG pipeline).

---

## Phase 8 — Security and supply chain (SHOULD)

**Rationale:** Security beyond secrets: dependency hygiene, image signing,
least-privilege, and a red-team mindset. Builds on Phase 0 and 4 tooling.

- MUST: Enforce RBAC end-to-end: role checks in gateway (route-level) and/or
  services (`@PreAuthorize`); tests proving role enforcement.
- MUST: Fix refresh/logout route exposure (public-path list) and align access
  token TTL config with the claimed 15 minutes.
- MUST: CORS explicitly restricted per environment; no wildcard origins.
- SHOULD: SBOM + image signing in CI (from Phase 4); Trivy gate on CVEs.
- SHOULD: Secrets management with Vault (or cloud-native secret store) — retire
  env-var-only if platform allows.
- SHOULD: OWASP ZAP / dependency scanning on the running gateway.
- OPTIONAL: Adaptive red teaming baseline against the AI endpoint
  (prompt-injection, data exfiltration attempts) — see `external-concepts.md`.

**Dependency:** Phases 0, 4, 6 (secrets, CI scanning, AI endpoint exist).

---

## Phase 9 — Platform maturity / FinOps (OPTIONAL)

**Rationale:** Production maturity is about cost, capacity, and process — things
this repo cannot fully demonstrate alone (see `external-concepts.md`), but the
tooling groundwork can be laid.

- MUST (if pursued): Resource requests/limits on k8s manifests; cost tagging
  (namespace labels); Grafana cost dashboard from Prometheus metrics.
- SHOULD: Capacity planning doc: expected QPS per service, memory/CPU per pod,
  Kafka partition sizing rationale.
- OPTIONAL: Canary/blue-green via Argo Rollouts with metrics-driven promotion
  (ties to Phase 5/9 traffic distribution concept).
- OPTIONAL: Load testing (k6) with recorded baselines for the SLOs defined in
  Phase 3.

**Dependency:** Phase 5 (k8s) and Phase 3 (metrics/SLOs).

---

## Dependency graph (summary)

```
Phase 0 (stabilize/security)
   └─▶ Phase 1 (tests/contracts/errors)
          └─▶ Phase 2 (eventing) ──┐
          └─▶ Phase 3 (observability) ──┐
                └─▶ Phase 4 (container/CI) ──▶ Phase 5 (k8s/GitOps) ──▶ Phase 9 (maturity/FinOps)
          └─▶ Phase 6 (prod AI/RAG) ──▶ Phase 7 (eval/LLMOps)
                └─▶ Phase 8 (security/supply chain)
```

Phases 0–2 and 4 are the spine a supervisor will most likely want to see first;
3, 6, 7 are where the most impressive portfolio evidence lives; 5 and 9 are the
platform-maturity differentiators.
