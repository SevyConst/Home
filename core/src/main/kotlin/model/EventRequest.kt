package model

import kotlinx.serialization.Serializable

@Serializable
data class EventRequest(
    val events: List<Event>,
    val deviceId: String,
)