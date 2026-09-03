package com.gymbuddy.domain.voice

/**
 * VAD gates the wake-word model, which gates the cloud.
 * Each stage exists to stop the next one running.
 *
 * Dormant for v1. The user's phone is in a pocket or locker, so the only
 * usable mic is in their earbuds. On ordinary Bluetooth an open earbud mic
 * drops the music to call quality. The coach therefore opens windows; the
 * user cannot open one by speaking. These types stay in the tree and return
 * if LE Audio earbuds become common. See contract 35 and SESSION-DESIGN.md.
 *
 * VoiceTurnMachine is the transport and is not wired to this yet.
 */
sealed interface ListenDecision {
    /** VAD stays armed. Wake word off. Cloud off. Dormant for v1; see file KDoc. */
    data object VadOnly : ListenDecision

    /**
     * Speech, no window, mic usable. Run wake word. Cloud still off.
     * Dormant for v1; see file KDoc.
     */
    data object RunWakeWord : ListenDecision

    /**
     * Coach window, wake word, earbud, on-screen, or emergency. Cloud STT may start.
     * Wake-word / earbud / generic on-screen are dormant for v1; see file KDoc.
     */
    data class OpenCloud(val via: CloudReason) : ListenDecision

    /**
     * Mic is unusable. Do not pretend a wake word fired.
     * On-screen mic is the way in. An earbud tap is music controls
     * while music is playing — the normal case — so it is not the fallback.
     * Session continues.
     */
    data class FallBackToOnScreen(val why: String) : ListenDecision
}

enum class CloudReason {
    COACH_WINDOW,
    WAKE_WORD,
    EARBUD,
    ON_SCREEN,
    EMERGENCY,
}

internal val FALLBACK_ON_SCREEN_WHY =
    "The gym is too loud for a wake word. Use the mic on the screen."

fun listenDecision(
    vad: VadObservation,
    wake: WakeWordObservation?,
    window: Window,
): ListenDecision {
    when (window) {
        is Window.CoachInitiated -> return ListenDecision.OpenCloud(CloudReason.COACH_WINDOW)
        is Window.UserInitiated -> return ListenDecision.OpenCloud(cloudReason(window.via))
        Window.None -> Unit
    }

    if (!vad.speech) return ListenDecision.VadOnly
    if (vad.echoFromTts) return ListenDecision.VadOnly

    if (vad.echoFromMusic && vad.usable != true) {
        return ListenDecision.FallBackToOnScreen(FALLBACK_ON_SCREEN_WHY)
    }
    if (vad.usable == false) {
        // TODO(onboarding): usable=false also covers a dead or unpermitted mic.
        // FALLBACK_ON_SCREEN_WHY then names gym noise and points at a control
        // that will not work either. Split when permission UI exists.
        return ListenDecision.FallBackToOnScreen(FALLBACK_ON_SCREEN_WHY)
    }

    if (!VoiceInteractionConstants.WAKE_WORD_PATH_LIVE) {
        return ListenDecision.VadOnly
    }
    if (wake?.detected == true) return ListenDecision.OpenCloud(CloudReason.WAKE_WORD)
    return ListenDecision.RunWakeWord
}

fun mayRunWakeWord(decision: ListenDecision): Boolean = decision is ListenDecision.RunWakeWord

fun mayOpenCloud(decision: ListenDecision): Boolean = decision is ListenDecision.OpenCloud

fun entryWorksWithoutWakeWord(entry: EntryPoint): Boolean =
    entry == EntryPoint.EARBUD || entry == EntryPoint.ON_SCREEN || entry == EntryPoint.EMERGENCY

/** While music plays, the earbud button is media controls. */
fun fallbackWhenUnusable(): EntryPoint = EntryPoint.ON_SCREEN

/**
 * Start or stop the next stage. VAD is always on and is not touched.
 * Never starts wake word on [ListenDecision.VadOnly].
 * Never starts STT unless the decision is [ListenDecision.OpenCloud].
 *
 * Dormant for v1; see [ListenDecision] file KDoc. Do not delete.
 */
fun applyListenDecision(
    decision: ListenDecision,
    wake: WakeWordProvider,
    stt: SttProvider,
) {
    when (decision) {
        ListenDecision.VadOnly, is ListenDecision.FallBackToOnScreen -> {
            if (wake.started) wake.stop()
            if (stt.recognizing) stt.abort()
        }
        ListenDecision.RunWakeWord -> {
            if (!VoiceInteractionConstants.WAKE_WORD_PATH_LIVE) {
                if (wake.started) wake.stop()
                if (stt.recognizing) stt.abort()
            } else {
                if (!wake.started) wake.start()
                if (stt.recognizing) stt.abort()
            }
        }
        is ListenDecision.OpenCloud -> {
            if (wake.started) wake.stop()
            if (!stt.recognizing) stt.startRecognition()
        }
    }
}

private fun cloudReason(entry: EntryPoint): CloudReason = when (entry) {
    EntryPoint.WAKE_WORD -> CloudReason.WAKE_WORD
    EntryPoint.EARBUD -> CloudReason.EARBUD
    EntryPoint.ON_SCREEN -> CloudReason.ON_SCREEN
    EntryPoint.EMERGENCY -> CloudReason.EMERGENCY
}
