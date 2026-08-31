const fs = require('fs');
const path = require('path');
const SRC = path.join(__dirname, 'src');
const R = f => fs.readFileSync(path.join(SRC, f), 'utf8');

const js = ['10-data.js','12-muscles.js','14-figure.js','15-match.js','16-heat.js','17-recovery.js','18-routines.js','19-warmup.js','20-engine.js','21-soreness.js','22-checkin.js','30-intent.js','32-numbers.js','34-dialogue.js','36-resolve.js',
            '40-policy.js','45-speech.js','52-actions.js','55-safety.js','58-controller.js','60-llm.js',
            '70-audio.js','72-gate.js','80-memory.js','85-fieldlog.js','88-screens.js','89-demo.js','90-app.js']
  .map(f => `\n/* ---------- ${f} ---------- */\n` + R(f)).join('\n');

const html = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover,maximum-scale=1">
<meta name="theme-color" content="#0a0b0d">
<meta name="apple-mobile-web-app-capable" content="yes">
<meta name="mobile-web-app-capable" content="yes">
<title>Voice Workout OS</title>
<style>
${R('05-style.css')}
</style>
</head>
<body>
${R('06-body.html')}
<script>
${js}
</script>
</body>
</html>
`;

const out = path.join(__dirname, 'voice-workout-os.html');
fs.writeFileSync(out, html);
console.log('built', out, (html.length / 1024).toFixed(1) + ' KB');
