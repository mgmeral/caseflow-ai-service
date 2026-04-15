# CaseFlow AI Service

AI orchestration layer for CaseFlow customer support ticket workflows, built with Spring Boot 3.x and Spring AI.

> **Access model:** This service is **strictly service-to-service**. It is designed to be called only by the CaseFlow backend, never directly by browser frontends or end users. No authentication is implemented in P1 (Phase 1) — token-based service auth is planned for **P2**. **Do not expose this service on a public network or allow direct frontend access.**

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    CaseFlow AI Service                      │
│                                                             │
│  ┌──────────┐   ┌──────────────┐   ┌───────────────────┐  │
│  │   REST   │──▶│   Services   │──▶│   Spring AI       │  │
│  │   API    │   │  (AI/Ingest) │   │  (ChatClient /    │  │
│  │ Controllers│  │              │   │  VectorStore)     │  │
│  └──────────┘   └──────────────┘   └───────────────────┘  │
│                        │                     │              │
│                        ▼                     ▼              │
│               ┌──────────────┐     ┌──────────────────┐   │
│               │  Prompt      │     │  Ollama (LLM +   │   │
│               │  Builders    │     │  Embeddings)     │   │
│               └──────────────┘     └──────────────────┘   │
│                                              │              │
│                                    ┌──────────────────┐   │
│                                    │  Qdrant (Vector  │   │
│                                    │  Store)          │   │
│                                    └──────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

**Key components:**
- **`TicketAiController`** — 4 AI endpoints per ticket: summary, reply draft, similar cases, policy guidance
- **`IngestController`** — Ingest documents and tickets into the vector store
- **`HealthController`** — Service readiness and model status
- **Spring AI** — Abstracts Ollama chat + embeddings and Qdrant vector search
- **`ChunkingService`** — Splits documents into overlapping text chunks for indexing
- **Prompt Builders** — Construct structured prompts instructing LLM to output JSON

### RAG endpoints require prior ingestion

The `/similar-cases` and `/policy-guidance` endpoints perform vector search against **Qdrant**. They are fully functional, but will return empty results until you have ingested data via the `/api/ai/ingest/documents` or `/api/ai/ingest/tickets` endpoints. Ingest your policies and resolved tickets before using these endpoints.

---

## Local Startup (without Docker)

