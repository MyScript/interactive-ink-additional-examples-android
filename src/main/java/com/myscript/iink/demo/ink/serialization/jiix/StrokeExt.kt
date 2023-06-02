package com.myscript.iink.demo.ink.serialization.jiix

import com.microsoft.device.ink.InputManager
import com.myscript.iink.PointerEvent
import com.myscript.iink.PointerEventType
import com.myscript.iink.PointerType
import com.myscript.iink.demo.inksample.util.DisplayMetricsConverter

val InputManager.PointerType.toIInkPointerType: PointerType
    get() = when (this) {
        InputManager.PointerType.MOUSE -> PointerType.MOUSE
        InputManager.PointerType.FINGER -> PointerType.PEN // we should allow only PointerType.PEN for writing (crashes if we use PointerType.TOUCH)
        InputManager.PointerType.PEN_TIP -> PointerType.PEN
        InputManager.PointerType.PEN_ERASER -> PointerType.ERASER
        InputManager.PointerType.UNKNOWN -> PointerType.TOUCH
    }

fun InputManager.PenInfo.toPointerEvent(
    pointerEventType: PointerEventType,
    pointerId: Int = 0
): PointerEvent {
    return PointerEvent(
        pointerEventType,
        x,
        y,
        -1,
        pressure,
        pointerType.toIInkPointerType,
        pointerId
    )
}

fun InputManager.ExtendedStroke.toPointerEvents(): List<PointerEvent> {
    val points = getPoints()
    return points.mapIndexedNotNull { index, point ->
        val pointerEventType = when (index) {
            0 -> PointerEventType.DOWN
            points.lastIndex -> PointerEventType.UP
            else -> PointerEventType.MOVE
        }
        getPenInfo(point)?.toPointerEvent(
            pointerEventType = pointerEventType,
        )
    }
}

fun PointerEvent.convertPointerEvent(converter: DisplayMetricsConverter?): PointerEvent {
    return PointerEvent(eventType, converter?.x_px2mm(x) ?: x, converter?.y_px2mm(y) ?: y, t, f, pointerType, pointerId)
}

fun Word.toScreenCoordinates(converter: DisplayMetricsConverter?): Word {
    return if (this.boundingBox != null) {
        this.copy(
            boundingBox = BoundingBox(
                x = converter?.x_mm2px(this.boundingBox.x) ?: this.boundingBox.x,
                y = converter?.y_mm2px(this.boundingBox.y) ?: this.boundingBox.y,
                width = converter?.x_mm2px(this.boundingBox.width) ?: this.boundingBox.width,
                height = converter?.y_mm2px(this.boundingBox.height) ?: this.boundingBox.height
            )
        )
    } else {
        this
    }
}