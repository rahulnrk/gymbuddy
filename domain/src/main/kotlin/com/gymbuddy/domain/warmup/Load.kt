package com.gymbuddy.domain.warmup

import kotlinx.serialization.json.Json

object MobilityJson {
    private val json = Json { ignoreUnknownKeys = false }

    fun loadWarmups(raw: String): WarmupLibrary =
        WarmupLibrary(json.decodeFromString<List<WarmupDrill>>(raw))

    fun loadStretches(raw: String): StretchLibrary =
        StretchLibrary(json.decodeFromString<List<Stretch>>(raw))
}
