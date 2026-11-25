package com.example.saddlemate.domain

/**
 * Which icon and colour a map mark gets. One place for the mapping, so the map and the
 * selection menu show the same symbol. The icon name is the Material icon in
 * `material-icons-extended`.
 */
enum class MarkIcon(val iconName: String, val colorHex: String) {
    DOG("Pets", "#8D6E63"),
    BLIND_TURN("TurnSlightRight", "#F9A825"),
    ROAD_CROSSING("Traffic", "#1565C0"),
    GATE("Key", "#6D4C41"),
    WATER("WaterDrop", "#0288D1"),
    LOOSE_HORSE("HorseHead", "#C62828"),
    NOTE("Info", "#546E7A"),
    VOICE_PIN("VolumeUp", "#6A1B9A"),
    ;

    companion object {
        /** Icon for a mark type key (a HazardType name or "NOTE"). Unknown keys get the note icon. */
        fun forKey(key: String): MarkIcon = entries.firstOrNull { it.name == key } ?: NOTE
    }
}
