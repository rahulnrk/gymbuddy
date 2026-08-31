/* ============================================================
   30-intent.js : FAST PATH
   Deterministic intent parsing. No network, no LLM, target <5ms.
   Anything this returns as {intent:'unknown'} escalates to the AI path.
   ============================================================ */
(function (root) {
  'use strict';
  const VOS = (root.VOS = root.VOS || {});

  const NUMWORDS = {
    zero:0, one:1, two:2, three:3, four:4, five:5, six:6, seven:7, eight:8, nine:9, ten:10,
    eleven:11, twelve:12, thirteen:13, fourteen:14, fifteen:15, sixteen:16, seventeen:17,
    eighteen:18, nineteen:19, twenty:20, thirty:30, forty:40, fifty:50, sixty:60, seventy:70,
    eighty:80, ninety:90, hundred:100,
    // common recognizer mishears
    ate:8, won:1, too:2, to:2, for:4, free:3, tree:3, fore:4, sex:6, nain:9
  };

  function wordsToNumber(str) {
    if (!str) return null;
    const t = str.trim().toLowerCase().replace(/[^a-z0-9.\- ]/g, '');
    const direct = t.match(/^-?\d+(\.\d+)?$/);
    if (direct) return parseFloat(t);
    const parts = t.split(/[\s-]+/).filter(Boolean);
    let total = 0, cur = 0, saw = false;
    for (const p of parts) {
      if (/^\d+(\.\d+)?$/.test(p)) { cur += parseFloat(p); saw = true; continue; }
      const v = NUMWORDS[p];
      if (v == null) continue;
      saw = true;
      if (v === 100) cur = (cur || 1) * 100;
      else if (v >= 20 && cur && cur < 10) cur = cur + v; // "twenty two" handled below anyway
      else cur += v;
    }
    total += cur;
    return saw ? total : null;
  }

  // pull the first number (digit or word) out of a phrase
  function firstNumber(text) {
    const d = text.match(/-?\d+(?:[.,]\d+)?/);
    if (d) return parseFloat(d[0].replace(',', '.'));
    const words = text.split(/[\s-]+/);
    for (let i = 0; i < words.length; i++) {
      const w = words[i].replace(/[^a-z]/g, '');
      if (NUMWORDS[w] != null) {
        // handle "twenty two"
        const nxt = (words[i+1] || '').replace(/[^a-z]/g, '');
        if (NUMWORDS[w] >= 20 && NUMWORDS[w] % 10 === 0 && NUMWORDS[nxt] != null && NUMWORDS[nxt] < 10) {
          return NUMWORDS[w] + NUMWORDS[nxt];
        }
        return NUMWORDS[w];
      }
    }
    return null;
  }

  // duration phrases -> seconds
  function parseDuration(text) {
    let m = text.match(/(\d+(?:\.\d+)?)\s*(?:min(?:ute)?s?|m\b)/);
    if (m) return Math.round(parseFloat(m[1]) * 60);
    m = text.match(/(\d+)\s*(?:sec(?:ond)?s?|s\b)/);
    if (m) return parseInt(m[1], 10);
    m = text.match(/\b(one|two|three|four|five)\s+min(?:ute)?s?\b/);
    if (m) return NUMWORDS[m[1]] * 60;
    m = text.match(/\b(thirty|forty|fifty|sixty|ninety)\s+sec(?:ond)?s?\b/);
    if (m) return NUMWORDS[m[1]] || 60;
    if (/\bminute and a half|ninety seconds\b/.test(text)) return 90;
    if (/\bhalf a minute\b/.test(text)) return 30;
    return null;
  }

  // subjective effort -> RPE
  const RPE_PHRASES = [
    [/\b(?:very|super|really|dead)\s*easy\b|\bnothing\b|\btoo light\b|\bpiece of cake\b/, 5],
    [/\beasy\b|\blight\b|\bcomfortable\b|\bsmooth\b|\bno problem\b/, 6],
    [/\bfine\b|\bokay\b|\bok\b|\bgood\b|\balright\b|\bdecent\b|\bmoderate\b|\bnormal\b/, 7],
    [/\bsolid\b|\bchallenging\b|\bworked\b|\bfelt it\b|\bbit hard\b|\ba little hard\b/, 8],
    [/\bhard\b|\bheavy\b|\btough\b|\bbrutal\b|\brough\b|\bgrind\b|\bstruggl/, 9],
    [/\bmax(?:imum)?\b|\ball i had\b|\bfailure\b|\bfailed\b|\bcouldn'?t\b|\bnothing left\b|\bdied\b|\beverything\b/, 10]
  ];

  const CANCEL = /\b(?:no|nope|cancel|wait|scratch that|never ?mind|undo)\b/;

  /**
   * parse(text, ctx) -> { intent, ...payload, confidence }
   * ctx: { awaiting, phase, hasCurrentExercise }
   */
  function parse(rawText, ctx) {
    ctx = ctx || {};
    const text = (rawText || '').toLowerCase().trim().replace(/[.!?,]+$/g, '');
    if (!text) return { intent:'unknown', text: rawText };
    const R = (intent, extra) => Object.assign({ intent, text: rawText, fast: true }, extra || {});

    /* ---- undo / correction ---- */
    if (/\b(?:undo|scratch that|delete that|wrong|i meant|correction)\b/.test(text) && !/why/.test(text))
      return R('undo');

    /* ---- music: requires an explicit music token, so bare "next" and
       "skip" stay reserved for the workout. This is the disambiguation
       design under test — every hit is also counted as demand data. ---- */
    if (/\b(?:song|track|tune|music|playlist|album)\b/.test(text) || /\bvolume\b/.test(text)) {
      if (/\b(?:next|skip|change|another)\b/.test(text) && !/\bvolume\b/.test(text)) return R('music_next');
      if (/\b(?:previous|last|back|restart|rewind|replay)\b/.test(text)) return R('music_prev');
      if (/\bshuffle\b/.test(text)) return R('music_shuffle');
      if (/\b(?:pause|stop|mute|kill|off)\b/.test(text)) return R('music_pause');
      if (/\b(?:play|resume|start|unpause|back on)\b/.test(text)) return R('music_resume');
      if (/\b(?:up|louder|higher|crank)\b/.test(text)) return R('music_volume', { dir: 1 });
      if (/\b(?:down|quieter|lower|softer)\b/.test(text)) return R('music_volume', { dir: -1 });
    }
    if (/\b(?:turn it (?:up|down))\b/.test(text) && !/\byou\b/.test(text))
      return R('music_volume', { dir: /up/.test(text) ? 1 : -1 });
    if (/\byou'?re too (?:loud|quiet)\b|\bspeak up\b|\bcan'?t hear you\b|\bsay it louder\b/.test(text))
      return R('agent_volume', { dir: /too loud/.test(text) ? -1 : 1 });

    /* ---- explicit RPE forms, valid in any phase ---- */
    const N = '\\d{1,2}(?:\\.5)?|one|two|three|four|five|six|seven|eight|nine|ten|a couple';
    const num = v => (v === 'a couple' ? 2 : (parseFloat(v) || NUMWORDS[v] || 0));
    let m = text.match(new RegExp(`\\brpe\\s*(?:of\\s*)?(${N})`)) ||
            text.match(new RegExp(`\\b(${N})\\s*(?:out of|\\/)\\s*(?:ten|10)\\b`));
    if (m) return R('rpe', { rpe: Math.min(10, num(m[1])) });
    m = text.match(new RegExp(`\\b(${N})\\s*(?:reps?\\s*)?(?:in|left in)\\s*(?:the\\s*)?(?:tank|reserve)\\b`)) ||
        text.match(new RegExp(`\\brir\\s*(${N})\\b`));
    if (m) return R('rpe', { rpe: Math.max(4, 10 - num(m[1])) });
    if (/\bcould(?:'ve| have)? done (?:a couple|two|2) more\b/.test(text)) return R('rpe', { rpe: 8 });
    if (/\bcould(?:'ve| have)? done (?:one|1) more\b|\bone more in me\b/.test(text)) return R('rpe', { rpe: 9 });

    /* ---- end / pause / resume ---- */
    if (/\b(?:end|finish|stop|done with) (?:the )?workout\b|\bthat'?s it for today\b|\bi'?m done for today\b|\bcall it\b/.test(text))
      return R('end_workout');
    if (/^\s*pause\b|\bhold on\b|\bgive me a sec(?:ond)?\b|\bwait a (?:sec|minute|moment)\b/.test(text))
      return R('pause');
    if (/^\s*(?:resume|continue|carry on|unpause)\b/.test(text)) return R('resume');

    /* ---- rest control ---- */
    if (/\b(?:another|one more|an extra|extra)\s+(?:minute|min)\b|\bgive me (?:a bit |a little )?(?:more|longer)\b|\bneed more time\b|\bnot ready\b/.test(text))
      return R('extend_rest', { seconds: parseDuration(text) || 60 });
    if (/\b(?:i'?m )?ready\b|\blet'?s go\b|\bgo again\b|\bnext set\b|^go$|\bstart(?:ing)? (?:it|now)\b|\bi'?m good\b/.test(text))
      return R('ready');
    m = text.match(/\b(?:rest|give me|make it|take|i want|set (?:the )?(?:rest|timer)(?: to)?)\s*(.+)$/);
    if (m) { const d = parseDuration(m[1]); if (d) return R('set_rest', { seconds: d }); }
    if (/^\s*(?:\d+(?:\.\d+)?\s*(?:min(?:ute)?s?|sec(?:ond)?s?))\s*$/.test(text)) {
      const d = parseDuration(text); if (d) return R('set_rest', { seconds: d });
    }
    if (/\bskip (?:the )?rest\b|\bno rest\b/.test(text)) return R('ready');

    /* ---- the warm-up ----
       "done" and "next" already exist below and mean the same thing
       here; what needs its own handling is skipping the warm-up and
       asking how much of it is left. Both are phase-gated by the
       controller, so they cannot fire mid-set. */
    if (/\b(?:skip|forget|no|without|don'?t need) (?:the )?warm[\s-]?up\b|\bwarm[\s-]?up'?s? not needed\b|\bi(?:'?m| am) (?:already )?warm(?:ed up)?\b/.test(text))
      return R('warmup_skip');
    if (/\bhow (?:much )?(?:long|more|many)\b.*\bwarm[\s-]?up\b|\bwarm[\s-]?up (?:left|to go|remaining)\b/.test(text))
      return R('warmup_left');
    if (/\b(?:what'?s|whats) (?:the )?(?:next|this) (?:warm[\s-]?up|drill|movement)\b|\bwhy (?:am i|are we) doing this\b/.test(text))
      return R('warmup_why');

    /* ---- navigation ---- */
    if (/\bskip (?:this|it|the)?\s*(?:exercise|movement|one)?\b|\bmove on\b|\bdrop this\b|\bcan i skip\b/.test(text))
      return R('skip_exercise');
    if (/\bnext exercise\b|\bmove to the next\b/.test(text)) return R('next_exercise');
    if (/^\s*next\b|^\s*done\b|^\s*finished\b|^\s*complete[d]?\b|\bthat'?s (?:the )?(?:set|it)\b|\bset done\b|\bracked\b/.test(text))
      return R('done_set');

    /* ---- info questions the engine can answer without the LLM ---- */
    if (/\bwhat'?s next\b|\bwhat (?:are we|am i) doing next\b|\bwhat'?s (?:coming )?(?:up|after)\b/.test(text))
      return R('whats_next');
    if (/\bwhat (?:am i|are we) (?:doing|on)\b|\bwhere are we\b|\bwhat exercise\b|\bwhich (?:set|exercise)\b|\bwhat set (?:am i|is this)\b/.test(text))
      return R('where_am_i');
    if (/\bwhat weight\b|\bhow much (?:weight|should i|am i)\b|\bwhat'?s the weight\b/.test(text))
      return R('what_weight');
    if (/\bhow (?:much )?(?:long|time)\b.*\b(?:left|rest|to go)\b|\bhow much rest\b|\btime left\b/.test(text))
      return R('time_left');
    if (/\brepeat\b|\bsay (?:that )?again\b|\bwhat did you say\b|\bcome again\b/.test(text))
      return R('repeat');
    if (/\bhow long (?:have (?:i|we) been|has it been)\b|\bhow far in\b|\bworkout time\b/.test(text))
      return R('elapsed');

    /* ---- weight statements ---- */
    m = text.match(/\b(?:using|use|put(?:ting)? (?:it )?(?:on|at)|loaded?|set (?:it )?(?:to|at)|on|at|with)\s*(\d+(?:[.,]\d+)?)\s*(?:kg|kilos?|kilograms?|lbs?|pounds?)?\b/);
    if (m && /\b(?:kg|kilos?|kilograms?|lbs?|pounds?)\b/.test(text)) {
      return R('set_weight', { weight: parseFloat(m[1].replace(',', '.')) });
    }
    m = text.match(/\b(?:going up to|dropping to|drop to|go(?:ing)? to)\s*(\d+(?:[.,]\d+)?)\b/);
    if (m) return R('set_weight', { weight: parseFloat(m[1].replace(',', '.')) });

    /* ---- time budget ---- */
    m = text.match(/\bi (?:only )?have\s*(.+?)\s*(?:today|left)?$/);
    if (m) { const d = parseDuration(m[1]); if (d && d >= 600) return R('time_budget', { minutes: Math.round(d/60) }); }
    m = text.match(/\b(?:only|just)\s*(\d+)\s*min(?:ute)?s?\b/);
    if (m) return R('time_budget', { minutes: parseInt(m[1],10) });

    /* ---- answering "what are you loading?" ---- */
    if (ctx.awaiting === 'weight') {
      const n = firstNumber(text);
      if (n != null && n >= 0 && n <= 500) return R('set_weight', { weight: n });
      if (/\bbody ?weight\b|\bnothing\b|\bjust me\b|\bno weight\b/.test(text)) return R('set_weight', { weight: 0 });
    }

    /* ---- energy / check-in answers ---- */
    if (ctx.awaiting === 'energy') {
      const n = firstNumber(text);
      if (n != null && n >= 1 && n <= 10) return R('energy', { energy: n });
    }

    /* ---- RPE by feel: only when we asked, or clearly evaluative ---- */
    const evaluative = /\bthat (?:was|felt)\b|\bfelt\b|\bit was\b|\bpretty\b|\breally\b/.test(text) || ctx.awaiting === 'rpe';
    if (evaluative) {
      for (const [re, val] of RPE_PHRASES) if (re.test(text)) return R('rpe', { rpe: val, inferred: true });
    }
    if (ctx.awaiting === 'rpe') {
      const n = firstNumber(text);
      if (n != null && n >= 1 && n <= 10) return R('rpe', { rpe: n });
      if (CANCEL.test(text)) return R('rpe_decline');
      if (/\byeah?\b|\byes\b|\byep\b|\bcorrect\b|\bexactly\b/.test(text)) return R('affirm');
    }

    /* ---- reps: the single most common utterance ---- */
    if (ctx.awaiting !== 'rpe') {
      // bare number, or number wrapped in the usual filler
      const bare = text.match(/^(?:i )?(?:got|did|hit|managed|that'?s|was)?\s*(\d{1,3}|[a-z]+(?:[ -][a-z]+)?)\s*(?:reps?|times?)?$/);
      if (bare) {
        const n = firstNumber(bare[1]);
        if (n != null && n >= 1 && n <= 100) return R('reps', { reps: Math.round(n) });
      }
      m = text.match(/\b(?:got|did|hit|managed|finished|completed)\s*(\d{1,3})\b/);
      if (m) return R('reps', { reps: parseInt(m[1],10) });
      m = text.match(/\b(\d{1,3})\s*reps?\b/);
      if (m) return R('reps', { reps: parseInt(m[1],10) });
      m = text.match(/^(?:got|did|hit|managed)\s+([a-z]+(?:[ -][a-z]+)?)$/);
      if (m) { const n = firstNumber(m[1]); if (n != null && n >= 1 && n <= 100) return R('reps', { reps: Math.round(n) }); }
    }

    /* ---- yes / no ---- */
    if (/^(?:yes|yeah|yep|yup|sure|correct|right|affirmative|ok|okay)$/.test(text)) return R('affirm');
    if (/^(?:no|nope|nah|negative)$/.test(text)) return R('deny');

    return { intent:'unknown', text: rawText, fast:false };
  }

  VOS.Intent = { parse, wordsToNumber, firstNumber, parseDuration, NUMWORDS };
})(typeof globalThis !== 'undefined' ? globalThis : this);
