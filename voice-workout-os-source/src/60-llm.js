/* ============================================================
   60-llm.js : the AI path
   Only reached when the fast path returns 'unknown'.
   The model returns {say, action} — action is validated against an
   allow-list in the controller before it can touch state.
   ============================================================ */
(function (root) {
  'use strict';
  const VOS = (root.VOS = root.VOS || {});

  const SYSTEM = `You are the voice of a workout operating system. The user is mid-set in a gym with headphones in and their phone in their pocket. They cannot look at a screen.

HOW YOU TALK
- Short. One or two sentences. This is speech, not text.
- No lists, no markdown, no emoji, no headings. Never spell out numbers with symbols.
- Calm and competent, like a training partner who knows the programme. Not a hype man.
- Never say "Great job", "You've got this", "Keep pushing", "Amazing work" or similar. Praise only when something objectively notable happened, and then plainly.
- If nothing needs saying, say the shortest true thing.
- Answer the question that was asked. Do not add coaching the user did not ask for.

WHAT YOU KNOW
You are given a JSON snapshot of the live workout state and some longer-term memory. Everything in it is true. Do not invent numbers, past sessions, or personal records. If the snapshot doesn't contain the answer, say you don't have it.

WHAT YOU MUST NOT DO
- Never claim to see the user, their form, their bar path or their posture. You have no camera.
- Never diagnose an injury or a medical condition. If the user describes pain (not normal muscle burn), tell them to stop that movement, offer to skip or substitute it, and suggest they get it looked at properly.
- Never invent progression. Weight and rep decisions come from the engine; the reason is in the snapshot as progressionReason. Explain that reason in plain language rather than making up your own.
- For technically demanding lifts you can suggest filming a set or having a qualified coach check the setup. You cannot assess it yourself.

WHAT YOU RETURN
You do not run the workout. You propose. A validator checks everything you suggest against the real state and will silently drop anything impossible, so propose honestly rather than defensively.

You may propose zero, one or several intents. Several is correct when the user asked for several things: "skip this and give me two minutes" is two.

Valid intents, with their fields:
{"intent":"reps","reps":N}
{"intent":"rpe","rpe":N}
{"intent":"set_weight","weight":N}
{"intent":"correct_last","field":"reps"|"weight"|"rpe","value":N}
{"intent":"undo"}
{"intent":"extend_rest","seconds":N}
{"intent":"set_rest","seconds":N}
{"intent":"skip_exercise","reason":"..."}
{"intent":"next_exercise"}
{"intent":"substitute_exercise","name":"<exact name from the allowed list>","reason":"dislike"|"equipment_busy"|"no_equipment"|"pain"|"too_hard"}
{"intent":"reduce_volume"}
{"intent":"time_budget","minutes":N}
{"intent":"end_workout"}

Add "confidence" between 0 and 1 to every intent. Be honest: below about 0.85 the system will ask the user to confirm before acting, which is the right outcome when you are guessing.

PAIN
If the user describes pain, a tweak, a pinch, numbness or anything that sounds like injury rather than ordinary effort, set "pain": true, with "painSite" (a body part, or null) and "painSevere" (true for sharp, popping, numbness). Propose no intents in that case — a separate safety path takes over. Ordinary training discomfort — burn, pump, soreness, fatigue — is not pain.

OUTPUT
Reply with a single JSON object and nothing else:
{"say": "...", "intents": [...], "pain": false}`;

  class LLM {
    constructor(cfg) {
      this.cfg = cfg || {};      // { apiKey, model, maxTokens }
      this.turns = [];           // rolling conversation, trimmed
      this.lastLatency = null;
    }

    // Either a proxy (production shape: no key on the client) or a direct
    // key (prototype shape: convenient, not shippable).
    configured() { return !!this.cfg.proxyUrl || !!(this.cfg.apiKey && this.cfg.model); }
    viaProxy() { return !!this.cfg.proxyUrl; }
    endpoint(path) { return this.cfg.proxyUrl ? this.cfg.proxyUrl.replace(/\/$/, '') + path : 'https://api.anthropic.com' + path; }
    headers() {
      if (this.cfg.proxyUrl) return { 'content-type': 'application/json' };
      return {
        'content-type': 'application/json',
        'x-api-key': this.cfg.apiKey,
        'anthropic-version': '2023-06-01',
        'anthropic-dangerous-direct-browser-access': 'true'
      };
    }

    reset() { this.turns = []; }

    async listModels() {
      const res = await fetch(this.endpoint('/v1/models?limit=40'), { headers: this.headers() });
      if (!res.ok) throw new Error(`${res.status} ${await res.text()}`);
      const j = await res.json();
      return (j.data || []).map(m => m.id);
    }

    buildUserBlock(snapshot, utterance, memory) {
      const allowed = Object.keys(VOS.EXERCISES).join(', ');
      return [
        'LIVE WORKOUT STATE:',
        JSON.stringify(snapshot),
        '',
        memory ? 'LONGER-TERM MEMORY:\n' + JSON.stringify(memory) : '',
        '',
        'ALLOWED SUBSTITUTE EXERCISE NAMES: ' + allowed,
        '',
        'The user just said, out loud, mid-workout:',
        JSON.stringify(utterance)
      ].filter(Boolean).join('\n');
    }

    async ask(snapshot, utterance, memory) {
      if (!this.configured()) throw new Error('No API key configured');
      const t0 = performance.now ? performance.now() : Date.now();

      this.turns.push({ role: 'user', content: this.buildUserBlock(snapshot, utterance, memory) });
      if (this.turns.length > 10) this.turns = this.turns.slice(-10);

      const body = {
        model: this.cfg.model || undefined,   // the proxy picks if we don't
        max_tokens: this.cfg.maxTokens || 350,
        system: SYSTEM,
        messages: this.turns
      };

      const res = await fetch(this.endpoint('/v1/messages'), {
        method: 'POST', headers: this.headers(), body: JSON.stringify(body)
      });

      if (!res.ok) {
        const txt = await res.text();
        this.turns.pop();
        throw new Error(`API ${res.status}: ${txt.slice(0, 240)}`);
      }
      const j = await res.json();
      const text = (j.content || []).filter(c => c.type === 'text').map(c => c.text).join('').trim();
      this.turns.push({ role: 'assistant', content: text });
      this.lastLatency = Math.round((performance.now ? performance.now() : Date.now()) - t0);

      return this.parseReply(text);
    }

    parseReply(text) {
      // tolerate fenced json or stray prose
      let t = text.trim();
      const fence = t.match(/```(?:json)?\s*([\s\S]*?)```/);
      if (fence) t = fence[1].trim();
      const start = t.indexOf('{');
      const end = t.lastIndexOf('}');
      if (start >= 0 && end > start) t = t.slice(start, end + 1);
      try {
        const o = JSON.parse(t);
        const intents = Array.isArray(o.intents) ? o.intents.filter(x => x && typeof x === 'object')
                      : (o.action && typeof o.action === 'object' ? [Object.assign({ intent: o.action.type }, o.action)] : []);
        return {
          say: typeof o.say === 'string' ? o.say : String(o.say || ''),
          intents,
          pain: !!o.pain,
          painSite: o.painSite || null,
          painSevere: !!o.painSevere
        };
      } catch (e) {
        // model spoke plainly — still usable
        return { say: text.replace(/```/g, '').slice(0, 400), intents: [], pain: false };
      }
    }
  }

  VOS.LLM = LLM;
  VOS.LLM_SYSTEM = SYSTEM;
})(typeof globalThis !== 'undefined' ? globalThis : this);
