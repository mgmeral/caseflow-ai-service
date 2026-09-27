// Benchmarks summary + reply-draft through the running caseflow-ai-service, so the real
// prompts, response_format and JSON parsing are measured — not a synthetic prompt.
//
//   node benchmark/run-llm.mjs --label qwen3-4b@llama-server [--service http://localhost:8081]
//
// Writes benchmark/results/<label>.json (every output, for manual quality review) and prints
// p50/p95 latency, JSON validity and answer-language accuracy.
import { readFile, writeFile, mkdir } from 'node:fs/promises';

const args = Object.fromEntries(process.argv.slice(2).reduce((acc, a, i, all) =>
  a.startsWith('--') ? [...acc, [a.slice(2), all[i + 1]]] : acc, []));
const label = args.label ?? 'unnamed';
const service = args.service ?? 'http://localhost:8081';
const dir = new URL('.', import.meta.url);

const tickets = JSON.parse(await readFile(new URL('golden-tickets.json', dir), 'utf8'));

const TURKISH = /[ğışĞŞİ]|\b(ve|bir|için|ile)\b/iu;

function looksLike(locale, text) {
  if (!text) return false;
  return locale === 'tr' ? TURKISH.test(text) : !/[ğışĞŞİ]/u.test(text);
}

function messages(t) {
  return t.messages.map((m, i) => ({
    direction: m.direction, from: m.direction === 'inbound' ? 'c***@example.com' : 'agent',
    preview: m.preview, sentAt: `2026-09-2${i}T09:00:00Z`,
  }));
}

async function call(path, body) {
  const start = performance.now();
  const res = await fetch(`${service}${path}`, {
    method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Source': 'benchmark' },
    body: JSON.stringify(body),
  });
  const ms = performance.now() - start;
  const json = res.ok ? await res.json() : { httpStatus: res.status, error: await res.text() };
  const warnings = json.warnings ?? [];
  return {
    ms: Math.round(ms), httpOk: res.ok,
    modelOk: res.ok && !warnings.some(w => w.startsWith('AI model unavailable')),
    jsonOk: res.ok && !warnings.some(w => w.startsWith('MODEL_OUTPUT_NOT_JSON')),
    body: json,
  };
}

const summaryBody = t => ({
  customerName: t.customerName, ticketStatus: t.ticketStatus, priority: t.priority, slaState: 'OK',
  tags: t.tags, latestMessages: messages(t), internalNotes: t.notes, locale: t.locale, summaryStyle: 'STANDARD',
});
const draftBody = t => ({
  customerName: t.customerName, locale: t.locale, tone: 'professional', ticketStatus: t.ticketStatus,
  priority: t.priority, tags: t.tags, latestMessages: messages(t), internalNotes: t.notes,
  policySnippets: [], constraints: [], replyGoal: 'RESOLUTION', selectedTemplateCode: 'CUSTOMER_REPLY',
});

// Warm-up (model load, CUDA graphs, prompt cache) is not measured.
await call(`/api/ai/tickets/warmup/summary`, summaryBody(tickets[0]));

const results = [];
for (const t of tickets) {
  const summary = await call(`/api/ai/tickets/${t.id}/summary`, summaryBody(t));
  const draft = await call(`/api/ai/tickets/${t.id}/reply-draft`, draftBody(t));
  results.push({
    id: t.id, locale: t.locale,
    summary: { ...summary, langOk: looksLike(t.locale, summary.body.summary) },
    draft: { ...draft, langOk: looksLike(t.locale, draft.body.suggestedBody) },
  });
  process.stdout.write(`${t.id} summary ${summary.ms}ms draft ${draft.ms}ms\n`);
}

const pct = (xs, p) => { const s = [...xs].sort((a, b) => a - b); return s[Math.min(s.length - 1, Math.ceil(p / 100 * s.length) - 1)]; };
const rate = (xs, f) => `${Math.round(100 * xs.filter(f).length / xs.length)}%`;
const report = {};
for (const kind of ['summary', 'draft']) {
  const rs = results.map(r => r[kind]);
  report[kind] = {
    p50ms: pct(rs.map(r => r.ms), 50), p95ms: pct(rs.map(r => r.ms), 95),
    modelOk: rate(rs, r => r.modelOk), jsonOk: rate(rs, r => r.jsonOk), langOk: rate(rs, r => r.langOk),
  };
}
console.log(`\n${label}`);
console.table(report);

await mkdir(new URL('results/', dir), { recursive: true });
await writeFile(new URL(`results/${label.replace(/[^\w.@-]/g, '_')}.json`, dir),
  JSON.stringify({ label, service, report, results }, null, 2));
