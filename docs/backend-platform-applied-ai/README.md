# FinSight — Backend Platform, Applied AI: Supervisor Roadmap

Documentation set for turning the FinSight microservices codebase into a
production-ready platform, presented as a supervisor roadmap. Every claim here
was checked against the current repository state on branch
`docs/backend-platform-applied-ai` — nothing is described as implemented unless
the code says so.

## What this folder is

A set of Markdown files that map where FinSight is today, where it needs to go,
what to learn to get there, and what evidence proves progress. It is written
for a supervisor review: phase-based, dependency-ordered, and measurable.

## Files

| File | Purpose |
|------|---------|
| `current-state.md` | Audited inventory: implemented, incomplete, risks — with repository paths. Read this first. |
| `roadmap.md` | Phased plan (Phase 0–9) with rationale and dependencies. |
| `implementation-milestones.md` | Checklist with definition of done and evidence artifacts for each milestone. |
| `learning-plan.md` | Week-by-week skill plan with concrete exercises. |
| `technology-map.md` | Each technology mapped to real-company usage and honest alternatives (anti-cargo-cult). |
| `external-concepts.md` | Concepts that cannot be credibly simulated alone, and how to learn them outside the repo. |

## How to use these docs

1. **Start with `current-state.md`.** It separates what actually works from what
   is stubbed or broken. Use it as the single source of truth when discussing
   scope with a supervisor — it prevents claiming capabilities that do not exist.
2. **Read `roadmap.md` next.** Phases are ordered by dependency: security and
   stability come before tests, tests before eventing, eventing before
   observability, and so on. Skip nothing before Phase 0 is done.
3. **Track work with `implementation-milestones.md`.** Each milestone has a
   definition of done and an evidence artifact. Evidence artifacts are what you
   show in a CV or interview — a passing CI build, a Grafana dashboard, a load
   test report.
4. **Use `learning-plan.md` to schedule study** around the milestones. Each week
   maps to a milestone phase.
5. **Consult `technology-map.md` when making technology choices.** It tells you
   who actually uses each tool in production and what the alternatives are, so
   decisions are grounded rather than hype-driven.
6. **Read `external-concepts.md` before claiming "full platform maturity".**
   On-call, multi-team adoption, chargeback, audits, procurement, governance,
   red teaming, and production traffic distribution cannot be demonstrated by
   this repository alone. Plan how to learn them outside.

## Recommended first milestone

**Milestone 1 — Trust and stability baseline** (see `implementation-milestones.md`).

Rationale: three of the highest-risk findings in `current-state.md` are in this
one milestone, and each sub-task is small:

1. **Rotate and externalize secrets.** Real API-key fallback values for Groq and
   HuggingFace are present in the local (git-ignored)
   `ai-service/src/main/resources/local-overrides.yml`, and a
   placeholder JWT secret is shared between `api-gateway` and `auth-service`.
   This is the single most urgent item — keys must be rotated and moved to
   environment variables before any further work.
2. **Implement consistent exception handling.** `account-service`'s
   `GlobalExceptionHandler` currently `extends RuntimeException`, which is not a
   handler at all. Unify error responses across services using the structured
   `{"detail": {"message", "code", "field"}}` contract.
3. **Write the first meaningful tests.** Today every service has only
   `contextLoads()`. Add a real integration test for the auth flow (register →
   login → refresh → logout) using Testcontainers.

DoD and evidence for these three items are in `implementation-milestones.md`.
This milestone should take roughly two weeks and produces the first CV-visible
artifact: a test report and a secrets-scan clean run.

## Honesty rule

If something is not implemented, these docs say so. If a feature is partial or
stubbed, it is listed as incomplete with the relevant file path. Never present
the roadmap as the current state — the roadmap is the plan, `current-state.md`
is the truth.
