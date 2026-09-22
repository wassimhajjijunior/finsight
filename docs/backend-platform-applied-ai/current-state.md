# FinSight — Current State (audited)

Audit date: branch `docs/backend-platform-applied-ai`. All paths are relative to
the repository root. "Implemented" means the code exists and is wired; it does
not mean production-ready.

## Stack summary

Maven multi-module, Java 21, Spring Boot 4.1, Spring Cloud 2025.1.2, Spring AI
2.0. Modules declared in `pom.xml`: `config-server`, `api-gateway`,
`auth-service`, `account-service`, `transaction-service`, `budget-service`,
`ai-service`, `notification-service`.

> Note: `eureka-server/` exists with its own `pom.xml` but is **not declared as
> a module** in the parent `pom.xml`. The multi-module build does not build it.
> It must be built/run separately or added to the parent modules.

## Implemented

### Infrastructure (Docker Compose)
- `docker-compose.yml` defines: 6 PostgreSQL databases (one per service),
  pgvector-enabled DB for `ai-service`, Redis, Kafka + Zookeeper + Kafka UI,
  Zipkin, Prometheus, Grafana (with `infrastructure/grafana/provisioning`),
  Loki + Promtail, Mailhog. Healthchecks on databases, Redis, Zookeeper, Kafka.

### Service discovery & config
- `eureka-server/` — Eureka registry (standalone, not in parent modules).
- `config-server/` — pulls configuration from external git repo
  `https://github.com/wassimhajjijunior/finsight-config.git` (clone-on-start,
  force-pull). Services import config via `optional:configserver:`.
- `api-gateway/` — Spring Cloud Gateway with global filters:
  - `CorrelationIdFilter` (order -3): injects/forwards `X-Correlation-ID`.
  - `JwtAuthenticationFilter` (order -2): validates JWT, injects
    `X-User-Id`, `X-User-Email`, `X-User-Role`, strips `Authorization` header.
  - `LoggingFilter` (order -1).
  - `RateLimiterConfig` (Redis-based rate limiting per user).
  - Public paths: `/api/auth/login`, `/api/auth/register`, `/actuator`.

### Auth service
- `auth-service/` — register, login, refresh, logout.
- JWT access tokens (claims: subject, email, role, firstName) + refresh tokens
  stored in Redis with TTL, revocable.
- Refresh token **rotation** on refresh (old revoked, new issued).
- BCrypt (cost 12). Token service in
  `auth-service/.../service/TokenService.java`.
- Schema: `auth-service/src/main/resources/db/migration/V1__create_users_table.sql`.

### Account service
- `account-service/` — CRUD accounts, owner-scoped queries
  (`findByIdAndUserId`, `findActiveAccountByIdAndUserId`).
- Balance initialized to zero on creation.
- Schema: `V1__create_accounts_table.sql`.

### Transaction service
- `transaction-service/` — create/list transactions; Feign client to
  account-service (`AccountClient`).
- **Transactional outbox**: `OutboxEvent` entity + `OutboxRelayService`
  (poll every 5s, publish to Kafka topic named by event type, mark published).
  Schemas: `V2__create_outbox_table.sql`, `V3__create_processed_events_table.sql`.

### Budget service
- `budget-service/` — budgets CRUD; outbox + relay (same pattern as
  transaction-service); Feign client to transaction-service with a fallback
  (`TransactionClientFallback` returns zero spending, logs warning).

### AI / RAG service
- `ai-service/` — chat endpoint with RAG + tool calling:
  - `RagService`: system prompt, conversation history, pgvector retrieval,
    LLM tool calls (`ToolService`), sources returned in response.
  - `EmbeddingService`: indexes transaction events into pgvector; retrieval
    filtered by `userId` (tenant isolation).
  - `TransactionEventConsumer`: Kafka consumer (`transaction.created`), dedup
    via `processed_events`, manual Micrometer span for tracing.
  - Groq chat (OpenAI-compatible base URL, `llama-3.1-8b-instant`), custom
    HuggingFace embedding model (`nomic-ai/nomic-embed-text-v1.5`, 768 dims).
  - Schema: `V1__create_ai_tables.sql` — pgvector extension, `chat_messages`,
    `transaction_embeddings` (vector(768), HNSW cosine index), `processed_events`.

### Notification service
- `notification-service/` — Kafka consumers (`transaction.created`,
  budget alerts), HTML email templates, Mailhog delivery. Idempotent via
  `processed_events` table (`V1__create_notification_tables.sql`).

### Observability wiring (partial)
- Micrometer tracing enabled, Zipkin endpoint configured in services.
- Prometheus scrape config at `infrastructure/prometheus/prometheus.yml`.
- Promtail config at `infrastructure/promtail/promtail-config.yml`.
- Management endpoints expose `health,info,prometheus` (gateway also `gateway`).

## Incomplete / broken

