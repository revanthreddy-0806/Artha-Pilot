package com.arthapilot.app.data.repository

import com.arthapilot.app.data.remote.ApiErrorMapper
import com.arthapilot.app.data.remote.ArthaApi
import com.arthapilot.app.data.remote.dto.WhatIfRequestDto
import com.arthapilot.app.data.remote.toDomain
import com.arthapilot.app.domain.model.Recommendation
import com.arthapilot.app.domain.model.WhatIfResult
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

interface RecommendationRepository {
    suspend fun fetchRecommendation(): Recommendation
    suspend fun simulate(category: String, monthlyReduction: Double): WhatIfResult
}

@Singleton
class RecommendationRepositoryImpl @Inject constructor(
    private val api: ArthaApi,
) : RecommendationRepository {

    override suspend fun fetchRecommendation(): Recommendation = mapNetwork {
        api.getRecommendations().toDomain()
    }

    override suspend fun simulate(category: String, monthlyReduction: Double): WhatIfResult = mapNetwork {
        api.postWhatIf(WhatIfRequestDto(category = category, monthlyReduction = monthlyReduction)).toDomain()
    }

    private suspend fun <T> mapNetwork(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        throw ApiErrorMapper.map(t)
    }
}