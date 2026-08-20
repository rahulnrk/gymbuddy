package com.gymbuddy.domain

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class GoldenIndexTest {
    @Test
    fun kernelGoldensExist() {
        val dir = File("").absoluteFile.let { cwd ->
            listOf(
                File(cwd, "tests/golden"),
                File(cwd, "../tests/golden"),
                File(cwd, "../../tests/golden"),
            ).firstOrNull { it.isDirectory } ?: error("tests/golden not found from $cwd")
        }
        val files = dir.listFiles { f -> f.extension == "json" }?.map { it.name } ?: emptyList()
        assertTrue(files.size >= 20, "need ≥20 goldens, found ${files.size} in $dir")
        val required = listOf(
            "04-nl-logging.event.got-10-inherit-weight.json",
            "04-nl-logging.event.did-10-no-working-weight-clarify.json",
            "04-nl-logging.event.multi-set-40x12-10-8.json",
            "05-extract.event.reps-as-string-ten-fails-schema.json",
            "07-correction.event.no-8-one-row.json",
            "07-correction.event.that-was-second-set.json",
            "07-correction.event.actually-9-after-no-8.json",
            "07-correction.event.scratch-that-deleted.json",
            "07-correction.event.occupancy-second-set-later-exists.json",
            "07-correction.multilingual.kaadu-8.json",
            "01-lifecycle.event.session-init-then-workout-started.json",
            "06-state-machine.state.set-started-required.json",
            "06-state-machine.event.catch-up-40x12-10-8.json",
            "11-safety.event.chest-pain-critical.json",
            "11-safety.event.chest-fly-occupied-not-critical.json",
            "11-safety.event.telugu-shoulder-medium-advise.json",
            "03-voice.event.bystander-got-10-no-commit.json",
            "03-voice.event.headset-got-10-commits.json",
            "08-context.event.empty-history-no-invented-weight.json",
            "08-context.event.known-thursday-60x10-citable.json",
            "08-context.event.estimate-requires-two-rows.json",
            "08-context.event.uncertain-must-check-db.json",
            "08-context.offline.assemble-from-local.json",
            "03-voice.voice.barge-in-lt-200ms.json",
            "27-music.event.duck-before-tts.json",
            "27-music.event.restore-after-barge-in.json",
        )
        required.forEach { name ->
            assertTrue(name in files, "missing golden $name")
        }
    }
}
