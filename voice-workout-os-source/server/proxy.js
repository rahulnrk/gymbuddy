#!/usr/bin/env node
/* ============================================================
   A minimal backend so the client never holds a provider key.
   The client talks to this; this talks to Anthropic. It also
   picks the model, so model IDs stop being a user problem.

     ANTHROPIC_API_KEY=sk-ant-... node server/proxy.js
     # then set the proxy URL in the app's Settings sheet

   Deliberately tiny — it is the shape of the production path,
   not the production path itself. For real use add auth, rate
   limiting, per-user quotas and request logging.
   ============================================================ */
const http = require('http');
const https = require('https');

const KEY = process.env.ANTHROPIC_API_KEY;
const PORT = process.env.PORT || 8787;
const MODEL = process.env.VOS_MODEL || 'claude-haiku-4-5';
const MAX_BODY = 256 * 1024;

if (!KEY) { console.error('Set ANTHROPIC_API_KEY first.'); process.exit(1); }

const cors = (res, origin) => {
  res.setHeader('access-control-allow-origin', origin || '*');
  res.setHeader('access-control-allow-headers', 'content-type');
  res.setHeader('access-control-allow-methods', 'GET,POST,OPTIONS');
  res.setHeader('access-control-max-age', '86400');
};

function upstream(path, method, body) {
  return new Promise((resolve, reject) => {
    const req = https.request({
      hostname: 'api.anthropic.com', path, method,
      headers: Object.assign({
        'x-api-key': KEY,
        'anthropic-version': '2023-06-01'
      }, body ? { 'content-type': 'application/json', 'content-length': Buffer.byteLength(body) } : {})
    }, r => {
      let d = '';
      r.on('data', c => { d += c; });
      r.on('end', () => resolve({ status: r.statusCode, body: d }));
    });
    req.on('error', reject);
    if (body) req.write(body);
    req.end();
  });
}

http.createServer(async (req, res) => {
  cors(res, req.headers.origin);
  if (req.method === 'OPTIONS') { res.writeHead(204); return res.end(); }

  if (req.url.startsWith('/v1/models')) {
    // The product chooses. The client is told one option so nobody has to
    // think about model IDs.
    res.writeHead(200, { 'content-type': 'application/json' });
    return res.end(JSON.stringify({ data: [{ id: MODEL, display_name: 'default' }] }));
  }

  if (req.method === 'POST' && req.url.startsWith('/v1/messages')) {
    let raw = '';
    let tooBig = false;
    req.on('data', c => { raw += c; if (raw.length > MAX_BODY) { tooBig = true; req.destroy(); } });
    req.on('end', async () => {
      if (tooBig) { res.writeHead(413); return res.end('{"error":"too large"}'); }
      let payload;
      try { payload = JSON.parse(raw); } catch (e) { res.writeHead(400); return res.end('{"error":"bad json"}'); }

      // the server decides the model and caps the cost, whatever the client sent
      payload.model = MODEL;
      payload.max_tokens = Math.min(payload.max_tokens || 350, 1000);

      try {
        const up = await upstream('/v1/messages', 'POST', JSON.stringify(payload));
        res.writeHead(up.status, { 'content-type': 'application/json' });
        res.end(up.body);
      } catch (e) {
        res.writeHead(502, { 'content-type': 'application/json' });
        res.end(JSON.stringify({ error: 'upstream unreachable' }));
      }
    });
    return;
  }

  res.writeHead(404); res.end('{"error":"not found"}');
}).listen(PORT, () => {
  console.log(`VOS proxy on http://localhost:${PORT}  model=${MODEL}`);
  console.log('Put that URL in the app under Settings -> Backend.');
});
