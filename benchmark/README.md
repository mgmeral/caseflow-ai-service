# LLM / embedding benchmark

Used to choose the runtime and models (ADR-0005 in `caseflow-central-brain`). All data is
synthetic. Never put real ticket data in these files, and never point the scripts at a hosted
provider with real data.

## What is measured

- `run-llm.mjs` sends 20 synthetic tickets (10 Turkish, 10 English, `golden-tickets.json`) to
  the **running `caseflow-ai-service`** (`/summary` and `/reply-draft`). This measures the real
  prompts, `response_format` and JSON parsing. It reports p50/p95 latency, the share of answers
  that parse as JSON, and the share written in the ticket's language. Every output is saved to
  `results/<label>.json` for manual quality review.
- `run-embed.mjs` embeds 16 resolved tickets and policies and 16 queries (`retrieval-set.json`),
  12 of which are cross-lingual (Turkish query → English document and vice versa). It reports
  hit@1, hit@3 and MRR. It calls the embedding server directly, with no Qdrant involved.

## How to run

```bash
# 1. Models: download the GGUF files into ./models (see the main README, "Models")
# 2. Runtime + dependencies
docker compose up -d llama-chat llama-embed postgres qdrant
# 3. The service, pointed at them
mvn -q -DskipTests package
AI_CHAT_BASE_URL=http://localhost:8090 AI_EMBED_BASE_URL=http://localhost:8091 \
  java -jar target/caseflow-ai-service-*.jar
# 4. Benchmarks
node benchmark/run-llm.mjs --label <model>@llama-server
node benchmark/run-embed.mjs --label <embed-model>
# nomic-embed-text needs task prefixes:
node benchmark/run-embed.mjs --label nomic --query-prefix "search_query: " --doc-prefix "search_document: "
```

To try another chat model, recreate `llama-chat` with `CHAT_MODEL_FILE=<file>.gguf AI_CHAT_MODEL=<alias>`
(and `CHAT_EXTRA_ARGS="--reasoning-budget 0"` for models with a thinking mode):

```bash
CHAT_MODEL_FILE=Qwen3.5-4B-Q4_K_M.gguf AI_CHAT_MODEL=qwen3.5-4b CHAT_EXTRA_ARGS="--reasoning-budget 0" \
  docker compose up -d --force-recreate llama-chat
```

The language check is a heuristic: Turkish letters or common Turkish words for `tr`, and none of
the Turkish-only letters for `en`. Read the saved outputs for real quality judgement.
