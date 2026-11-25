package com.example.saddlemate.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Horse icons that Material does not ship. Simplified silhouettes on a 24 dp grid;
 * the colour comes from the Icon tint.
 */
object HorseIcons {

    /** Horse head, facing left. Used for the Pferd tab and loose-horse marks. */
    val HorseHead: ImageVector by lazy {
        ImageVector.Builder(
            name = "HorseHead",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = addPathNodes(
                "M17,2 L15,3 C13,4 12,6 11,8 L9,11 C7,12 5,14 4,17 L3,22 L6,22 " +
                    "L8,19 C10,17 12,16 14,15 L16,11 C18,9 19,6 19,4 Z",
            ),
            fill = SolidColor(Color.Black),
        ).build()
    }

    /** Horseshoe, open end down. Used for the Ritte tab. */
    val HorseShoe: ImageVector by lazy {
        ImageVector.Builder(
            name = "HorseShoe",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = addPathNodes(
                "M4,4 L7,4 L7,12 A5,5 0 0 0 17,12 L17,4 L20,4 L20,13 A8,8 0 0 1 4,13 Z",
            ),
            fill = SolidColor(Color.Black),
        ).build()
    }
}
