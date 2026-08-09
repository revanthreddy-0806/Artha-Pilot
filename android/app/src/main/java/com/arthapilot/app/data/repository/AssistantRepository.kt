package com.arthapilot.app.data.repository

import com.arthapilot.app.data.remote.ApiErrorMapper
import com.arthapilot.app.data.remote.ArthaApi
import com.arthapilot.app.data.remote.dto.ChatMessageDto
import com.arthapilot.app.data.remote.dto.ChatRequestDto
import com.arthapilot.app.data.remote.toDomain
import com.arthapilot.app.domain.model.AssistantReply
import com.arthapilot.app.domain.model.ChatTurn
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

interface AssistantRepository {
    suspend fun chat(message: String, history: List<ChatTurn>): AssistantReply
}

@Singleton
class AssistantRepositoryImpl @Inject constructor(
    private val api: ArthaApi,
) : AssistantRepository {

    override suspend fun chat(message: String, history: List<ChatTurn>): AssistantReply = mapNetwork {
        api.assistantChat(
            ChatRequestDto(
                message = message,
                history = history.map { ChatMessageDto(role = it.role, content = it.content) },
            )
        ).toDomain()
    }

    private suspend fun <T> mapNetwork(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        throw ApiErrorMapper.map(t)
    }
}