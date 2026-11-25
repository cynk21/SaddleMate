package com.example.saddlemate.ui.map

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.saddlemate.domain.MarkIcon
import com.example.saddlemate.ui.icons.HorseIcons

/** Compose icon for each [MarkIcon], matching the map symbols. */
fun markIconVector(icon: MarkIcon): ImageVector = when (icon) {
    MarkIcon.DOG -> Icons.Filled.Pets
    MarkIcon.BLIND_TURN -> Icons.Filled.TurnSlightRight
    MarkIcon.ROAD_CROSSING -> Icons.Filled.Traffic
    MarkIcon.GATE -> Icons.Filled.Key
    MarkIcon.WATER -> Icons.Filled.WaterDrop
    MarkIcon.LOOSE_HORSE -> HorseIcons.HorseHead
    MarkIcon.NOTE -> Icons.Filled.Info
    MarkIcon.VOICE_PIN -> Icons.AutoMirrored.Filled.VolumeUp
}
