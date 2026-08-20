#!/usr/bin/env python3
"""Voice Evidence scorecard.

Reads human labels (evidence/labels/*.jsonl) and per-provider kernel results
(evidence/runs/<provider>.jsonl), and reports the metrics in TECHNICAL_SPEC
Observability plus the end-to-end numbers the product hypothesis needs.

It computes. It does not decide. There is deliberately no "winner" logic:
providers are reported side by side (Part 12).

If no results exist it says so and exits 1. It never invents a number.
"""
from __future__ import annotations
import json, sys, glob, os
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LABELS = os.path.join(ROOT, "evidence", "labels")
RUNS = os.path.join(ROOT, "evidence", "runs")


def read_jsonl(path):
    out = []
    with open(path, encoding="utf-8") as fh:
        for n, line in enumerate(fh, 1):
            line = line.strip()
            if not line or line.startswith("//"):
                continue
            try:
                out.append(json.loads(line))
            except json.JSONDecodeError as e:
                print(f"WARN: {os.path.basename(path)}:{n} bad JSON: {e}", file=sys.stderr)
    return out


def wer(ref: str, hyp: str) -> float:
    """Standard Levenshtein word error rate."""
    r, h = ref.lower().split(), hyp.lower().split()
    if not r:
        return 0.0 if not h else 1.0
    d = [[0] * (len(h) + 1) for _ in range(len(r) + 1)]
    for i in range(len(r) + 1):
        d[i][0] = i
    for j in range(len(h) + 1):
        d[0][j] = j
    for i in range(1, len(r) + 1):
        for j in range(1, len(h) + 1):
            d[i][j] = min(d[i - 1][j] + 1, d[i][j - 1] + 1,
                          d[i - 1][j - 1] + (r[i - 1] != h[j - 1]))
    return d[len(r)][len(h)] / len(r)


def pct(num, den):
    return None if not den else round(100.0 * num / den, 1)


def fmt(v, suffix="%"):
    return "n/a" if v is None else f"{v}{suffix}"


def score(labels, results):
    by_id = {l["utteranceId"]: l for l in labels}
    m = defaultdict(int)
    wers = []
    for r in results:
        lab = by_id.get(r.get("utteranceId"))
        if not lab:
            m["orphan_result"] += 1
            continue
        m["scored"] += 1
        exp, act = lab["expected"], r.get("actual", {})

        gt = lab.get("groundTruthTranscript")
        if gt and r.get("transcript") is not None:
            wers.append(wer(gt, r["transcript"]))
            m["wer_n"] += 1

        exp_i, act_i = set(exp["intent"]), set(act.get("intent", []))
        if exp_i == act_i:
            m["intent_exact"] += 1
        if exp_i & act_i:
            m["intent_overlap"] += 1

        if exp.get("weightKg") is not None:
            m["weight_n"] += 1
            if act.get("weightKg") == exp["weightKg"]:
                m["weight_ok"] += 1
        if exp.get("reps") is not None:
            m["reps_n"] += 1
            if act.get("reps") == exp["reps"]:
                m["reps_ok"] += 1
        if exp.get("exercise"):
            m["ex_n"] += 1
            if act.get("exercise") == exp["exercise"]:
                m["ex_ok"] += 1

        exp_mut, act_mut = exp["mutationExpected"], bool(act.get("mutated"))
        if act_mut and not exp_mut:
            m["false_mutation"] += 1
        if exp_mut and not act_mut:
            m["missed_event"] += 1
        if act.get("clarified"):
            m["clarified"] += 1

        sev = exp.get("safetySeverity")
        if sev in ("critical", "high"):
            m[f"safety_{sev}_n"] += 1
            if act.get("safetySeverity") == sev:
                m[f"safety_{sev}_ok"] += 1
        elif sev is None and act.get("safetySeverity") in ("critical", "high"):
            m["safety_false_positive"] += 1

        # End-to-end correct: right intent set, right numbers, right mutation.
        ok = (exp_i == act_i and exp_mut == act_mut)
        if exp.get("weightKg") is not None:
            ok = ok and act.get("weightKg") == exp["weightKg"]
        if exp.get("reps") is not None:
            ok = ok and act.get("reps") == exp["reps"]
        if ok:
            m["e2e_ok"] += 1
    return m, wers


def report(provider, labels, results):
    m, wers = score(labels, results)
    n = m["scored"]
    print(f"\n=== {provider} ===")
    if not n:
        print("  no scored utterances")
        return
    print(f"  utterances scored          {n}")
    print(f"  word error rate            {round(sum(wers)/len(wers),3) if wers else 'n/a'}"
          f"  (n={m['wer_n']})")
    print(f"  intent exact match         {fmt(pct(m['intent_exact'], n))}")
    print(f"  intent partial overlap     {fmt(pct(m['intent_overlap'], n))}")
    print(f"  weight accuracy            {fmt(pct(m['weight_ok'], m['weight_n']))}  (n={m['weight_n']})")
    print(f"  rep accuracy               {fmt(pct(m['reps_ok'], m['reps_n']))}  (n={m['reps_n']})")
    print(f"  exercise-name accuracy     {fmt(pct(m['ex_ok'], m['ex_n']))}  (n={m['ex_n']})")
    print(f"  -- headline --")
    print(f"  END-TO-END CORRECT         {fmt(pct(m['e2e_ok'], n))}")
    print(f"  FALSE WORKOUT MUTATION     {fmt(pct(m['false_mutation'], n))}")
    print(f"  MISSED WORKOUT EVENT       {fmt(pct(m['missed_event'], n))}")
    print(f"  CLARIFICATION RATE         {fmt(pct(m['clarified'], n))}")
    print(f"  SAFETY critical recall     {fmt(pct(m['safety_critical_ok'], m['safety_critical_n']))}"
          f"  (n={m['safety_critical_n']})")
    print(f"  SAFETY high recall         {fmt(pct(m['safety_high_ok'], m['safety_high_n']))}"
          f"  (n={m['safety_high_n']})")
    print(f"  SAFETY false positives     {m['safety_false_positive']}")


def main():
    labels = []
    for p in sorted(glob.glob(os.path.join(LABELS, "*.jsonl"))):
        if os.path.basename(p).startswith("EXAMPLE"):
            continue
        labels += read_jsonl(p)
    runs = sorted(glob.glob(os.path.join(RUNS, "*.jsonl")))

    print(f"labels: {len(labels)}   provider runs: {len(runs)}")
    if not labels or not runs:
        print("\nNO MEASURED EVIDENCE YET.")
        print("Collect real utterances and run the evidence runner first.")
        print("See docs/living-spec/VOICE_EVIDENCE_MVP.md.")
        print("Do not report an accuracy number until this command prints one.")
        return 1
    for p in runs:
        report(os.path.basename(p)[:-6], labels, read_jsonl(p))
    print("\nNote: providers are reported side by side. Choosing one is a human decision.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
