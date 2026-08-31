# Audio diagnostic — three runs, Pixel 10 Pro (sdk 36), device "ZEB-BOOM"

Measured from the raw PCM, not from the app's own verdict.

| run | mic | zeros | longest gap | RMS | 300–3400 Hz | >4 kHz |
|---|---|---|---|---|---|---|
| 20:22:07 | headset (SCO) | **49.0%** | **584 ms** | -46.1 | 33% | 0.0% |
| 20:22:07 | phone + A2DP | 6.2% | 158 ms | -52.3 | 65% | 0.3% |
| 20:37:02 | headset (SCO) | 2.0% | 21 ms | -21.8 | 70% | 0.0% |
| 20:37:02 | phone + A2DP | 5.6% | 153 ms | -38.7 | 75% | 0.8% |
| 20:37:15 | headset (SCO) | 1.3% | 20 ms | -18.5 | 84% | 0.0% |
| 20:37:15 | phone + A2DP | 5.3% | 154 ms | -36.4 | 84% | 0.4% |

## 1. CORRECTED: run 1 was silence, not a dropout
The user confirmed he said nothing during run 1 and spoke during runs 2 and 3.
The 49% exact zeros and the 584 ms gaps are the **headset's own noise gate**
(HFP handsets commonly stop transmitting when there is no voice), not lost
frames. There is no evidence of a cold-start bug. The headset mic behaved
consistently across all three runs.

Consequence that matters more: when the wearer is silent, the earbud mic sends
literal zeros. We hear nothing of the room through it — no gym noise, no other
people. The gate is inside the earbud, outside our control, and it can clip the
first syllable of speech.

## 2. The headset mic is 8 kHz telephone audio wearing a 48 kHz label
Every headset recording has **0.0% of its energy above 4 kHz**, with a 15 dB
cliff between the 3–4 kHz and 4–5 kHz bands. That is a hard Nyquist wall at
4 kHz, i.e. a true stream of 8 kHz — classic CVSD, not even mSBC's 16 kHz.
`actualRate=48000` is the HAL upsampling to whatever we asked for.
If we ever use this path, request 8000 Hz and stop shipping 6x the bytes.

The phone mic, by contrast, rolls off gently and still has real content at
8–11 kHz. It is genuinely wideband.

## 3. The headset mic is ~18 dB hotter
Run 3: headset RMS -18.5 dBFS vs phone -36.4 dBFS on the same speech. A mic at
your mouth beats a mic on the table, by a lot. In a quiet room that does not
matter. In a gym it may decide everything — and has not yet been measured there.

## 4. The routing trade-off is real and confirmed on every run
- Headset mic open  -> `OUTPUT_AFTER TYPE_BLUETOOTH_SCO`, music collapses to call quality.
- Phone mic         -> `OUTPUT_AFTER TYPE_BLUETOOTH_A2DP`, music untouched.
- SCO connect costs 0.7–1.0 s (`SCO_STATE CONNECTING` -> `CONNECTED`), so the
  headset mic can never be opened mid-sentence without losing the sentence.
- Ducking focus granted on all three runs.

## 5. The app's verdict is still wrong
`HEADSET_MIC_VERDICT genuine=true` was printed for run 1's 49%-zero recording.
The verdict reads routing metadata only. It must read the samples.

## Standing conclusion
Default to **phone mic + A2DP**: full bandwidth, no music disruption, no
connection latency, no cold-start dropout, and the coach's voice is in the ears
rather than on a speaker, so there is no echo loop to cancel.
The headset mic stays an option for phone-in-locker users, at 8 kHz.
**The undecided question is gym-noise SNR**, and only a gym test settles it.
