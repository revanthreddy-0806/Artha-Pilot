package com.arthapilot.app.data.refresh

import com.arthapilot.app.data.local.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared, user-scoped invalidation signal for transaction-derived data.
 *
 * A monotonically increasing revision counter is used instead of a one-shot
 * event so that an invalidation is never lost while a consumer is temporarily
 * inactive: consumers compare the latest revision with the one they last handled.
 */
interface DataInvalidationBus {
    val revision: StateFlow<Long>
    fun notifyDataChanged()
    fun reset()
}

@Singleton
class DataInvalidationBusImpl @Inject constructor(
    private val tokenStore: TokenStore,
) : DataInvalidationBus {

    private val _revision = MutableStateFlow(0L)
    override val revision: StateFlow<Long> = _revision.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch {
            tokenStore.tokens.collect { tokens ->
                if (tokens == null) {
                    _revision.value = 0L
                }
            }
        }
    }

    override fun notifyDataChanged() {
        _revision.value = _revision.value + 1
    }

    override fun reset() {
        _revision.value = 0L
    }
}

/**
 * No-op implementation used by legacy/test constructors and when no shared bus
 * is available. It never emits an invalidation.
 */
object NoOpDataInvalidationBus : DataInvalidationBus {
    private val _revision = MutableStateFlow(0L)
    override val revision: StateFlow<Long> = _revision.asStateFlow()
    override fun notifyDataChanged() = Unit
    override fun reset() = Unit
}