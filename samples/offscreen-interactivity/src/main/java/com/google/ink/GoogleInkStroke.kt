// Copyright @ MyScript. All rights reserved.

package com.google.ink

import android.util.DisplayMetrics
import androidx.ink.brush.InputToolType
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import com.myscript.iink.PointerEvent
import com.myscript.iink.PointerEventType
import com.myscript.iink.PointerType
import com.myscript.iink.demo.inksample.util.DisplayMetricsConverter
import java.util.UUID

class GoogleInkStroke(
    val id: String = UUID.randomUUID().toString(),
    val start: Long = System.currentTimeMillis(),
    val stroke: Stroke) {

    fun toPointerEvents(): List<PointerEvent> {
        val pointerEvents = mutableListOf<PointerEvent>()
        for (index in 0 until stroke.inputs.size) {
            val point = stroke.inputs[index]
            val pointerEventType = when (index) {
                0 -> PointerEventType.DOWN
                stroke.inputs.size - 1 -> PointerEventType.UP
                else -> PointerEventType.MOVE
            }

            pointerEvents.add(PointerEvent(pointerEventType, point.x, point.y, start + point.elapsedTimeMillis, 0f, PointerType.PEN, 0))
        }

        return pointerEvents
    }

    companion object {
        fun fromPointerEvents(pointerEvents: List<PointerEvent>, displayMetrics: DisplayMetrics): GoogleInkStroke {
            val brush = GoogleInkStrokeView.DEFAULT_BRUSH

            val initialTime = pointerEvents.firstOrNull()?.t ?: 0L

            val strokeInputBatch = MutableStrokeInputBatch()
            val converter = DisplayMetricsConverter(displayMetrics)

            var prevElapsedTimeMillis = -1L
            pointerEvents.forEach {
                val elapsedTimeMillis = it.t - initialTime
                if (elapsedTimeMillis > prevElapsedTimeMillis) {
                    strokeInputBatch.add(InputToolType.STYLUS, converter.x_mm2px(it.x), converter.y_mm2px(it.y), elapsedTimeMillis)
                }
                prevElapsedTimeMillis = elapsedTimeMillis
            }

            return GoogleInkStroke(start = initialTime, stroke = Stroke(brush, strokeInputBatch))
        }
    }
}