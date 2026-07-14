package com.obdvis.android.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.LongMessageTemplate
import androidx.car.app.model.Template
import com.obdvis.android.domain.health.DiagnosticFinding

class AutoFindingScreen(
    carContext: CarContext,
    private val finding: DiagnosticFinding,
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val body = buildString {
            append(finding.description)
            if (finding.evidence.isNotEmpty()) {
                append("\n\nEvidence:")
                finding.evidence.forEach { (label, value) -> append("\n• $label: $value") }
            }
            append("\n\nSeverity: ${finding.severity.name}  |  Confidence: ${finding.confidence.name}")
        }

        return LongMessageTemplate.Builder(body)
            .setTitle(finding.title)
            .setHeaderAction(Action.BACK)
            .build()
    }
}
