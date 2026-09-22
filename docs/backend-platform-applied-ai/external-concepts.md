# FinSight — External Concepts

Concepts that **cannot be credibly simulated or demonstrated by this repository
alone**. No amount of Docker Compose, CI pipelines, or dashboards proves them.
Each entry explains why it is external, what an honest demo looks like instead,
and how to actually learn it outside the repo. Being upfront about these is a
strength in interviews, not a weakness.

---

## 1. On-call and incident command

- **Why external:** On-call means real users, real pager rotations, a real
  severity taxonomy (SEV1–3), and real pressure. A solo project has no one to
  page and no production traffic to break.
- **What you can demo instead:** incident runbooks for your own services
  ("transaction-service down" playbook), a documented severity matrix, a game
  day (deliberately kill a service and time the recovery using your Grafana
  alerts + runbook).
- **How to learn it outside:**
  - Read SRE books (Google's *Site Reliability Engineering*, free online) —
    chapters on on-call, incident response, and postmortems.
  - Volunteer for on-call at an internship/part-time job — nothing replaces
    being woken up.
  - Practice with incident simulation platforms (incident.io, Blameless,
    PagerDuty Academy) and write postmortems (blameless, 5 Whys) for your own
    outages.
  - Do a home game day: run the FinSight stack, kill Kafka or a DB, and time
    how long detection + recovery takes using your M4 alerts.

## 2. Multi-team adoption

- **Why external:** Adoption means other engineers choosing your platform —
  pull, not push. A solo repo cannot demonstrate onboarding docs, migration
  guides, support channels, or people actually depending on your contracts.
- **What you can demo instead:** a README that a stranger can use (one command
  to run the stack — currently *not* true, routes live in an external config
  repo), sample consumers, API contract docs (OpenAPI/AsyncAPI), a changelog,
  and a deprecation policy for events.
- **How to learn it outside:**
  - Contribute to open source and maintain a small OSS project — real users
    adopting your code is the closest simulation of multi-team adoption.
  - Write platform-style docs for FinSight (RFC template, ADRs, onboarding
    guide) and ask other students to follow them without your help.
  - Internships: build an internal tool/library that at least two other teams
    consume.
  - Study how platforms publish (Kubernetes, Terraform providers, Spring Boot
    starters) — adoption is a documentation + compatibility game.

## 3. Organizational chargeback / FinOps

- **Why external:** Chargeback is an org-level cost allocation mechanism (each
  team pays for what it consumes). It requires real budgets, real invoices, and
  real accounting. A solo project has no money flow.
- **What you can demo instead:** cost tagging on your k8s namespaces/labels, a
  Grafana cost dashboard, a capacity plan with per-service resource estimates
  (M10), and a written "what this would cost per month" estimate for each
  environment.
- **How to learn it outside:**
  - Take the FinOps Foundation fundamentals (free) — learn unit economics:
    per-user, per-request, per-GB cost.
  - Use AWS Free Tier / a personal cloud account and track real spend by
    resource tag; build a cost dashboard.
  - Contribute to open-source cost tools or write cost-estimation scripts for
    FinSight (LLM token cost per chat request is a genuine FinOps-style metric
    you can own).
  - Interview talking point: "In my internship I saw how X allocated infra
    costs per team — I implemented tagging + a cost dashboard as the FinSight
    equivalent."

## 4. Audits and compliance

- **Why external:** Compliance (ISO 27001, SOC 2, GDPR, PCI) is proven by
  external auditors, evidence retention policies, and legal processes — none of
  which exist in a repo. You cannot self-certify.
- **What you can demo instead:** a written security/compliance posture doc for
  FinSight (what data is processed, where it lives, encryption at rest/in
  transit, retention), a data-flow map, and a self-assessment checklist against
  a real framework (e.g., GDPR articles for user financial data, OWASP ASVS
  for the API).
- **How to learn it outside:**
  - Read the actual frameworks: GDPR (right to erasure applies to chat
    history!), ISO 27001 Annex A, SOC 2 trust criteria. Free PDFs abound.
  - Do a mock audit: print FinSight's data map and have a peer play auditor
    asking "where is user data, who can see it, how is it deleted?"
  - Internships: ask to sit in on a compliance review — the questions asked are
    the curriculum.
  - For the AI part specifically: map the RAG pipeline against AI-specific
    guidance (EU AI Act risk categories, NIST AI RMF) — honest to say "I know
    where my system sits and what it would take to comply."

## 5. Vendor procurement

- **Why external:** Procurement is real contracts, SLAs, pricing negotiations,
  and security reviews of vendors (Groq, HuggingFace, a cloud provider). A solo
  project just signs up for a free tier.
- **What you can demo instead:** a vendor comparison matrix for your AI
  dependencies (Groq vs OpenAI vs self-hosted: latency, cost, rate limits,
  data-retention policy, security posture), a decision record (ADR), and a
  total-cost-of-ownership estimate. This is genuinely impressive in interviews.
- **How to learn it outside:**
  - Run the comparison for real: benchmark Groq vs a self-hosted model vs
    another provider on the same eval set (Phase 7 gives you the harness).
  - Read public pricing pages and SLA documents; write a 1-page "if this vendor
    disappears, here is the migration path" (this is a procurement muscle).
  - Internships: volunteer to help evaluate a tool/library purchase — the
    spreadsheet with weights and scores is the deliverable.
  - Study how OSS projects handle upstream vendor risk (pinning, fallbacks,
    license checks) — you already have the theme in Phase 8 supply chain.

## 6. Organizational governance

- **Why external:** Governance is about who decides (architecture review
  boards, change advisory boards), how decisions are recorded, and how
  standards are enforced org-wide. A solo repo has no review process beyond
  yourself.
- **What you can demo instead:** a rigorous decision process *within the repo*:
  ADRs for every significant choice (outbox vs CDC, Eureka vs CoreDNS, embedding
  model), a design-review template, and a standards checklist (structured
  errors, event naming, migration rules) that CI partially enforces.
- **How to learn it outside:**
  - Adopt the ADR process seriously (Michael Nygard's ADR format); each FinSight
    milestone should ship one.
  - Join or start a student architecture review group: present your ADRs, get
    challenged, revise. That *is* governance.
  - Read engineering maturity models (DORA, SPACE) and self-assess the repo
    against them — DORA metrics (deploy frequency, lead time, MTTR, change
    failure rate) are measurable even on a solo project via your CI history.
  - Internships: attend design reviews and note how decisions actually get
    made (it is rarely pure tech).

## 7. Adaptive red teaming

- **Why external:** Red teaming against a real product requires adversarial
  specialists, threat modeling against real attackers, and iteration on
  findings. A solo project attacks itself, which is circular.
- **What you can demo instead:** a *structured* security test pass: OWASP ZAP
  scan of the gateway, OWASP WSTG checklist walkthrough of the auth flow,
  prompt-injection attempts against the chat endpoint (a mini adversarial eval),
  and a written threat model with a risk register (STRIDE per service).
- **How to learn it outside:**
  - Practice on deliberately vulnerable apps: OWASP Juice Shop, DVWA, PortSwigger
    Web Security Academy (free).
  - CTFs (picoCTF, HTB) train the attacker mindset; document every finding as
    a report, not a chat.
  - For AI specifically: read MITRE ATLAS and Microsoft's AI red-teaming
    guidance; run their prompt-injection test cases against FinSight's chat
    endpoint and document results.
  - Real adaptive red teaming only happens with a real target + real defenders —
    say honestly: "I ran structured adversarial tests; adaptive red teaming
    against production is a skill I need a team to practice."

## 8. Production traffic distribution

- **Why external:** Canaries, blue-green, A/B traffic splitting, and gradual
  rollouts require real users to split. Deploying to an empty cluster is not
  traffic distribution.
- **What you can demo instead:** the *tooling and process*: Argo Rollouts
  canary strategy with metrics-driven promotion, feature flags, a documented
  rollout policy (5% → 25% → 100% with SLO gates from M4), and a k6 load test
  that simulates the traffic you *would* split.
- **How to learn it outside:**
  - Stand up Argo Rollouts on kind (M10) and run a canary against synthetic
    traffic with a metric gate — the mechanics are real even if users are not.
  - Learn feature-flag concepts (Unleash/OpenFeature) — flags are how traffic
    is actually steered in production.
  - Shadowing/mirroring traffic is a real technique: send copies of requests to
    a new version and compare behavior — you can simulate this with a script
    replaying recorded requests against two FinSight deployments.
  - Internships where you ship to real users are the only true training; use
    the simulator until then.

---

## Summary — what to say in an interview

For each concept, the honest structure is:

> "I know what X involves in a real organization. In FinSight I built the
> closest credible proxy: [artifact]. What I cannot claim from a solo project
> is [the real thing], and the way I'd get that experience is [plan]."

That is stronger than pretending a home cluster proves on-call maturity.