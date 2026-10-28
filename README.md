# FinSight

**An event-driven personal finance platform built as a microservices system in Java 21 / Spring Boot — with an AI assistant that answers questions about your own money.**

[![CI](https://github.com/wassimhajjijunior/finsight/actions/workflows/ci.yml/badge.svg)](https://github.com/wassimhajjijunior/finsight/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-21-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-green)
![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.1-green)
![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0-purple)
![Docker](https://img.shields.io/badge/Docker-Compose-blue)

---

## Table of contents

- [What it does](#what-it-does)
- [Architecture](#architecture)
- [Why microservices](#why-microservices)
- [Services](#services)
- [Tech stack](#tech-stack)
- [Quick start](#quick-start)
- [What is verified](#what-is-verified)
- [Design decisions and trade-offs](#design-decisions-and-trade-offs)
- [Project structure](#project-structure)
- [Testing and CI](#testing-and-ci)
- [Known limitations](#known-limitations)
- [Roadmap](#roadmap)
- [Further reading](#further-reading)

---

## What it does

Users register, open accounts, record transactions, and set category budgets.
When money moves, the system reacts asynchronously: the transaction is published as
an event, notifications are emailed, and the transaction is indexed for semantic
retrieval by the AI assistant.

The assistant answers questions like *"how much did I spend on food?"* by calling
live services through **LLM tool calling**, and — when a funded embedding provider is
configured — augments answers with **retrieval** over the user's indexed history.

---

## Architecture

```
                            ┌──────────────────┐
             HTTP           │   api-gateway    │  JWT validation
        ┌──────────────────▶│  Spring Cloud    │  correlation ID
        │                   │     Gateway      │  structured logging
        │                   └────────┬─────────┘
        │                            │  lb://  (Eureka discovery)
        │      ┌───────────────┬─────┴────────┬───────────────┐
        │      ▼               ▼              ▼               ▼
        │  ┌────────┐   ┌────────────┐  ┌──────────┐   ┌──────────┐
        │  │  auth  │   │  account   │  │transaction│   │  budget  │
        │  │service │   │  service   │  │  service  │   │ service  │
        │  └───┬────┘   └─────┬──────┘  └────┬─────┘   └────┬─────┘
        │      │              │              │              │
        │  ┌───▼───┐      ┌───▼────┐    ┌────▼─────┐   ┌────▼─────┐
        │  │auth-db│      │account │    │transaction│   │ budget-db│
        │  │  PG   │      │  -db   │    │   -db     │   │    PG    │
        │  └───────┘      └────────┘    └────┬──────┘   └────┬─────┘
        │                                    │               │
        │                              transactional     transactional
        │                                 outbox            outbox
        │                                    │               │
        │                              ┌─────▼───────────────▼─────┐
        │                              │           Kafka           │
        │                              └─────┬───────────────┬─────┘
        │                                    │               │
        │                              ┌─────▼─────┐   ┌─────▼──────────┐
        │                              │ai-service │   │notification-   │
        │                              │   RAG     │   │   service      │
        │                              └─────┬─────┘   └────────────────┘
        │                                    │
        │                              ┌─────▼─────┐
        │                              │  ai-db    │
        │                              │ pgvector  │
        │                              └───────────┘
        │
   ┌────▼─────┐
   │  client  │
   └──────────┘

  Observability: Prometheus · Grafana · Zipkin · Loki / Promtail
```

---

## Why microservices

This project follows the decomposition and reliability patterns described in
Chris Richardson's *Microservices Patterns*. The interesting part is not the number
of services — it is the invariants each pattern protects.

**The honest starting point:** for a single-user finance tracker, a modular monolith
would be simpler, faster, and cheaper to operate. This repository exists partly to
make that trade-off visible. The operational cost is real: six databases, a broker,
discovery, and a gateway must all be healthy before a user can record a coffee.

**Decomposition by business capability.** Auth, accounts, transactions, budgets,
notifications, and AI are separate bounded contexts that own their data.
`account-service` owns balances; `transaction-service` owns ledger entries. No
service reads another's tables.

**Database per service.** Six PostgreSQL databases, one per domain. This is the
single most important constraint: cross-service joins become impossible, so every
cross-boundary read must be an explicit API call or an event. That friction is the
point — it makes coupling visible early.

**Transactional outbox.** A business change and its event must commit atomically, and
you cannot atomically write to PostgreSQL and Kafka. So the write path inserts an
`outbox_events` row in the *same* transaction as the business change. A relay polls
unpublished rows, publishes them, and marks them published only on success. This
trades latency for guaranteed delivery, and removes the need for a distributed
transaction.

**Idempotent consumers.** Kafka gives at-least-once delivery, so duplicates are
normal, not exceptional. Consumers keep a `processed_events` table and skip event IDs
they have already handled. Correctness lives in the consumer, not the broker.

**API gateway as trust boundary.** The gateway validates the JWT once, strips the
`Authorization` header, and injects `X-User-Id` / `X-User-Email` / `X-User-Role`.
Downstream services never parse tokens, so auth logic lives in exactly one place.

**Sagas were deliberately avoided.** Every flow here degrades gracefully: a failed
embedding or email does not roll back a recorded transaction. Ordering is enforced by
the outbox and consumer idempotency rather than two-phase commit.

---

## Services

| Service | Responsibility | Data store |
|---|---|---|
| `api-gateway` | Edge routing, JWT validation, correlation IDs | Redis |
| `auth-service` | Registration, login, JWT issuance, refresh-token rotation | `auth-db` + Redis |
| `account-service` | Accounts and ownership | `account-db` |
| `transaction-service` | Transaction ledger, outbox relay, spending aggregation | `transaction-db` |
| `budget-service` | Budgets, threshold evaluation, outbox relay | `budget-db` |
| `ai-service` | LLM chat, tool calling, embeddings, Kafka indexing | `ai-db` (pgvector) |
| `notification-service` | Kafka consumers, transactional email | `notification-db` |
| `eureka-server` | Service discovery | — |
| `config-server` | Optional centralized configuration | — |

---

## Tech stack

| Area | Choice |
|---|---|
| Language / runtime | Java 21 |
| Framework | Spring Boot 4.1, Spring Cloud 2025.1.2 |
| AI | Spring AI 2.0, Groq (chat), HuggingFace Inference (embeddings) |
| Data | PostgreSQL 15/16, pgvector, Redis 7, Flyway |
| Messaging | Apache Kafka, transactional outbox, idempotent consumers |
| Resilience | Feign clients with fallbacks |
| Observability | Micrometer, Prometheus, Grafana, Zipkin, Loki / Promtail |
| Packaging | Multi-stage Docker builds, Docker Compose |
| CI | GitHub Actions |

---

## Quick start

Requirements: Docker Desktop with Compose v2 and roughly 8 GB of free RAM.

```bash
# 1. Provide the required secrets (never commit these)
export JWT_SECRET="$(openssl rand -base64 32)"
export GROQ_API_KEY="gsk_..."
export HF_API_KEY="hf_..."

# 2. Build and start the platform
docker compose up -d --build

# 3. Wait for services to register, then check the gateway
curl http://localhost:8080/actuator/health
```

PowerShell equivalent:

```powershell
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
$env:JWT_SECRET = [Convert]::ToBase64String($bytes)
$env:GROQ_API_KEY = "gsk_..."
$env:HF_API_KEY   = "hf_..."
docker compose up -d --build
```

> The stack is self-contained: `SPRING_CLOUD_CONFIG_ENABLED=false` disables the
> optional Config Server import, and all configuration is injected through
> environment variables. No external configuration repository is required to run it.

### First request

```bash
# register (public route, no token required)
curl -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"me@example.com","password":"StrongPass123!","firstName":"A","lastName":"B"}'

# authenticated call — replace <token> with the accessToken from the response
curl http://localhost:8080/api/accounts \
  -H "Authorization: Bearer <token>"
```

### Endpoints

| What | URL |
|---|---|
| API gateway | http://localhost:8080 |
| Eureka dashboard | http://localhost:8761 |
| Prometheus | http://localhost:9090 |
| Grafana (`admin` / `admin`) | http://localhost:3000 |
| Zipkin | http://localhost:9411 |
| Mailhog (captured email) | http://localhost:8025 |
| Kafka UI | http://localhost:8090 |

Service host ports: auth `18082`, transaction `18083`, account `18084`, ai `18085`,
budget `18086`, notification `18087`.

---

## What is verified

These behaviours were exercised against the running Docker Compose stack, not just
compiled:

| Check | Result |
|---|---|
| All 7 application services register with Eureka | ✅ |
| Prometheus scrapes every service | ✅ 8/8 targets `up` |
| Grafana provisions the dashboard as code | ✅ |
| Register and login through the gateway | ✅ `201` + JWT |
| Create account and transaction through the gateway | ✅ |
| Transaction committed and outbox event published | ✅ `published = true` |
| Kafka → `notification-service` → email delivered to Mailhog | ✅ |
| Kafka → `ai-service` consumes the event | ✅ |
| AI assistant answers using live data via tool calling | ✅ returned the real transaction |
| Flyway migrations apply on startup (`V1`, `V2`) | ✅ |

Two defects found and fixed while bringing this up, both worth recording:

1. **Spring Cloud Gateway 5.x moved route configuration** to
   `spring.cloud.gateway.server.webflux.routes`. Routes declared under the old path
   are silently ignored and every request returns 404.
2. **`eureka.client.serviceUrl` is a `Map`, and map keys are case-sensitive.**
   Environment-variable binding uppercases the key to `DEFAULTZONE`, so Eureka's
   lookup for `defaultZone` misses and every client silently falls back to
   `localhost`. Setting it in YAML preserves the case.

Both are examples of the same lesson: in distributed systems, failure is usually
silent.

---

## Design decisions and trade-offs

**Database per service** — prevents accidental cross-domain joins and forces explicit
contracts. Cost: no ad-hoc cross-domain reporting.

**Transactional outbox instead of dual writes** — guarantees "business change
committed ⇒ event will eventually publish". Cost: polling latency, plus the outbox
table needs pruning. Debezium CDC would remove the polling; the trade-off is
documented rather than hidden.

**At-least-once delivery plus consumer deduplication** — simpler and more robust than
attempting exactly-once semantics across a broker and a database.

**JWT at the edge, identity headers inside** — one validation point, no token parsing
scattered across services. Cost: services must not be reachable except through the
gateway.

**Spring AI with a custom embedding adapter** — the HuggingFace starter in this
version ships no embedding model, so the service implements `EmbeddingModel` against
the inference HTTP API. This keeps the stack single-language at the cost of owning
that adapter.

**Observability from day one** — correlation IDs, Prometheus metrics, distributed
traces, and structured logs are wired at the gateway and propagated downstream, so
failures can be diagnosed rather than guessed at.

---

## Project structure

```
finsight/
├── api-gateway/            # Spring Cloud Gateway (WebFlux), JWT filter chain
├── auth-service/           # JWT issuance, refresh-token rotation in Redis
├── account-service/        # accounts and ownership
├── transaction-service/    # ledger + transactional outbox
├── budget-service/         # budgets + outbox + threshold alerts
├── ai-service/             # LLM chat, tool calling, embeddings, Kafka consumer
├── notification-service/   # Kafka consumers + Thymeleaf email
├── eureka-server/          # service discovery
├── config-server/          # optional centralized config
├── infrastructure/
│   ├── prometheus/         # scrape configuration
│   ├── grafana/            # dashboards-as-code provisioning
│   └── promtail/           # log shipping
├── docker-compose.yml
└── .github/workflows/ci.yml
```

Each service follows the same internal layout:

```
src/main/java/com/finsight/<service>/
├── controller/   # HTTP endpoints only
├── service/      # business logic
├── repository/   # persistence
├── client/       # Feign clients
├── entity/       # ORM models
├── dto/          # request/response shapes
└── exception/    # structured error handling
```

---

## Testing and CI

GitHub Actions runs four jobs on every push and pull request:

1. **Build** — compiles and packages all modules.
2. **Contract tests** — standalone `@RestControllerAdvice` tests asserting the shared
   error shape `{"detail":{"message","code","field"}}`. No database or broker needed.
3. **Secret scan** — gitleaks over tracked files.
4. **Images** — builds every service image and publishes to GHCR on `main`.

Run the contract tests locally:

```bash
mvn test -Dtest=GlobalExceptionHandlerTest -DfailIfNoTests=false
```

The remaining `contextLoads` tests require live infrastructure and are not part of
the CI gate.

---

## Known limitations

Stated plainly, because the value of this repository is honesty about what it
demonstrates.

- **Semantic retrieval is disabled in the default demo.** The embedding provider is
  HuggingFace Inference, and the demo account has no inference credits. The assistant
  therefore answers through tool calling against live services (verified working),
  while retrieval degrades gracefully to an empty context. A funded `HF_API_KEY`
  re-enables RAG with no code change.
- **No RAG evaluation harness.** Retrieval quality is not measured. A golden set with
  recall@k and faithfulness scoring is the highest-value missing piece.
- **No Kubernetes or GitOps.** Deployment is Docker Compose. Images exist and CI
  publishes them; the next step is `kind`/`k3d` → manifests → Argo CD.
- **RBAC is propagated but not enforced.** The role claim reaches services as a
  header, but no endpoint checks it yet.
- **Account balances are not updated by transactions.** The field exists; the
  projection does not.
- **Consumer integration tests are missing.** Outbox and dedup logic are implemented
  but not proven under duplicate delivery in CI.
- **`config-server` points at an external Git repository** and is disabled in the
  Compose stack.

---

## Roadmap

| Phase | Focus | Status |
|---|---|---|
| 0 | Microservices, database per service, outbox, idempotent consumers, gateway | ✅ done |
| 1 | Containerization, Compose stack, CI | ✅ done |
| 2 | Observability: metrics, dashboards, tracing, log correlation | 🟡 partial |
| 3 | Kubernetes manifests and GitOps delivery | ⬜ next |
| 4 | Security hardening: RBAC enforcement, CORS, secret management | ⬜ planned |
| 5 | RAG evaluation harness and quality gates | ⬜ planned |

---

## Further reading

- Chris Richardson, *Microservices Patterns* — decomposition, database per service,
  sagas, transactional outbox, API composition
- [microservices.io](https://microservices.io/patterns/) — pattern catalogue
- [Spring AI reference](https://docs.spring.io/spring-ai/reference/)
- [Transactional outbox pattern](https://microservices.io/patterns/data/transactional-outbox.html)
- [Docker multi-stage builds](https://docs.docker.com/build/building/multi-stage/)

---

## License

No license file is included yet. If you intend to reuse this code, open an issue and
I will add one.
