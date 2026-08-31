# Running the demo

**Open `voice-workout-os.html` in Chrome. Add `?demo` to the end of the address.**

That one flag loads ten weeks of training for a user called Rahul and lands on the home screen. Nothing in the app is ever empty from that point on. If a laptop has been used before and the state looks wrong, the ▶ button in the header resets it in one tap.

Everything runs in the browser. No login, no server, no network. It works on aeroplane wifi.

---

## The three-minute version

**1. Home — "what am I doing today?"**
One screen answers it: the session, the six exercises, the honest length, and what is still sore from earlier in the week. The steppers change energy, soreness and available time, and the line underneath updates immediately — drop the time to 40 minutes and it tells you what gets cut and why. That is the product's argument in one interaction.

**2. Run a session hands-free — ▶ in the header → "Run a session, hands free"**
It plays a real session forward. A small pill at the bottom shows what the app just heard, so a room can follow along without the audio.

It opens on the **warm-up**, which is the part worth narrating. The app planned it from the first lift on today's list, and the ramp sets are computed from the weight actually going on the bar. While that is running it asks two things — how the energy is, and whether the soreness is where it expects. Watch the second one: it does not ask an open question, it states what it already believes and asks you to check it. In the demo the answer contradicts it, and the app names the likely cause, explains what that usually means, and adds work to the rest of the warm-up.

Then the working sets: logged by voice, rest timers running, the mini body map heating up as the work lands.

This is the safe path. It needs no microphone and no permission dialog. Use it unless the room is quiet and you have already tested the mic.

Worth pointing at while it plays:
- The step strip under the warm-up drill — green for general prep, blue for the ramp sets on the movement itself.
- **Undo** in the top right, always there. Speech mishears; the recovery is one tap.
- The set chips filling in as they are logged, with the effort grade.
- Around fifteen seconds in, the script says *"my shoulder hurts"* — the app stops pushing, swaps the movement and stays careful for the rest of the session. Pain is a state, not a note.
- The metrics line at the bottom of the session card: the fast path answers in single-digit milliseconds. Nothing waits on a model to log a set.

**3. Body map — the Body tab**
Red means *still recovering*, not *worked at some point*. Muscles outlined in white are ones **you told it about** rather than ones it estimated — tap one and the panel says which. Legs are lit from the last lower day, arms are part way back, chest and shoulders are ready. Tap any muscle for when it was last worked and roughly when it will be ready. Flip to the back view for lats, glutes and hamstrings.

The three chips change the question: what is recovering now, what was worked this week, what one session hit.

---

## If someone asks

**"Does the voice actually work, or is it a script?"**
It works. Click the microphone in Chrome and say a number — it logs a set. The playback exists because meeting rooms are loud and permission dialogs are a bad place to spend your third minute. Every path the playback drives is the same code path a spoken utterance takes.

**"What happens when it mishears?"**
Undo, always one tap. Beyond that: numbers are resolved in context, so "eight" during a set means reps and during check-in means energy; anything destructive asks first; anything ambiguous asks a single short question rather than guessing.

**"How does it know I'm sore?"**
Two ways, and it keeps them separate. It estimates from volume and elapsed time, per muscle, on that muscle's own recovery clock. Then during the warm-up it asks — and whatever you say replaces the estimate for that muscle and decays from when you said it. The map shows you which readings came from your mouth.

**"Where does the training logic come from?"**
Rep-range progression: hit the top of the range on every set and the weight goes up next time — 2.5 kg upper, 5 kg lower. Miss the bottom twice and it comes down 10%. Recovery is per-muscle with its own window; a forearm is back in about 30 hours, a lower back needs 60.

**"Is there a backend?"**
Not for this. Everything is local, which is why it opens instantly. The model calls it does make are optional and go through a proxy so no key sits on the device.

---

## Other things you can show

- `?demo=new` — starts as a brand new user, five onboarding screens. Worth it if someone asks about first-run.
- The ▶ menu → "Start as a brand new user" does the same thing.
- **Programmes** (Change, on the home screen) — paste a workout in whatever shape you already write it. There is no format to learn. Show it a messy paste and it tells you what it understood.
- **Pocket mode** — the moon button during a session. Near-empty, very high contrast, for a glance at arm's length.

## Before you walk in

- Open it once on the machine you will present from, with `?demo`, and let the playback run to the end.
- Chrome, not Safari — the speech APIs are better and the date picker matches.
- If you are screen-sharing, the desktop layout is a device-width column; it is meant to look like that.
