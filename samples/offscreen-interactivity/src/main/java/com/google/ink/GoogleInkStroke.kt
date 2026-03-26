// Copyright @ MyScript. All rights reserved.

package com.google.ink

import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import com.myscript.iink.PointerEvent
import com.myscript.iink.PointerEventType
import com.myscript.iink.PointerType
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
        fun fromPointerEvents(pointerEvents: List<PointerEvent>): GoogleInkStroke {
            val brush = GoogleInkStrokeView.DEFAULT_BRUSH

            val initialTime = pointerEvents.firstOrNull()?.t ?: 0L

            val strokeInputBatch = MutableStrokeInputBatch()
            pointerEvents.forEach {
                strokeInputBatch.add(StrokeInput.create(it.x, it.y, it.t - initialTime))
            }
            return GoogleInkStroke(start = initialTime, stroke = Stroke(brush, strokeInputBatch.toImmutable()))
        }
    }
}