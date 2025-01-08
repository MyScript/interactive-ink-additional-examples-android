// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.demo.ink.serialization.jiix

import com.microsoft.device.ink.InkView
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
        timestamp,
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

fun List<PointerEvent>.toBrush(converter: DisplayMetricsConverter?): InkView.Brush {
    return InkView.Brush(
        stroke = InputManager.ExtendedStroke().also { extendedStroke ->
            map { pointerEvent ->
                extendedStroke.addPoint(
                    InputManager.PenInfo(
                        pointerType = InputManager.PointerType.PEN_TIP,
                        x = converter?.x_mm2px(pointerEvent.x) ?: pointerEvent.x,
                        y = converter?.y_mm2px(pointerEvent.y) ?: pointerEvent.y,
                        timestamp = pointerEvent.t,
                        pressure = pointerEvent.f,
                        orientation = 0f,
                        tilt = 0f,
                        primaryButtonState = false,
                        secondaryButtonState = false
                    )
                )
            }
        }
    )
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

fun BoundingBox.toScreenCoordinates(converter: DisplayMetricsConverter?): BoundingBox {
    return BoundingBox(
        x = converter?.x_mm2px(this.x) ?: this.x,
        y = converter?.y_mm2px(this.y) ?: this.y,
        width = converter?.x_mm2px(this.width) ?: this.width,
        height = converter?.y_mm2px(this.height) ?: this.height
    )
}