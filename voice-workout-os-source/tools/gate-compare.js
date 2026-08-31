#!/usr/bin/env node
/* ============================================================
   Replay a labelled field log through every addressee strategy
   and print false activations, missed commands and wrong actions.

     node tools/gate-compare.js session-*.json

   Label each user row in the exported CSV/JSON first:
     coach          - said to the agent
     third_party    - said to a person
     self_talk      - muttering
     misrecognition - the recogniser invented it
   Rows with no label are skipped and counted as unlabelled.
   ============================================================ */
const fs = require('fs');
const { load } = require('../tests/_harness');
const VOS = load();

const files = process.argv.slice(2);
if (!files.length) { console.error('usage: node tools/gate-compare.js <session.json ...>'); process.exit(1); }

const rows = [];
files.forEach(f => {
  const j = JSON.parse(fs.readFileSync(f, 'utf8'));
  (j.entries || []).filter(e => e.kind === 'user').forEach(e => rows.push(e));
});

const labelled = rows.filter(r => r.label);
if (!labelled.length) {
  console.log(`${rows.length} utterances, none labelled yet.`);
  console.log('Fill the label column with coach / third_party / self_talk / misrecognition and run this again.');
  process.exit(0);
}

const STRATEGIES = ['open', 'window', 'wake', 'hybrid'];
const results = {};

STRATEGIES.forEach(strategy => {
  const gate = new VOS.Gate({ strategy });
  let falseAccept = 0, falseReject = 0, correct = 0;
  const examples = { falseAccept: [], falseReject: [] };

  labelled.forEach(r => {
    // reconstruct what the dialogue looked like at that moment
    const dialogue = new VOS.Dialogue();
    if (r.awaiting) dialogue.expect(r.awaiting, { question: 'replayed' });

    const d = gate.check({ text: r.text, dialogue, session: { phase: r.phase } });
    const shouldAccept = r.label === 'coach';
    if (d.accept && !shouldAccept) { falseAccept++; if (examples.falseAccept.length < 6) examples.falseAccept.push(r.text); }
    else if (!d.accept && shouldAccept) { falseReject++; if (examples.falseReject.length < 6) examples.falseReject.push(r.text); }
    else correct++;
  });

  const n = labelled.length;
  results[strategy] = {
    correct, falseAccept, falseReject,
    accuracy: +(100 * correct / n).toFixed(1),
    // a false accept is recoverable with undo; a false reject just annoys.
    // weight accordingly when choosing.
    weightedCost: +(falseAccept * 1 + falseReject * 1.5).toFixed(1),
    examples
  };
});

console.log(`\n${labelled.length} labelled utterances from ${files.length} session(s)`);
const dist = {};
labelled.forEach(r => { dist[r.label] = (dist[r.label] || 0) + 1; });
console.log('labels:', JSON.stringify(dist));
console.log(`unlabelled and skipped: ${rows.length - labelled.length}\n`);

const pad = (s, n) => String(s).padEnd(n);
console.log(pad('strategy', 10), pad('accuracy', 10), pad('false accepts', 15), pad('false rejects', 15), 'weighted cost');
STRATEGIES.forEach(s => {
  const r = results[s];
  console.log(pad(s, 10), pad(r.accuracy + '%', 10), pad(r.falseAccept, 15), pad(r.falseReject, 15), r.weightedCost);
});

const best = STRATEGIES.slice().sort((a, b) => results[a].weightedCost - results[b].weightedCost)[0];
console.log(`\nlowest cost: ${best}`);
console.log('\nwhat it got wrong:');
STRATEGIES.forEach(s => {
  const r = results[s];
  if (r.examples.falseAccept.length) console.log(`  ${s} wrongly accepted: ${r.examples.falseAccept.map(x => JSON.stringify(x)).join(', ')}`);
  if (r.examples.falseReject.length) console.log(`  ${s} wrongly ignored:  ${r.examples.falseReject.map(x => JSON.stringify(x)).join(', ')}`);
});
console.log('');
