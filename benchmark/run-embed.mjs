// Retrieval quality of an embedding model on a small TR/EN support corpus, including
// cross-lingual pairs (Turkish query -> English resolved ticket and vice versa).
//
//   node benchmark/run-embed.mjs --label bge-m3 [--url http://localhost:8091]
//        [--query-prefix "search_query: " --doc-prefix "search_document: "]   # nomic needs these
//
// Prints hit@1, hit@3 and MRR overall and per query kind.
import { readFile } from 'node:fs/promises';

const args = Object.fromEntries(process.argv.slice(2).reduce((acc, a, i, all) =>
  a.startsWith('--') ? [...acc, [a.slice(2), all[i + 1]]] : acc, []));
const url = args.url ?? 'http://localhost:8091';
const qp = args['query-prefix'] ?? '';
const dp = args['doc-prefix'] ?? '';

const { documents, queries } = JSON.parse(
  await readFile(new URL('retrieval-set.json', import.meta.url), 'utf8'));

async function embed(texts) {
  const res = await fetch(`${url}/v1/embeddings`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ model: 'x', input: texts }),
  });
  if (!res.ok) throw new Error(`${res.status} ${await res.text()}`);
  return (await res.json()).data.sort((a, b) => a.index - b.index).map(d => d.embedding);
}

const cos = (a, b) => {
  let dot = 0, na = 0, nb = 0;
  for (let i = 0; i < a.length; i++) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i]; }
  return dot / Math.sqrt(na * nb);
};

const docVecs = await embed(documents.map(d => dp + d.text));
const queryVecs = await embed(queries.map(q => qp + q.q));

const rows = queries.map((q, i) => {
  const ranked = documents.map((d, j) => ({ id: d.id, s: cos(queryVecs[i], docVecs[j]) }))
    .sort((a, b) => b.s - a.s);
  const rank = ranked.findIndex(r => r.id === q.expected) + 1;
  return { kind: q.kind, rank };
});

const summarize = rs => ({
  n: rs.length,
  hit1: `${Math.round(100 * rs.filter(r => r.rank === 1).length / rs.length)}%`,
  hit3: `${Math.round(100 * rs.filter(r => r.rank <= 3).length / rs.length)}%`,
  mrr: (rs.reduce((s, r) => s + 1 / r.rank, 0) / rs.length).toFixed(3),
});

const cross = rows.filter(r => r.kind === 'tr->en' || r.kind === 'en->tr');
const same = rows.filter(r => r.kind === 'tr->tr' || r.kind === 'en->en');
console.log(`\n${args.label ?? url} (dim ${docVecs[0].length})`);
console.table({ all: summarize(rows), crossLingual: summarize(cross), sameLanguage: summarize(same) });
