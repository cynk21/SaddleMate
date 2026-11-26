package com.example.saddlemate.domain

enum class RiskLevel { LOW, MEDIUM, HIGH }

data class WeatherConditions(
    val temperatureC: Double,
    val humidityPct: Int,
    val nightMinTempC: Double,
    val sunny: Boolean,
)

data class WeatherShieldReport(
    val thermalStressIndex: Double,
    val heatRisk: RiskLevel,
    val fructanRisk: RiskLevel,
    val warnings: List<String>,
)

/**
 * Pure heuristic for Fructan & Heat Stress traffic light (Fruktan-/Hitzestress-Ampel).
 * Thermal Stress Index = tempC + 0.1 * humidityPct (simple barn-side proxy for heat + mugginess),
 * Fructan risk = high when cold night (< 5°C) meets sunny day.
 */
object WeatherShield {

    fun evaluate(conditions: WeatherConditions, hasLaminitisHistory: Boolean): WeatherShieldReport {
        val tsi = conditions.temperatureC + conditions.humidityPct * 0.1
        val heatRisk = when {
            conditions.temperatureC >= 30.0 || tsi >= 37.0 -> RiskLevel.HIGH
            conditions.temperatureC >= 24.0 || tsi >= 31.0 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
        val fructanRisk = when {
            conditions.nightMinTempC < 5.0 && conditions.sunny -> RiskLevel.HIGH
            conditions.nightMinTempC < 8.0 && conditions.sunny -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
        val warnings = mutableListOf<String>()
        if (heatRisk == RiskLevel.HIGH) {
            warnings += "Hitzestress hoch (${conditions.temperatureC.toInt()}°C, ${conditions.humidityPct}%): Tempo reduzieren, Pausen & Wasser."
        } else if (heatRisk == RiskLevel.MEDIUM) {
            warnings += "Wärmebelastung mittel: ausreichend Schrittpausen einplanen."
        }
        if (hasLaminitisHistory && fructanRisk == RiskLevel.HIGH) {
            warnings += "Fruktan-Alarm (kalte Nacht + Sonne): Rehe-Pferd nicht unkontrolliert grasen lassen!"
        } else if (hasLaminitisHistory && fructanRisk == RiskLevel.MEDIUM) {
            warnings += "Fruktan mittel: Weidezeit für Rehe-Pferd begrenzen."
        }
        return WeatherShieldReport(tsi, heatRisk, fructanRisk, warnings)
    }
}
