# FinSight — Technology Map

Maps every technology in FinSight to **who actually uses it in production** and
**honest alternatives**. Purpose: avoid cargo cult — a tool is justified by what
it solves here, not by its popularity. Claims are directional (industry
knowledge), not guarantees about specific companies' current stacks.

---

## Language / runtime

### Java 21 + Spring Boot 4.1
- **Real usage:** Java is the backbone of financial services (banks, payment
  processors) and large-scale e-commerce. Spring Boot is the dominant Java
  framework for REST microservices; companies like Netflix (Java + Spring,
  historically), Spotify, and most fintechs run large Spring/Java estates.
- **Alternatives:** Go (Kubernetes, gRPC-heavy platforms), Node.js/NestJS
  (startup speed), .NET (enterprise/Microsoft shops), Quarkus or Micronaut
  (lighter JVM frameworks with faster startup).
- **FinSight fit:** Matches the "backend engineer, Java microservices" profile
  and the SUPCOM curriculum. Keep.

### Spring Cloud (2025.1.2)
- **Real usage:** Spring Cloud (Gateway, Config, Netflix components) is used
  widely in Java shops. Note: Spring Cloud Netflix *Eureka* is legacy — many
  teams migrated to Kubernetes-native discovery.
- **Alternatives:** Kubernetes-native (CoreDNS + k8s Services), Consul, gRPC +
  service mesh (Istio/Linkerd).
- **FinSight fit:** Good learning value; but Phase 5 (k8s) should trigger an ADR:
  keep Eureka (still valid for non-k8s deployments) or retire it.

---

## Service infrastructure

### API Gateway (Spring Cloud Gateway)
- **Real usage:** Gateway-as-edge is standard in microservices; Spring Cloud
  Gateway is popular in Java shops. Kong and Envoy are the big non-Java names
  (Envoy powers many service meshes).
- **Alternatives:** Kong, Traefik, Envoy, NGINX, AWS API Gateway.
- **FinSight fit:** The gateway trust boundary (validate JWT, inject
  `X-User-*`, strip `Authorization`) is exactly how banks and fintechs do
  authN at the edge. Strong.

### Config Server (Spring Cloud Config)
- **Real usage:** Originated at Netflix/Spring; still used in Java estates.
- **Alternatives:** Consul KV, etcd, Kubernetes ConfigMaps/Secrets,
  cloud-native (AWS AppConfig, Vault).
- **FinSight fit:** Fine for now, but routes+config living in an *external git
  repo* is a reproducibility risk — CI should validate config server bootstrap
  in tests.

### Eureka
- **Real usage:** Historically Netflix. Most modern teams that moved to k8s
  dropped it for CoreDNS; on VMs it is still used.
- **Alternatives:** CoreDNS (k8s), Consul, Nacos (Alibaba), Zookeeper.
- **FinSight fit:** Not cargo cult per se, but the k8s migration (Phase 5)
  should revisit it — running Eureka *and* k8s DNS is often redundant.

### Kafka (Confluent)
- **Real usage:** Kafka is the de-facto event backbone: LinkedIn (creator),
  Uber, Shopify, Netflix (streaming), most fintechs for ledger/notification
  events.
- **Alternatives:** RabbitMQ (smaller scale, simpler routing), NATS (lightweight),
  Pulsar (multi-tenant streaming), AWS SQS/Kinesis (managed, cloud-locked).
- **FinSight fit:** The outbox + idempotent consumer + Kafka combination is the
  canonical reliable-event-delivery pattern in production fintech. Excellent.

### Transactional outbox + idempotent consumers
- **Real usage:** Uber, Shopify, and many banks use outbox/CDC (Debezium) for
  reliable events. Idempotent consumers with dedup tables are standard practice.
- **Alternatives:** Debezium CDC (sub-second, no polling), two-phase commit
  (avoid), event sourcing (bigger architectural shift).
- **FinSight fit:** Already implemented (polling relay). The honest upgrade is
  CDC; document the tradeoff in an ADR rather than silently keeping polling.

---

## Data

### PostgreSQL (per service) + Flyway
- **Real usage:** Postgres is the default relational DB across startups and
  enterprises (Instagram, Reddit, Apple, most fintechs). Database-per-service is
  the microservices rule (each team owns its schema). Flyway/Liquibase are the
  standard migration tools.
- **Alternatives:** MySQL (equally common), Aurora (managed PG/MySQL), Yugabyte
  (distributed Postgres), and for migrations Liquibase.
- **FinSight fit:** DB-per-service + Flyway is exactly right; the audit found no
  shared-DB violations. Keep.

### pgvector
- **Real usage:** pgvector is the most common "add embeddings to my Postgres"
  choice (Supabase, Neon, many RAG stacks); OpenAI and LangChain ecosystems
  support it. Used when the vector corpus fits in a single Postgres and you want
  one database to manage.
- **Alternatives:** Pinecone, Weaviate, Qdrant, Milvus (dedicated vector DBs at
  larger scale), Elasticsearch (hybrid search).
- **FinSight fit:** Right-sized for a single-user finance RAG corpus. The risk
  is *schema divergence* between the manual migration and Spring AI's
  `PgVectorStore` — resolve with a single source of truth (M7).

---

## AI / RAG

