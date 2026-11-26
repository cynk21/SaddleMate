package com.example.saddlemate.domain

/**
 * Hands-free Q&A intent router during a ride ("Wie weit noch?", "Ist das Pferd warmgeritten?",
 * "Wie hoch ist der Welfare-Score?", "Nächste Gefahr?").
 */
object VoiceQaRouter {

    data class RideQaContext(
        val warmupMinutes: Double,
        val requiredWarmupMinutes: Double,
        val cooldownNeededMinutes: Double,
        val distanceMeters: Double,
        val remainingMeters: Double?,
        val welfareScore: Int,
        val symmetryPct: Double,
        val nextHazardLabel: String?,
        val nextHazardDistM: Double?,
    )

    fun answer(question: String, ctx: RideQaContext): String? {
        val q = question.lowercase()
        return when {
            q.contains("warm") -> {
                if (ctx.warmupMinutes >= ctx.requiredWarmupMinutes) {
                    "Ja, das Pferd ist seit ${ctx.warmupMinutes.toInt()} Minuten im Schritt warmgeritten."
                } else {
                    val left = (ctx.requiredWarmupMinutes - ctx.warmupMinutes).coerceAtLeast(1.0).toInt()
                    "Noch nicht. Noch etwa $left Minuten Schritt zum Aufwärmen."
                }
            }
            q.contains("cool") || q.contains("trocken") || q.contains("abkühl") -> {
                if (ctx.cooldownNeededMinutes <= 0.5) {
                    "Aktuell keine zusätzliche Trockenreit-Zeit nötig."
                } else {
                    "Bitte noch ${ctx.cooldownNeededMinutes.toInt()} Minuten im Schritt trockenreiten."
                }
            }
            q.contains("weit") || q.contains("rest") || q.contains("stall") || q.contains("ziel") -> {
                val rem = ctx.remainingMeters
                if (rem != null) {
                    if (rem >= 1000.0) "Noch ${"%.1f".format(rem / 1000.0).replace('.', ',')} Kilometer bis zum Ziel."
                    else "Noch ${rem.toInt()} Meter bis zum Ziel."
                } else {
                    "Bisher ${"%.1f".format(ctx.distanceMeters / 1000.0).replace('.', ',')} Kilometer geritten."
                }
            }
            q.contains("takt") || q.contains("lahm") || q.contains("symmetr") -> {
                "Die Trab-Symmetrie liegt bei ${ctx.symmetryPct.toInt()} Prozent."
            }
            q.contains("welfare") || q.contains("gesund") || q.contains("belast") -> {
                "Der Tierwohl-Score liegt bei ${ctx.welfareScore} von 100 Punkten."
            }
            q.contains("gefahr") || q.contains("voraus") || q.contains("hindernis") -> {
                if (ctx.nextHazardLabel != null && ctx.nextHazardDistM != null) {
                    "Voraus in ${ctx.nextHazardDistM.toInt()} Metern: ${ctx.nextHazardLabel}."
                } else {
                    "Aktuell keine bekannte Gefahr direkt voraus."
                }
            }
            else -> null
        }
    }
}
