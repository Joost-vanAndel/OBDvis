package com.obdvis.android

import com.obdvis.android.domain.health.DiagnosticFinding
import com.obdvis.android.domain.model.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SharedAutoState {
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _latestValues = MutableStateFlow<Map<String, Float>>(emptyMap())
    val latestValues: StateFlow<Map<String, Float>> = _latestValues.asStateFlow()

    private val _healthFindings = MutableStateFlow<List<DiagnosticFinding>>(emptyList())
    val healthFindings: StateFlow<List<DiagnosticFinding>> = _healthFindings.asStateFlow()

    fun updateConnection(state: ConnectionState) { _connectionState.value = state }
    fun updateLatestValues(values: Map<String, Float>) { _latestValues.value = values }
    fun updateHealthFindings(findings: List<DiagnosticFinding>) { _healthFindings.value = findings }
}
