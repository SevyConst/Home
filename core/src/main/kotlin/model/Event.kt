package model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class Event(
    val id: Long,
    val eventType: EventType,
    val time: String,
    val additionalInfo: Map<String, JsonElement>?
)
