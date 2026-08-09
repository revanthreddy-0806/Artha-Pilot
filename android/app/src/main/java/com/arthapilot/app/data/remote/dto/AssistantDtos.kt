package com.arthapilot.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ChatMessageDto(
    val role: String,
    val content: String,
)

data class ChatRequestDto(
    val message: String,
    val history: List<ChatMessageDto> = emptyList(),
)

data class ChatResponseDto(
    val answer: String,
    @SerializedName("grounded_context") val groundedContext: Map<String, Any?>,
    val disclaimer: String,
)