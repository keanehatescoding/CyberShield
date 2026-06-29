package com.example.cybershield.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cybershield.core.sync.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ConnectivityViewModel
    @Inject
    constructor(
        networkMonitor: NetworkMonitor,
    ) : ViewModel() {
        val isOnline: StateFlow<Boolean> =
            networkMonitor.isOnline
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000L),
                    initialValue = true, // optimistic default — avoids a false "offline" flash on cold start
                )
    }