### Prerequisites
- Java 21+
- Maven 3.9+
- [Ollama](https://ollama.ai) running locally on port 11434
- [Qdrant](https://qdrant.tech) running locally (HTTP port 6333, gRPC port 6334)

### Start Ollama and pull models
```bash
ollama pull llama3.1
ollama pull nomic-embed-text
```

### Start Qdrant
```bash
docker run -p 6333:6333 -p 6334:6334 qdrant/qdrant
```
- Port **6333** — Qdrant HTTP REST API (used by the health checker)
- Port **6334** — Qdrant gRPC API (used by Spring AI VectorStore for vector operations)

### Build and run
```bash
cd caseflow-ai-service
mvn clean package -DskipTests
java -jar target/caseflow-ai-service-*.jar
```

The service will be available at `http://localhost:8080`.

---

## Docker Compose

### Start core services (production-like)
```bash
docker-compose up --build
```

This starts:
| Service | Port | Notes |
|---|---|---|
| `caseflow-ai-service` | 8080 | The Spring Boot app |
| `ollama` | 11434 | Ollama LLM server |
| `ollama-init` | — | One-shot: pulls llama3.1 + nomic-embed-text |
| `qdrant` | 6333 (HTTP) / 6334 (gRPC) | Vector DB |

### Start with Open WebUI (local dev only)

Open WebUI is a browser-based chat interface for Ollama. It is gated behind the `dev` Compose profile and **must not be started in any environment where the host is accessible to untrusted networks**, because it has no authentication by default.

```bash
docker-compose --profile dev up --build
```

This additionally starts:
| Service | Port | Notes |
|---|---|---|
| `open-webui` | 3000 | Chat UI for Ollama — **dev only** |

### Stop everything
```bash
docker-compose --profile dev down
```

---

## API Endpoints

### Swagger UI
```
http://localhost:8080/swagger-ui.html
```

### OpenAPI JSON
```
http://localhost:8080/api-docs
```

---

## Sample curl Requests

### 1. Ticket Summary
```bash
curl -X POST http://localhost:8080/api/ai/tickets/TKT-001/summary \
  -H "Content-Type: application/json" \
  -d '{
    "ticketStatus": "OPEN",
    "priority": "HIGH",
    "customerName": "Acme Corp",
    "summaryStyle": "STANDARD",
    "latestMessages": [
      {
        "direction": "INBOUND",
        "sender": "customer@acme.com",
        "subject": "Billing issue",
        "body": "I was charged twice for my subscription this month.",
        "createdAt": "2025-01-15T10:00:00Z"
      }
    ],
    "internalNotes": ["Customer has been with us for 3 years."],
    "tags": ["billing", "duplicate-charge"],
    "slaState": "WITHIN_SLA"
  }'
```

### 2. Reply Draft
```bash
curl -X POST http://localhost:8080/api/ai/tickets/TKT-001/reply-draft \
  -H "Content-Type: application/json" \
  -d '{
    "ticketStatus": "OPEN",
    "priority": "HIGH",
    "customerName": "Acme Corp",
    "tone": "PROFESSIONAL",
    "replyGoal": "RESOLUTION",
    "locale": "en",
    "latestMessages": [
      {
        "direction": "INBOUND",
        "sender": "customer@acme.com",
        "body": "I was charged twice for my subscription this month."
      }
    ],
    "policySnippets": ["Duplicate charges are refunded within 5 business days."],
    "constraints": ["Do not promise refunds without approval."]
  }'
```

### 3. Similar Cases
```bash
curl -X POST http://localhost:8080/api/ai/tickets/TKT-001/similar-cases \
  -H "Content-Type: application/json" \
  -d '{
    "queryText": "Customer was double billed for subscription",
    "topK": 5,
    "tags": ["billing"],
    "filters": {
      "status": "RESOLVED"
    }
  }'
```

### 4. Policy Guidance
```bash
curl -X POST http://localhost:8080/api/ai/tickets/TKT-001/policy-guidance \
  -H "Content-Type: application/json" \
  -d '{
    "query": "What is the refund policy for duplicate charges?",
    "ticketStatus": "OPEN",
    "priority": "HIGH",
    "customerName": "Acme Corp",
    "topK": 5,
    "tags": ["billing"]
  }'
```

### 5. Ingest Document
```bash
curl -X POST http://localhost:8080/api/ai/ingest/documents \
  -H "Content-Type: application/json" \
  -d '{
    "sourceId": "POLICY-001",
    "sourceType": "POLICY",
    "title": "Refund Policy",
    "text": "Customers are eligible for a full refund within 30 days of purchase. Duplicate charges are automatically refunded within 5 business days upon verification.",
    "metadata": {
      "version": "2.1",
      "department": "billing"
    }
  }'
```

### 6. Ingest Ticket
```bash
curl -X POST http://localhost:8080/api/ai/ingest/tickets \
  -H "Content-Type: application/json" \
  -d '{
    "sourceId": "TKT-2024-5000",
    "customerName": "Widget Co",
    "subject": "Double charge on invoice #4521",
    "body": "We were billed twice for the Enterprise plan in December.",
    "resolutionSummary": "Duplicate charge confirmed and refunded on Jan 3.",
    "tags": ["billing", "duplicate-charge"],
    "status": "RESOLVED",
    "metadata": {
      "resolvedBy": "agent_42"
    }
  }'
```

### 7. Health / Models
```bash
# Readiness check
curl http://localhost:8080/api/ai/health/ready

# Model status
curl http://localhost:8080/api/ai/health/models
```

---

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `SERVER_PORT` | `8080` | HTTP port |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama base URL |
| `OLLAMA_CHAT_MODEL` | `llama3.1` | Model used for chat/generation |
| `OLLAMA_EMBED_MODEL` | `nomic-embed-text` | Model used for embeddings |
| `QDRANT_HOST` | `localhost` | Qdrant hostname |
| `QDRANT_PORT` | `6334` | Qdrant **gRPC** port (used by Spring AI VectorStore) |
| `QDRANT_HTTP_PORT` | `6333` | Qdrant **HTTP** port (used by health checker) |
| `QDRANT_COLLECTION_DOCUMENTS` | `caseflow-documents` | Qdrant collection name |
| `SPRING_PROFILES_ACTIVE` | `default` | Active Spring profile |

---

## Ollama Model Setup

Before using the service, pull the required models:

```bash
# Chat / generation model
ollama pull llama3.1

# Embedding model (for vector search)
ollama pull nomic-embed-text
```

Alternative models that work well:
- Chat: `mistral`, `llama3.2`, `qwen2.5`
- Embeddings: `mxbai-embed-large`, `all-minilm`

Update `OLLAMA_CHAT_MODEL` and `OLLAMA_EMBED_MODEL` environment variables accordingly.

---

## Security and Access Model

> ### ⚠️ Service-to-service only — no direct frontend access
>
> This service is an **internal AI backend**. It is intended to be called exclusively by the **CaseFlow backend service** over a private/on-prem network. It must **never** be called directly from browser frontends, mobile apps, or any untrusted client.
>
> **Authentication is intentionally omitted in P1.** All endpoints are open to simplify local development and integration testing. Token-based service authentication (e.g. shared secret header, mTLS, or JWT service account) is planned for **Phase 2 (P2)**.
>
> **Do not:**
> - Expose port 8080 on a public interface without a network boundary
> - Allow end users or browser frontends to call this service directly
> - Deploy without a surrounding API gateway or network policy in production
>
> **Planned P2 security additions:** service token validation, role-based endpoint access, audit logging.

---

## Actuator Endpoints

| Endpoint | Description |
|---|---|
| `GET /actuator/health` | Spring Boot health check |
| `GET /actuator/info` | Application info |