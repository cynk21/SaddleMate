package com.example.saddlemate.domain

/**
 * Picks a map mark icon type from spoken German text of a voice pin.
 * Pure logic, no Android dependencies, unit-testable.
 * Falls back to [MarkIcon.VOICE_PIN] when no specific keyword matches.
 */
object PinClassifier {

    private val rules: List<Pair<List<String>, MarkIcon>> = listOf(
        listOf("hund", "hunde", "kläffer", "bellen") to MarkIcon.DOG,
        listOf("kurve", "unübersichtlich", "biegung", "schlechte sicht") to MarkIcon.BLIND_TURN,
        listOf("straße", "strasse", "querung", "überquerung", "verkehr", "ampel", "auto") to MarkIcon.ROAD_CROSSING,
        listOf("tor", "schranke", "gatter", "zaun", "schloss") to MarkIcon.GATE,
        listOf("wasser", "bach", "tränke", "pfütze", "teich", "fluss") to MarkIcon.WATER,
        listOf("pferd", "pferde", "herde", "koppel", "ausgebrochen") to MarkIcon.LOOSE_HORSE,
        listOf("hinweis", "achtung", "vorsicht", "loch", "ast", "baum") to MarkIcon.NOTE,
    )

    fun classify(text: String): MarkIcon {
        val lower = text.lowercase()
        for ((keywords, icon) in rules) {
            if (keywords.any { lower.contains(it) }) return icon
        }
        return MarkIcon.VOICE_PIN
    }
}
