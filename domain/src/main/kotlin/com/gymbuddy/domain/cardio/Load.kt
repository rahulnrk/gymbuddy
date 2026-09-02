package com.gymbuddy.domain.cardio

import kotlinx.serialization.json.Json

object CardioJson {
    private val json = Json { ignoreUnknownKeys = false }

    fun load(modalitiesRaw: String, protocolsRaw: String): CardioLibrary =
        CardioLibrary(
            modalities = json.decodeFromString<List<CardioModality>>(modalitiesRaw),
            protocols = json.decodeFromString<List<CardioProtocol>>(protocolsRaw),
        )
}
