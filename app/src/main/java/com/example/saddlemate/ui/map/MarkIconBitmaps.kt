package com.example.saddlemate.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import com.example.saddlemate.R
import com.example.saddlemate.domain.MarkIcon
import org.maplibre.android.maps.Style

/**
 * Loads the vector drawable of each mark type, rasterises it and registers it with the map
 * style. The map shows these as symbols, so marks read as icons instead of dots.
 */
object MarkIconBitmaps {

    fun imageId(icon: MarkIcon): String = "mark-${icon.name}"

    fun register(context: Context, style: Style, sizePx: Int = 64) {
        MarkIcon.entries.forEach { icon ->
            val drawable = ContextCompat.getDrawable(context, drawableFor(icon)) ?: return@forEach
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(canvas)
            style.addImage(imageId(icon), bitmap)
        }
    }

    private fun drawableFor(icon: MarkIcon): Int = when (icon) {
        MarkIcon.DOG -> R.drawable.mark_dog
        MarkIcon.BLIND_TURN -> R.drawable.mark_warning
        MarkIcon.ROAD_CROSSING -> R.drawable.mark_crossing
        MarkIcon.GATE -> R.drawable.mark_gate
        MarkIcon.WATER -> R.drawable.mark_water
        MarkIcon.LOOSE_HORSE -> R.drawable.mark_horse
        MarkIcon.CANTER_STRETCH -> R.drawable.mark_canter
        MarkIcon.RESIDENT_WARN -> R.drawable.mark_resident
        MarkIcon.MANURE_RULE -> R.drawable.mark_manure
        MarkIcon.DEEP_MUD -> R.drawable.mark_mud
        MarkIcon.NOTE -> R.drawable.mark_info
        MarkIcon.VOICE_PIN -> R.drawable.mark_speaker
    }
}