### Spring AI 2.0
- **Real usage:** Spring AI is newer; adopted by Java teams wanting LLM
  integration without a second stack. Still maturing compared to Python
  ecosystems.
- **Alternatives:** LangChain4j (Java), LangChain/LangGraph (Python), LlamaIndex,
  Semantic Kernel (.NET). For pure Java: LangChain4j is the main rival.
- **FinSight fit:** Choosing it keeps the stack 100% Java — defensible, but note
  in interviews that Python dominates AI infra and the choice was a
  stack-cohesion tradeoff.

### Groq chat (Llama 3.1)
- **Real usage:** Groq sells fast LPU inference and is used by AI startups for
  low-latency chat. Llama models are the open-weight standard.
- **Alternatives:** OpenAI, Anthropic (hosted); Mistral (open-weight); Ollama /
  vLLM (self-hosted).
- **FinSight fit:** Fine for a demo/PFE; the LLM provider should be a config
  abstraction so it can be swapped — that is already mostly true
  (OpenAI-compatible base URL).

### HuggingFace embeddings (custom client)
- **Real usage:** HuggingFace inference is common for open embeddings (e.g.,
  `nomic-embed-text`, `bge`, `gte`). Self-hosting (vLLM, TEI, Ollama) is the
  production-grade alternative many teams move to for cost/latency/control.
- **Alternatives:** OpenAI `text-embedding-3`, Cohere, Voyage (hosted);
  self-hosted TEI/vLLM/Ollama.
- **FinSight fit:** The committed key + external dependency is the problem (M7).
  Make the provider pluggable and offer a self-hosted fallback.

---

## Observability

### Prometheus + Grafana
- **Real usage:** The industry-standard metrics stack (Spotify, SoundCloud,
  DigitalOcean, virtually every SRE team). Dashboards-as-code (JSON) is the norm.
- **Alternatives:** Datadog, New Relic (SaaS), VictoriaMetrics/Mimir (scaled
  Prometheus), CloudWatch (AWS).
- **FinSight fit:** Compose wiring exists; the gap is dashboards + alerts (M4).
  Grafana dashboards as committed JSON is a great interview artifact.

### Zipkin / Micrometer Tracing
- **Real usage:** Zipkin started at Twitter; OpenTelemetry is now the standard
  for traces (with Zipkin/Jaeger/Tempo backends). Micrometer bridges Spring to
  OTel.
- **Alternatives:** Jaeger, Grafana Tempo, Datadog APM.
- **FinSight fit:** Traces exist in code (the AI consumer opens spans manually).
  Finish correlation propagation end-to-end (M4).

### Loki + Promtail
- **Real usage:** Grafana Labs' log stack; used when you want logs next to
  metrics in Grafana and cheap label-based search.
- **Alternatives:** ELK (Elasticsearch — heavier, full-text), Splunk
  (enterprise), Datadog logs.
- **FinSight fit:** Promtail currently scrapes host `/var/log` — service logs
  are not shipped (M4). Structured JSON logs with correlation IDs are the fix.

### Mailhog
- **Real usage:** Dev-only mail catcher; used in local dev everywhere.
- **Alternatives:** Mailpit, local SMTP (MailHog's own). Never for prod — it is a
  dev tool by design.

---

## Platform / delivery

### Docker Compose
- **Real usage:** Standard for local dev environments at every company. Not a
  production deployment tool.
- **Alternatives (prod):** Kubernetes, AWS ECS/EKS, Nomad.
- **FinSight fit:** Keep for dev; the production story is Phase 4/5
  (Dockerfiles → k8s).

### Kubernetes + GitOps (planned)
- **Real usage:** k8s is the platform standard (Google, Spotify, most SaaS);
  GitOps (ArgoCD/Flux) is the leading deployment model in k8s shops.
- **Alternatives:** ECS/Fargate (simpler AWS), Nomad (simpler ops), PaaS
  (Heroku/Fly).
- **FinSight fit:** Adds real value for self-healing + reproducible envs. But:
  if a supervisor pushes back on k8s, ECS + Terraform is a credible lighter
  alternative — decide by where the internship/job market points.

### Resilience4j
- **Real usage:** Standard for Java circuit breakers (Spring Cloud Circuit
  Breaker wraps it); Sentinel (Alibaba) is the main alternative. Hystrix is
  retired.
- **FinSight fit:** Currently *absent* — budget-service has a fallback bean but
  no breaker. Wire it properly (retry, timeout, circuit breaker) in M3/M9.

---

## Anti-cargo-cult rules for this project

1. **Don't add k8s because it's trendy** — add it because Phase 4 gives you
   images and CI to deploy, and the market rewards it. If time is short, ECS +
   Terraform is an honest alternative.
2. **Don't keep Eureka just because it's in the stack** — the k8s ADR should
   decide Eureka vs CoreDNS based on where the platform actually runs.
3. **Don't replace polling outbox with CDC just because Debezium is cool** —
   only if latency requirements demand it; an ADR with numbers beats a blind
   swap.
4. **Don't add a dedicated vector DB** — pgvector is correct until the corpus
   or query rate outgrows Postgres, which is not on the horizon here.
5. **Don't introduce Python AI tooling** — the stack is Java; Spring AI's
   limitations are accepted, documented, and reasoned about.
6. **Don't treat Mailhog/Promtail defaults as production** — they are dev
   scaffolding; production logging/metrics are M4's job.