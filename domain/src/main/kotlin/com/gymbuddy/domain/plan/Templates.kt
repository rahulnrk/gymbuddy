package com.gymbuddy.domain.plan

import kotlinx.serialization.json.Json

object Templates {
    private val json = Json { ignoreUnknownKeys = false }

    fun decode(raw: String): Template = json.decodeFromString(Template.serializer(), raw)

    fun load(rawTemplates: List<String>): List<Template> =
        rawTemplates.map { decode(it) }.sortedBy { it.id }
}
