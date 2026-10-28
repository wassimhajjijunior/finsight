# FinSight

A personal-finance intelligence platform built as an event-driven microservices
system in Java 21 / Spring Boot, with a retrieval-augmented AI assistant over
your own transaction history.

FinSight is a study in applying distributed-systems patterns to a real problem:
money data is small, highly relational, and unforgiving about correctness — which
makes it a good domain for learning what microservices actually cost and when
they are worth it.

---

## Table of contents

- [What it does](#what-it-does)
- [Architecture](#architecture)
- [Why microservices](#why-microservices)
- [Services](#services)
- [Tech stack](#tech-stack)
- [Quick start](#quick-start)
- [Ports and endpoints](#ports-and-endpoints)
- [Design decisions](#design-decisions)
- [Testing and CI](#testing-and-ci)
- [Known limitations](#known-limitations)
- [Further reading](#further-reading)

---

## What it does

Users register, open accounts, record transactions, and set category budgets.
When money moves, the system reacts asynchronously: transactions are indexed for
semantic search, and notifications are emailed. An AI assistant answers questions
such as *"how much did I spend on food last month?"* by calling live services
through tool calling, and — when a funded embedding provider is configured —
augments answers with retrieval over the user's indexed transaction history.

---

## Architecture

```
                            ┌──────────────────┐
             HTTP           │   api-gateway    │  JWT validation
        ┌──────────────────▶│  (Spring Cloud   │  correlation ID
        │                   │    Gateway)      │  structured logging
        │                   └────────┬─────────┘
        │                            │  lb:// (Eureka discovery)
        │      ┌───────────────┬─────┴────────┬───────────────┐
        │      ▼               ▼              ▼               ▼
        │  ┌────────┐   ┌────────────┐  ┌──────────┐   ┌──────────┐
        │  │  auth  │   │  account   │  │transaction│   │  budget  │
        │  │service │   │  service   │  │  service  │   │ service  │
        │  └───┬────┘   └─────┬──────┘  └────┬─────┘   └────┬─────┘
        │      │              │              │              │
        │  ┌───▼───┐      ┌───▼────┐    ┌────▼─────┐   ┌────▼─────┐
        │  │auth-db│      │account │    │transaction│   │ budget-db│
        │  │(PG)   │      │ -db    │    │   -db     │   │  (PG)    │
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
        │                              │  (RAG)    │   │   service      │
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

  Observability: Prometheus · Grafana · Zipkin · Loki/Promtail
```

---

## Why microservices

This project follows the decomposition and reliability patterns described in
Chris Richardson's *Microservices Patterns*. The interesting part is not the
number of services — it is the invariants each pattern protects.

**Decomposition by business capability.** Auth, accounts, transactions, budgets,
notifications, and AI are separate bounded contexts with separate owners of their
data. `account-service` owns balances; `transaction-service` owns the ledger
entries. No service reaches into another's tables.

**Database per service.** Six PostgreSQL databases, one per domain. This is the
single most important constraint: it makes cross-service joins impossible, so
every cross-boundary read has to be an explicit API or event. That friction is
the point — it forces you to notice coupling early.

**Transactional outbox.** A transaction and its event must commit atomically.
Writing to Kafka inside a database transaction is not atomic, so instead the
write path inserts an `outbox_events` row in the *same* transaction as the
business change. A relay polls unpublished rows and publishes them, marking them
published only on success. This trades latency (polling) for guaranteed
delivery — and it is why the project does not need a distributed transaction.

**Idempotent consumers.** Kafka gives at-least-once delivery, so duplicates are
normal, not exceptional. Consumers keep a `processed_events` table and skip event
IDs they have already handled. Correctness lives in the consumer, not in the
broker.

**API gateway as trust boundary.** The gateway validates the JWT once, strips the
`Authorization` header, and injects `X-User-Id` / `X-User-Email` / `X-User-Role`.
Downstream services never parse tokens. This keeps auth logic in one place and
means a compromised service cannot mint identities.

**Sagas and distributed transactions were deliberately avoided.** Every flow here
degrades gracefully: a failed embedding or email does not roll back a recorded
transaction. Where ordering matters, it is enforced by the outbox and by consumer
idempotency rather than by two-phase commit.

**When microservices are the wrong answer.** For a single-user finance tracker,
a modular monolith would be simpler and faster to operate. This repository exists
partly to make that trade-off visible: the operational cost is real — six
databases, a broker, discovery, and a gateway, all of which must be healthy
before a user can record a coffee.

---

## Services

| Service | Responsibility | Data store |
|---|---|---|
| `api-gateway` | Edge routing, JWT validation, correlation IDs | Redis (rate limiting) |
| `auth-service` | Registration, login, JWT issuance, refresh-token rotation | `auth-db` + Redis |
| `account-service` | Accounts and ownership | `account-db` |
| `transaction-service` | Transaction ledger, outbox relay, spending aggregation | `transaction-db` |
| `budget-service` | Budgets and threshold evaluation, outbox relay | `budget-db` |
| `ai-service` | RAG chat, embeddings, tool calling, Kafka indexing | `ai-db` (pgvector) |
| `notification-service` | Kafka consumers, transactional email | `notification-db` |
| `eureka-server` | Service discovery | — |
| `config-server` | Optional centralized configuration | — |

---

## Tech stack

- **Language / runtime:** Java 21
- **Framework:** Spring Boot 4.1, Spring Cloud 2025.1.2
- **AI:** Spring AI 2.0, Groq (chat), HuggingFace Inference API (embeddings)
- **Data:** PostgreSQL 15/16, pgvector, Redis 7, Flyway migrations
- **Messaging:** Apache Kafka, transactional outbox, idempotent consumers
- **Resilience:** Feign clients with fallbacks
- **Observability:** Micrometer, Prometheus, Grafana, Zipkin, Loki/Promtail
- **Packaging:** multi-stage Docker builds, Docker Compose
- **CI:** GitHub Actions (build, contract tests, secret scan, image build)

---

## Quick start

Requirements: Docker Desktop with Compose v2, and about 8 GB of free RAM.

```bash
# 1. Provide the two external AI credentials (never commit them)
export JWT_SECRET="$(openssl rand -base64 32)"
export GROQ_API_KEY="gsk_..."
export HF_API_KEY="hf_..."

# 2. Build and start the whole platform
docker compose up -d --build

# 3. Watch services register with Eureka
open http://localhost:8761
```

On Windows PowerShell:

```powershell
$env:JWT_SECRET = [Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Max 256 }))
$env:GROQ_API_KEY = "gsk_..."
$env:HF_API_KEY = "hf_..."
docker compose up -d --build
```

The stack is ready when `http://localhost:8080/actuator/health` reports `UP`.

> The `config-server` module is included for the centralized-configuration
> pattern, but the Compose stack runs with `SPRING_CLOUD_CONFIG_ENABLED=false`
> and injects configuration through environment variables. The repository is
> therefore self-contained: no external config Git repository is required.

---

## Ports and endpoints

| What | URL |
|---|---|
| API gateway | http://localhost:8080 |
| Eureka dashboard | http://localhost:8761 |
| Prometheus | http://localhost:9090 |
| Grafana (`admin` / `admin`) | http://localhost:3000 |
| Zipkin | http://localhost:9411 |
| Mailhog (captured email) | http://localhost:8025 |
| Kafka UI | http://localhost:8090 |
| Loki | http://localhost:3100 |

Host-mapped service ports: auth `18082`, transaction `18083`, account `18084`,
ai `18085`, budget `18086`, notification `18087`.

Example request through the gateway:

```bash
# register
curl -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"me@example.com","password":"StrongPass123!","firstName":"A","lastName":"B"}'

# authenticated call
curl http://localhost:8080/api/accounts -H "Authorization: Bearer <token>"
```

---

## Design decisions

**Database per service.** Prevents accidental cross-domain joins and forces
explicit contracts. Cost: no ad-hoc reporting across domains.

**Transactional outbox instead of dual writes.** Guarantees "business change
committed ⇒ event will eventually publish". Cost: polling latency, and the
outbox table needs pruning. Debezium CDC would remove the polling; the trade-off
is documented rather than silently ignored.

**At-least-once delivery plus consumer dedup.** Simpler and more robust than
attempting exactly-once semantics across a broker and a database.

**JWT at the edge, identity headers inside.** One validation point, no token
parsing scattered across services. Cost: services must not be reachable except
through the gateway, and forged `X-User-*` headers are a real risk if they are.

**Spring AI with a custom embedding adapter.** The HuggingFace starter in this
version ships no embedding model, so the service implements `EmbeddingModel`
against the inference HTTP API. This keeps the stack single-language at the cost
of owning that adapter.

**Observability from day one.** Correlation IDs, Prometheus metrics, distributed
traces, and structured logs are wired at the gateway and propagated downstream,
so failures can be diagnosed rather than guessed at.

---

## Testing and CI

GitHub Actions runs four jobs:

1. **Build** — compiles and packages every module.
2. **Contract tests** — standalone `@RestControllerAdvice` tests asserting the
   shared error shape `{"detail":{"message","code","field"}}` across services.
   These need no database or broker.
3. **Secret scan** — gitleaks over tracked files.
4. **Images** — builds every service image and publishes to GHCR on `main`.

Run the contract tests locally:

```bash
mvn test -Dtest=GlobalExceptionHandlerTest -DfailIfNoTests=false
```

The remaining `contextLoads` tests require live infrastructure and are not part
of the CI gate.

---

## Known limitations

Stated plainly, because the point of this repository is to be honest about what
it demonstrates.

- **No Kubernetes or GitOps.** Deployment is Docker Compose. The next step is
  images → `kind`/`k3d` → Argo CD, in that order.
- **Semantic retrieval is currently disabled in the shipped demo.** The embedding
  provider is HuggingFace Inference, and the demo account has no inference
  credits. The assistant therefore answers through tool calling against live
  services (which is verified working) while retrieval degrades gracefully to an
  empty context. Supplying a funded `HF_API_KEY` re-enables RAG with no code
  change.
- **No RAG evaluation harness.** Retrieval quality is not measured. A golden set
  with recall@k and faithfulness scoring is the highest-value missing piece.
- **RBAC is propagated but not enforced.** The role claim reaches services as a
  header, but no endpoint checks it yet.
- **Account balances are not updated by transactions.** The field exists; the
  projection does not.
- **Consumer integration tests are missing.** The outbox and dedup logic is
  implemented but not proven under duplicate delivery in CI.
- **Shared secret placeholders.** The JWT secret must be supplied via environment
  variables; the repository ships no defaults.
- **`config-server` points at an external Git repository** and is disabled in the
  Compose stack.

---

## Further reading

- Chris Richardson, *Microservices Patterns* — decomposition, database per
  service, sagas, transactional outbox, API composition.
- [microservices.io](https://microservices.io/patterns/) — pattern catalogue.
- [Spring AI reference](https://docs.spring.io/spring-ai/reference/)
- [Transactional outbox pattern](https://microservices.io/patterns/data/transactional-outbox.html)
- [Docker multi-stage builds](https://docs.docker.com/build/building/multi-stage/)
