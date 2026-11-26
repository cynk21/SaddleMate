package com.example.saddlemate.domain

/**
 * Which icon and colour a map mark gets. One place for the mapping, so the map and the
 * selection menu show the same symbol.
 */
enum class MarkIcon(val iconName: String, val colorHex: String) {
    DOG("Pets", "#8D6E63"),
    BLIND_TURN("TurnSlightRight", "#F9A825"),
    ROAD_CROSSING("Traffic", "#1565C0"),
    GATE("Key", "#6D4C41"),
    WATER("WaterDrop", "#0288D1"),
    LOOSE_HORSE("HorseHead", "#C62828"),
    CANTER_STRETCH("Bolt", "#D97706"),
    RESIDENT_WARN("RecordVoiceOver", "#C62828"),
    MANURE_RULE("CleaningServices", "#5D4037"),
    DEEP_MUD("Waves", "#4E342E"),
    NOTE("Info", "#546E7A"),
    VOICE_PIN("VolumeUp", "#1B5E20"),
    ;

    companion object {
        /** Icon for a mark type key (a HazardType name or etiquette/trail key). Unknown keys get NOTE. */
        fun forKey(key: String): MarkIcon = entries.firstOrNull { it.name == key } ?: NOTE
    }
}
