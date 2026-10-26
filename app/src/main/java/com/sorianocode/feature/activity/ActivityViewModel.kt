package com.sorianocode.feature.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sorianocode.core.api.OpenCodeSession
import com.sorianocode.data.repository.RuntimeActivityRepository
import com.sorianocode.data.repository.RuntimeCatalogRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ActivityUiState(
    val sessions: List<OpenCodeSession> = emptyList(),
    val activeSessionIds: Set<String> = emptySet(),
    val completedSessionIds: Set<String> = emptySet(),
)

class ActivityViewModel(
    private val catalog: RuntimeCatalogRepository,
    private val activity: RuntimeActivityRepository,
) : ViewModel() {
    val state: StateFlow<ActivityUiState> =
        combine(
            catalog.state,
            activity.state,
        ) { runtime, events ->
            ActivityUiState(
                sessions = runtime.sessions.sortedByDescending { it.time.updated ?: it.time.created },
                activeSessionIds = events.activeSessionIds,
                completedSessionIds = events.completedSessionIds,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ActivityUiState())
}