| Item | Location | Detail |
|------|----------|--------|
| Exception handlers | `account-service/.../exception/GlobalExceptionHandler.java` | Class `extends RuntimeException` — it is **not** a `@RestControllerAdvice`. No structured error contract exists across services. |
| Tests | every `src/test/java/.../*ApplicationTests.java` | Only `contextLoads()` skeleton tests. Zero unit/integration/contract tests. |
| Dockerfiles | repository-wide | No `Dockerfile` in any service. Compose runs raw services only via build tooling? (Services are not even defined as buildable images in compose — only infra images). |
| CI/CD | repository-wide | No GitHub Actions, no pipeline, no artifact publishing. |
| Gateway routes | `api-gateway/src/main/resources/application.yml` | No routes declared in repo. Routes live in the **external** config repo (`finsight-config.git`). Repo alone is not runnable end-to-end. |
| RBAC enforcement | `api-gateway/.../JwtAuthenticationFilter.java` | Role is injected as `X-User-Role` header but **never checked** anywhere. No `@PreAuthorize`/authority checks in any service. |
| Refresh/logout route risk | `api-gateway/.../JwtAuthenticationFilter.java` | Public-path list only covers login/register/actuator. `/api/auth/refresh` and `/api/auth/logout` therefore require a *valid access token* — an expired access token cannot be refreshed (defeats refresh flow). |
| Access token TTL mismatch | `auth-service/src/main/resources/local-overrides.yml` | `jwt.expiration: 86400000` (24h) but `AuthResponse.expiresIn` claims 900s (15 min). Claim vs config divergence. |
| Account balance updates | `account-service/.../service/AccountService.java` | Balance is set to zero at creation and never updated when transactions occur. No balance mutation endpoint or event consumer. |
| Resilience4j | repository-wide | No resilience4j dependency in any `pom.xml`. `TransactionClientFallback` is a plain `@Component`, not wired to a circuit breaker. |
| RAG config/schema divergence | `ai-service/src/main/resources/application.yml`, `local-overrides.yml`, `V1__create_ai_tables.sql` | Manual schema (`transaction_embeddings` with `vector(768)` + JSONB metadata) vs Spring AI `PgVectorStore` expectations (`vectorstore.pgvector.table-name` config). Risk of runtime schema mismatch / auto-DDL behavior; needs a documented contract. |
| External HF dependency | `ai-service/.../config/HuggingFaceEmbeddingModel.java`, `application.yml` | Embeddings call the hosted HuggingFace router API with a committed key. Latency, rate limits, and key exposure are external risks; no self-hosted/fallback option. |
| Eval suite | `ai-service` | No retrieval or answer-quality evaluation. |
| Grafana dashboards | `infrastructure/grafana/provisioning/` | Only datasource provisioning (`prometheus.yml`). No dashboards, no alerts. |
| Loki/correlation propagation | `infrastructure/promtail/promtail-config.yml`, gateway | Promtail scrapes host `/var/log` only. Service logs are not shipped to Loki; correlation IDs are not propagated into structured log fields service-wide. |
| Eureka registration | `eureka-server` not in parent `pom.xml` modules | Multi-module build excludes Eureka. |
| Schema registry / AsyncAPI | repository-wide | Kafka payloads are unstructured JSON strings; no Confluent Schema Registry, no AsyncAPI contract, no versioned event schemas. |
| Kubernetes / GitOps / IaC / supply chain | repository-wide | Nothing beyond Docker Compose. No k8s manifests, no Terraform, no GitOps tooling, no SBOM/dependency scanning. |

## Risks (severity-ordered)

1. **CRITICAL — Secrets present in local fallback configuration.**
   The git-ignored `ai-service/src/main/resources/local-overrides.yml` contains
   real Groq (`gsk_...`) and HuggingFace (`hf_...`) API keys as fallback defaults.
   `api-gateway/src/main/resources/application.yml` and
   `auth-service/src/main/resources/local-overrides.yml` contain a shared
   placeholder JWT secret. Any reader of the repo can forge tokens / burn quota.
   → Rotate keys immediately; move all secrets to env vars only.
2. **HIGH — No real tests.** Only `contextLoads`. Nothing prevents regressions
   in auth, outbox relay, dedup, or RAG retrieval.
3. **HIGH — No RBAC enforcement.** Role claim is propagated but unused; any
   authenticated user can call any endpoint with any role.
4. **HIGH — Refresh flow is unreachable after access-token expiry** (see above),
   and access-token TTL (24h configured) contradicts the 15-min claim.
5. **HIGH — No container images or CI/CD.** The platform cannot be built,
   versioned, scanned, or deployed reproducibly.
6. **MEDIUM — Exception handling is stubbed.** `GlobalExceptionHandler extends
   RuntimeException` in account-service; no uniform error contract.
7. **MEDIUM — No account balance updates.** Transactions never affect balance —
   a core domain invariant of a finance platform is missing.
8. **MEDIUM — RAG schema/config divergence + external embedding dependency.**
   Runtime schema risk plus an unmanaged external API dependency with committed
   keys.
9. **MEDIUM — Observability is wiring-only.** No dashboards, no alerts, no
   SLOs, logs not shipped to Loki, correlation IDs not end-to-end.
10. **LOW/MEDIUM — Resilience4j absent.** Fallback exists but is not a circuit
    breaker; no retry/timeout/bulkhead configuration.
11. **LOW — Eureka excluded from parent build; routes external-only.** The repo
    is not self-contained or fully runnable as a unit.

## What this means

Strengths to preserve: outbox pattern, idempotent consumers, gateway trust
boundary (strip `Authorization`, inject identity), DB-per-service isolation,
Flyway migrations, RAG with tool calling + tenant filtering, and a complete
observability stack in Compose. The gaps are almost entirely "stabilize,
test, harden, package, deploy" — the architecture is sound, the operational
finishing is missing.
