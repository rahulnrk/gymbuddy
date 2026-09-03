# PR — adopt the new session design in the contracts

**Read `docs/living-spec/SESSION-DESIGN.md` in full before doing anything.**
It is the design of record, agreed 2026-09-03. This PR makes the contracts
say what it says.

**This is a design reversal, not a bug fix.** The wake-word-first model in
contract 35 is being replaced by a coach-initiated one. The reason is physical,
not technical: the user's phone is in a pocket or locker, so the only usable
mic is in their earbuds, and on ordinary Bluetooth an open earbud mic drops the
music to call quality. The mic therefore cannot stay open across a workout, so
something other than the user must decide when it opens. That is the whole
argument; everything else follows from it.

**Documentation only. No behaviour change in this PR.** KDoc comments that
record dormancy are fine. No new logic, no deletions, no renames.

## Task 1 — rewrite contract 35

From §2, §3, §5 and §6 of the design document. It must state:

- The coach opens windows. The user cannot open one by speaking.
- The ducked period and the listening period are the same interval, and it is
  elastic — it extends while either party is talking.
- The mic opens the moment the coach starts speaking, so the user can cut in.
- The window ends a quiet hold after the exchange ends, where the exchange ends
  when the user stops **or** the coach finishes answering, whichever is later.
- Quiet means the user stopped speaking, not that the room went silent.
- Music ducks, never pauses. The app cannot set the duck level — Android asks,
  the music app decides.
- The 60 s cap that cut a live conversation in the 2026-09-02 floor test was a
  defect. There is no hard cap on a conversation in progress.
- Coach-initiated lines are pre-recorded with many variants and never repeat
  within one session. Replies to the user always come from the model. Lines
  carrying the user's own numbers are generated fresh and never stitched from
  recorded fragments.
- Topic scope: training, technique, programming, nutrition, calories, protein,
  muscles and anatomy, injuries, recovery, equipment. Everything else gets one
  polite line. **Write that line into the contract** — it will be said often.

Keep the contract's existing structure and section conventions. Do not invent
new formatting.

## Task 2 — contract 37 entry point

Pain triage currently assumes the user can raise pain by speaking. In v1 they
cannot. Its entry becomes the on-screen emergency control, which opens a window
immediately, out of schedule, and works whatever the clock believes is
happening — mid-set included. Update the contract; do not touch the triage
logic itself.

## Task 3 — mark the listen gate dormant

`Listen.kt`, `WakeWordProvider`, `ListenDecision` and their fakes stay in the
tree. Add a KDoc note to each recording that they are dormant for v1, the
earbud-mic reason above, and that they return if LE Audio earbuds become
common. Record the same in contract 35 and `MODULE_BOUNDARIES.md`.

**Delete nothing.** The architecture test that forbids fakes in production main
stays exactly as it is.

## Task 4 — inventory the tests that assert exact spoken lines

Once the model writes replies, a test cannot assert the sentence. Those tests
must move to asserting what was **logged** and what was **decided**. Cached
coach-initiated lines can still be asserted word for word.

**Do not change any test in this PR.** Produce a list: file, test name, and
which of the three buckets it falls into — (a) asserts a model-generated reply
and must change, (b) asserts a cached coach line and can stay, (c) unclear.
The size of bucket (a) tells us how big that job is.

## Task 5 — placeholder rows for the undecided numbers

The design document deliberately leaves these unset: check-in interval, its
minimum and maximum bounds, and the quiet hold. Add a row for each in
`DECISIONS_AND_THRESHOLDS.md` with source `UNDECIDED — SEE SESSION-DESIGN.md`.

**Do not pick values.** The floor test used 2500 ms for the hold and Rahul has
asked for 3000 ms; record that as the starting point under discussion, not as
a decision. A number that enters a spec unmarked becomes fact by the end of the
week, and nobody remembers it was a guess.

## Rules

- Docs and KDoc only. `.\gradlew :domain:test :persist:test` must still pass —
  **run it in full**, not as a compile dependency of `:app`.
- No new numeric constant.
- Do not begin the session clock, the window scheduler, or any screen. Those
  are later steps in §12 of the design document.

## Report back

- The rewritten contract 35 window policy, quoted.
- The out-of-scope line you wrote.
- The three test buckets, with counts.
- The dormancy notes, quoted once.
- **Where the design document is wrong.** It was written from a conversation,
  not from the code. If a section contradicts something already built, or
  assumes something that does not exist, say so plainly rather than writing a
  contract around it. Previous rounds found real defects this way; the same
  scrutiny applies here.
