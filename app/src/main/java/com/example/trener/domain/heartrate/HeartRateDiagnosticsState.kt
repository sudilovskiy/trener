package com.example.trener.domain.heartrate

enum class HeartRateDiagnosticSeverity {
    Pass,
    Warning,
    Blocker
}

data class HeartRateDiagnosticFinding(
    val title: String,
    val detail: String,
    val severity: HeartRateDiagnosticSeverity
)

data class HeartRateDiagnosticsState(
    val lastCheckedEpochMillis: Long? = null,
    val summary: String = "",
    val findings: List<HeartRateDiagnosticFinding> = emptyList()
) {
    val blockingFindings: List<HeartRateDiagnosticFinding>
        get() = findings.filter { it.severity == HeartRateDiagnosticSeverity.Blocker }

    val warningFindings: List<HeartRateDiagnosticFinding>
        get() = findings.filter { it.severity == HeartRateDiagnosticSeverity.Warning }

    val passingFindings: List<HeartRateDiagnosticFinding>
        get() = findings.filter { it.severity == HeartRateDiagnosticSeverity.Pass }

    val hasProblems: Boolean
        get() = blockingFindings.isNotEmpty() || warningFindings.isNotEmpty()
}
